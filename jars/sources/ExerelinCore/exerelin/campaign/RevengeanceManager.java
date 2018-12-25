package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.events.SSP_FactionVengeanceEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.NexUtilsMath;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Handles SS+ vengeance fleets and Nexerelin counter-invasion fleets
 */
public class RevengeanceManager extends BaseCampaignEventListener {

	public static final boolean DEBUG_MODE = false;
	public static final String PERSISTENT_KEY = "nex_revengeanceManager";
	
	// controls frequency of spawning counter-invasion fleets
	public static final float POINTS_TO_SPAWN = 125;
	
	// each entry in the array represents a fleet
	// first number is vengeance points needed, second is escalation level (0-2)
	public static final List<Integer[]> FLEET_STAGES = Arrays.asList(
			new Integer[][] {{50, 0}, {100, 0}, {150, 0}, {200, 1}, {275, 1}, {350, 1}, {450, 2}}
	);
	// after all stages are used up, spawn new vengeance fleets per this many points
	public static final float ADDITIONAL_STAGE_INTERVAL = 75;	
	// this + ADDITIONAL_STAGE_INTERVAL should not be too far from the points required for last stage
	public static final float ADDITIONAL_STAGE_OFFSET = 0;
	
	public static final float VENGEANCE_FLEET_POINT_MULT = 0.8f;
	
	public static Logger log = Global.getLogger(RevengeanceManager.class);
	
	float points = 0;
	Map<String, Float> factionPoints = new HashMap<>();
	Map<String, Integer> factionVengeanceStage = new HashMap<>();
	
	static {
		if (DEBUG_MODE)
		{
			for (Integer[] stage : FLEET_STAGES)
			{
				stage[0] = (int)(stage[0] * 0.01);
				stage[1] = 2;
			}
		}		
	}

	public RevengeanceManager() {
		super(true);
	}
	
	// this exists because else it'd be a leak in constructor
	public void init() {
		Global.getSector().getPersistentData().put(PERSISTENT_KEY, this);
	}
	
	/**
	 * Add general vengeance points (for retaliatory invasion)
	 * @param addedPoints
	 */
	public void addPoints(float addedPoints)
	{
		if (!isRevengeanceEnabled()) return;
		
		if (!SectorManager.getHardMode())
			addedPoints *= 0.5f;
		points += addedPoints;
		String debugStr = "Adding revengeance points: " + addedPoints;
		log.info(debugStr);
		if (DEBUG_MODE)
		{
			Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		if (points >= POINTS_TO_SPAWN)
		{
			boolean success = generateCounterInvasionFleet();
			if (success) points -= POINTS_TO_SPAWN;
		}
	}
	
	/**
	 * Add vengeance points for a specific faction (for hunter-killer fleets)
	 * @param factionId
	 * @param points
	 */
	public void addFactionPoints(String factionId, float points)
	{
		if (!isRevengeanceEnabled()) return;
		if (SSP_FactionVengeanceEvent.EXCEPTION_LIST.contains(factionId)) return;
		if (factionPoints == null)
			factionPoints = new HashMap<>();
		if (!factionPoints.containsKey(factionId))
		{
			factionPoints.put(factionId, 0f);
		}
		
		// lower point generation if not vengeful
		if (!Global.getSector().getFaction(factionId).isHostileTo(Factions.PLAYER))
			return;
		else if (Global.getSector().getFaction(factionId).isAtWorst(Factions.PLAYER, RepLevel.HOSTILE))
			points *= 0.25f;
		
		points *= SSP_FactionVengeanceEvent.VengeanceDef.getDef(factionId).vengefulness * 2;
		points *= VENGEANCE_FLEET_POINT_MULT;
		
		String debugStr = "Adding faction revengeance points for " + factionId + ": " + points;
		log.info(debugStr);
		if (DEBUG_MODE)
		{
			Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		
		float currPts = factionPoints.get(factionId);
		float newPts = currPts + points;
		tryActivateFactionVengeance(factionId, currPts, newPts);
		factionPoints.put(factionId, currPts + points);
	}
	
	/**
	 * Having incremented faction vengeance points, see if we should launch a hunter-killer fleet
	 * @param factionId
	 * @param currPts
	 * @param newPts
	 */
	public void tryActivateFactionVengeance(String factionId, float currPts, float newPts)
	{
		int currStageNum = getCurrentVengeanceStage(factionId);
		if (currStageNum < FLEET_STAGES.size() - 1)
		{
			Integer[] nextStage = FLEET_STAGES.get(currStageNum + 1);
			float pointsNeeded = nextStage[0];
			if (newPts > pointsNeeded)
			{
				advanceVengeanceStage(factionId);
			}
		}
		else
		{
			int newStageNum = (int)((newPts + ADDITIONAL_STAGE_OFFSET)/ADDITIONAL_STAGE_INTERVAL);
			if (newStageNum > currStageNum)
			{
				advanceVengeanceStage(factionId);
			}
		}
	}
	
	/**
	 * Increments the faction vengeance stage and starts a H/K fleet event
	 * @param factionId
	 */
	public void advanceVengeanceStage(String factionId)
	{
		if (factionVengeanceStage == null)
			factionVengeanceStage = new HashMap<>();
		
		if (!factionVengeanceStage.containsKey(factionId))
		{
			factionVengeanceStage.put(factionId, 0);
		}
		else
		{
			int currStage = factionVengeanceStage.get(factionId);
			factionVengeanceStage.put(factionId, currStage + 1);
		}
		
		MarketAPI source = pickMarketForFactionVengeance(factionId);
		if (source != null)
		{
			String debugStr = "Spawning faction vengeance fleet for " + factionId + " at " + source.getName();
			log.info(debugStr);
			if (Global.getSettings().isDevMode())
			{
				//Global.getSector().getCampaignUI().addMessage(debugStr);
			}
			
			// TODO: actually spawn the vengeance fleet
			//Global.getSector().getEventManager().startEvent(new CampaignEventTarget(source), "exerelin_faction_vengeance", null);
		}
	}
	
	public int getCurrentVengeanceStage(String factionId)
	{
		if (factionVengeanceStage == null)
			factionVengeanceStage = new HashMap<>();
		
		if (!factionVengeanceStage.containsKey(factionId))
		{
			factionVengeanceStage.put(factionId, -1);
			return -1;
		}
		return factionVengeanceStage.get(factionId);
	}
	
	public int getVengeanceEscalation(String factionId)
	{
		int stage = getCurrentVengeanceStage(factionId);
		if (stage < 0) return 0;
		else if (stage > FLEET_STAGES.size() - 1) return 2;
		else return (FLEET_STAGES.get(stage)[1]);
	}
	
	// because for some reason ReportBattleFinished isn't being called, have StatsTracker call this instead
	@Override
	public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
	{
		log.info("Battle finished");
		if (!battle.isPlayerInvolved()) return;
		
		List<CampaignFleetAPI> killedFleets = battle.getNonPlayerSide();

		float involvedFraction = battle.getPlayerInvolvementFraction();

		float recentFpKilled = 0;
		
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(playerAlignedFactionId, 
				true, true, false);
		
		for (CampaignFleetAPI killedFleet : killedFleets)
		{
			String factionId = killedFleet.getFaction().getId();
			if (!enemies.contains(factionId)) continue;
			if (ExerelinUtilsFleet.getFleetType(killedFleet).equals("vengeanceFleet"))
				continue;
			
			List<FleetMemberAPI> killCurrent = killedFleet.getFleetData().getMembersListCopy();
			for (FleetMemberAPI member : killedFleet.getFleetData().getSnapshot()) {
				if (!killCurrent.contains(member)) {
					recentFpKilled += member.getFleetPointCost();
				}
			}
		}
		
		recentFpKilled *= involvedFraction;
		float points = recentFpKilled * ExerelinConfig.revengePointsPerEnemyFP;
		if (points > 0)
		{
			addPoints(points);
			addFactionPoints(killedFleets.get(0).getFaction().getId(), points * 2f);
		}
	}
	
	public static boolean isRevengeanceEnabled()
	{
		int requiredSetting = 2;
		if (SectorManager.getHardMode()) requiredSetting = 1;
		log.info("Required revengeance setting: " + requiredSetting);
		if (requiredSetting <= ExerelinConfig.enableRevengeFleets) {
			return true;
		}
		return false;
	}
	
	/**
	 * Make a fleet to conquer one of player faction's markets
	 * @return True if fleet was successfully created, false otherwise
	 */
	protected boolean generateCounterInvasionFleet()
	{
		log.info("Trying to generate counter-invasion fleet");
		
		SectorAPI sector = Global.getSector();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		WeightedRandomPicker<String> attackerPicker = new WeightedRandomPicker();
		WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
		WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
		
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(playerAlignedFactionId, 
				ExerelinConfig.allowPirateInvasions, true, false);
		if (enemies.isEmpty()) return false;
		
		for (String enemyId : enemies)
		{
			// only allow Templars to send counter-invasion fleet if we have no other enemies
			if (enemyId.equals("templars") && enemies.size() > 1)
				continue;
			attackerPicker.add(enemyId);
		}
		String revengeFactionId = attackerPicker.pick();
		if (revengeFactionId == null || revengeFactionId.isEmpty())
			return false;
		FactionAPI revengeFaction = Global.getSector().getFaction(revengeFactionId);
		log.info("Picked enemy: " + revengeFaction.getDisplayName());
		
		// spawn our revengeance fleet
		String debugStr = "Spawning counter-invasion fleet for " + revengeFactionId;
		log.info(debugStr);
		if (Global.getSettings().isDevMode())
		{
			//Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		
		InvasionFleetManager.getManager().generateInvasionFleet(revengeFaction, PlayerFactionStore.getPlayerFaction(), false, 1.5f);
		
		return true;
	}
	
	/**
	 * Select a source market for faction vengeance fleets
	 * @param factionId
	 * @return
	 */
	public MarketAPI pickMarketForFactionVengeance(String factionId) 
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
        float total = 0f;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (faction.getId().contentEquals("cabal")) {
                if (market.getFaction() != faction && !market.hasCondition("cabal_influence")) {
                    continue;
                }
            } else {
                if (market.getFaction() != faction) {
                    continue;
                }
            }
			if (!market.hasSpaceport()) continue;
			
            float weight = market.getSize() * (float) Math.sqrt(NexUtilsMath.lerp(0.25f, 1f, market.getShipQualityFactor()));
            float mod = 1f;
            if (market.hasIndustry(Industries.MILITARYBASE) || market.hasCondition("ii_interstellarbazaar")) {
                mod += 0.15f;
            }
            if (market.hasIndustry(Industries.HIGHCOMMAND)) {
                mod += 0.2f;
            }
            if (market.hasIndustry(Industries.PATROLHQ)) {
                mod += 0.1f;
            }
            if (market.hasIndustry(Industries.MEGAPORT)) {
                mod += 0.15f;
            }
            weight *= mod;
            picker.add(market, weight);
        }
        return picker.pick();
    }
	
	public static RevengeanceManager getManager()
	{
		return (RevengeanceManager)Global.getSector().getPersistentData().get(PERSISTENT_KEY);
	}
	
}
