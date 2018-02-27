package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.InvasionFleetManager;
import static exerelin.campaign.fleets.InvasionFleetManager.DEFENDER_STRENGTH_MARINE_MULT;
import static exerelin.campaign.fleets.InvasionFleetManager.EXCEPTION_LIST;
import static exerelin.campaign.fleets.InvasionFleetManager.spawnInvasionFleet;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * Handles SS+ vengeance fleets and Nexerelin counter-invasion fleets
 */
public class RevengeanceManagerEvent extends BaseEventPlugin {

	public static final boolean DEBUG_MODE = false;
	
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
	
	public static Logger log = Global.getLogger(RevengeanceManagerEvent.class);
	
	private boolean ended = false;
	private final IntervalUtil interval = new IntervalUtil(1f, 1f);
	
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
			points *= 0.2f;
		
		points *= SSP_FactionVengeanceEvent.VengeanceDef.getDef(factionId).vengefulness * 2;
		
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
			
			Global.getSector().getEventManager().startEvent(new CampaignEventTarget(source), "exerelin_faction_vengeance", null);
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

	@Override
	public String getCurrentImage() {
		return faction.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return faction.getCrest();
	}

	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}

	@Override
	public String getEventIcon() {
		return faction.getCrest();
	}

	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "revengeanceFleet");
	}
	
	@Override
	public void init(String eventType, CampaignEventTarget eventTarget) {
		super.init(eventType, eventTarget, false);
	}

	@Override
	public boolean isDone() {
		return ended;
	}
	
	public void endEvent()
	{
		ended = true;
	}
	
	// because for some reason ReportBattleFinished isn't being called, have StatsTracker call this instead
	public void reportBattle(CampaignFleetAPI winner, BattleAPI battle)
	{
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
			addFactionPoints(killedFleets.get(0).getFaction().getId(), points * 2.5f);
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
		
		// pick a source market
		for (MarketAPI market : markets) {
			if (market.hasCondition(Conditions.ABANDONED_STATION)) continue;
			if (market.hasCondition(Conditions.DECIVILIZED)) continue;
			if (market.getPrimaryEntity() instanceof CampaignFleetAPI) continue;
			if  ( market.getFactionId().equals(revengeFactionId) && 
				( (market.hasCondition(Conditions.SPACEPORT)) || (market.hasCondition(Conditions.ORBITAL_STATION)) || (market.hasCondition(Conditions.MILITARY_BASE))
					|| (market.hasCondition(Conditions.REGIONAL_CAPITAL)) || (market.hasCondition(Conditions.HEADQUARTERS))
				) && market.getSize() >= 3 )
			{
				//marineStockpile = market.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();
				//if (marineStockpile < MIN_MARINE_STOCKPILE_FOR_INVASION)
				//		continue;
				float weight = 1;   //marineStockpile;
				if (market.hasCondition(Conditions.MILITARY_BASE)) {
					weight *= 1.4F;
				}
				if (market.hasCondition(Conditions.ORBITAL_STATION)) {
					weight *= 1.15F;
				}
				if (market.hasCondition(Conditions.SPACEPORT)) {
					weight *= 1.35F;
				}
				if (market.hasCondition(Conditions.HEADQUARTERS)) {
					weight *= 1.3F;
				}
				if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) {
					weight *= 1.1F;
				}
				weight *= 0.5f + (0.5f * market.getSize() * market.getStabilityValue());
				sourcePicker.add(market, weight);
			}
		}
		MarketAPI originMarket = sourcePicker.pick();
		if (originMarket == null) {
			return false;
		}
		log.info("\tStaging from " + originMarket.getName());
		//marineStockpile = originMarket.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();

		// now we pick a target
		Vector2f originMarketLoc = originMarket.getLocationInHyperspace();
		for (MarketAPI market : markets) 
		{
			FactionAPI marketFaction = market.getFaction();
			if (EXCEPTION_LIST.contains(marketFaction.getId())) continue;
			if (!marketFaction.getId().equals(playerAlignedFactionId))
				continue;
			

			if (!ExerelinUtilsMarket.shouldTargetForInvasions(market, 0)) continue;
			/*
			float defenderStrength = InvasionRound.GetDefenderStrength(market);
			float estimateMarinesRequired = defenderStrength * 1.2f;
			if (estimateMarinesRequired > marineStockpile * MAX_MARINE_STOCKPILE_TO_DEPLOY)
				continue;   // too strong for us
			*/
			float dist = Misc.getDistance(market.getLocationInHyperspace(), originMarketLoc);
			if (dist < 5000.0F) {
				dist = 5000.0F;
			}
			
			float weight = 20000.0F / dist;
			// prefer high value targets
			if (market.hasCondition(Conditions.MILITARY_BASE)) {
				weight *= 1.25F;
			}
			if (market.hasCondition(Conditions.HEADQUARTERS)) {
				weight *= 1.5F;
			}
			if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) {
				weight *= 1.2F;
			}

			//weight *= market.getSize() * market.getStabilityValue();	// try to go after high value targets
			targetPicker.add(market, weight);
		}
		MarketAPI targetMarket = targetPicker.pick();
		if (targetMarket == null) {
			return false;
		}
		log.info("\tTarget: " + targetMarket.getName());

		// spawn our revengeance fleet
		String debugStr = "Spawning counter-invasion fleet for " + revengeFactionId + "; source " + originMarket.getName() + "; target " + targetMarket.getName();
		log.info(debugStr);
		if (Global.getSettings().isDevMode())
		{
			//Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		
		InvasionFleetManager.InvasionFleetData data = spawnInvasionFleet(revengeFaction, originMarket, targetMarket, DEFENDER_STRENGTH_MARINE_MULT, 1.5f, false);
		Map<String, Object> params = new HashMap<>();
		params.put("target", targetMarket);
		params.put("dp", data.startingFleetPoints);
		InvasionFleetEvent event = (InvasionFleetEvent)Global.getSector().getEventManager().startEvent(new CampaignEventTarget(originMarket), "exerelin_invasion_fleet", params);
		data.event = event;
		event.reportStart();
		
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
            float weight = market.getSize() * (float) Math.sqrt(ExerelinUtils.lerp(0.25f, 1f, market.getShipQualityFactor()));
            float mod = 1f;
            if (market.hasCondition(Conditions.MILITARY_BASE) || market.hasCondition("ii_interstellarbazaar")) {
                mod += 0.15f;
            }
            if (market.hasCondition(Conditions.HEADQUARTERS)) {
                mod += 0.1f;
            }
            if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) {
                mod += 0.1f;
            }
            if (market.hasCondition(Conditions.SPACEPORT) || market.hasCondition(Conditions.ORBITAL_STATION)) {
                mod += 0.15f;
            }
            weight *= mod;
            picker.add(market, weight);
        }
        return picker.pick();
    }
	
	public static RevengeanceManagerEvent getOngoingEvent()
	{
		CampaignEventPlugin eventSuper = Global.getSector().getEventManager().getOngoingEvent(null, "exerelin_revengeance_manager");
		if (eventSuper != null) 
		{
			RevengeanceManagerEvent event = (RevengeanceManagerEvent)eventSuper;
			return event;
		}
		return null;
	}
	
}
