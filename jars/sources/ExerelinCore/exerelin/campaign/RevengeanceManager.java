package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.DelayedFleetEncounter;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.econ.ResourcePoolManager.RequisitionParams;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.VengeanceFleetIntel;
import exerelin.campaign.intel.fleets.VengeanceFleetIntel.VengeanceDef;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

/**
 * Handles SS+ vengeance fleets and Nexerelin counter-invasion fleets
 */
public class RevengeanceManager extends BaseCampaignEventListener implements ColonyPlayerHostileActListener {

	public static final boolean DEBUG_MODE = false;
	public static final String PERSISTENT_KEY = "nex_revengeanceManager";
	
	public static final float HARD_MODE_MULT = 2;	
	
	// each entry in the array represents a fleet
	// first number is vengeance points needed, second is escalation level (0-2)
	public static final List<Integer[]> FLEET_STAGES = Arrays.asList(
			new Integer[][] {{50, 0}, {100, 0}, {150, 1}, {200, 1}, {275, 2}, {350, 2}}
	);
	// after all stages are used up, spawn new vengeance fleets per this many points
	public static final float ADDITIONAL_STAGE_INTERVAL = 75;
	
	// after last preset stage is reached, and we want first interval-based stage,
	// total stage number will equal (current vengeance points + offset)/ADDITIONAL_STAGE_INTERVAL
	// make sure the first interval stage isn't too far or close to last preset stage's points
	public static final float ADDITIONAL_STAGE_OFFSET = 0;	//-FLEET_STAGES.get(FLEET_STAGES.size() - 1)[0];
	
	public static final Set<String> IGNORED_FLEET_TYPES = new HashSet<>(Arrays.asList(new String[]{
		"vengeanceFleet", "exerelinInvasionFleet", "exerelinStrikeFleet", "exerelinInvasionSupportFleet",
		"nex_satBombFleet"
	}));
	
	public static final float VENGEANCE_FLEET_POINT_MULT = 0.8f;
	public static final float NEW_STYLE_VENGEANCE_POINT_MULT = 0.5f;
	public static final float INVASION_POINT_COST_MULTIPLIER = 0.5f;
	public static final float SAT_BOMB_CHANCE = 0.4f;	
	public static final int SAT_BOMB_DEATHS_FOR_REVENGE = 50000;
	
	public static Logger log = Global.getLogger(RevengeanceManager.class);
	
	protected float points = 0;
	protected int satBombDeathToll = 0;
	protected int deathTollSinceLastVengeance = 0;
	
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
		Global.getSector().getListenerManager().addListener(this);
	}
	
	/**
	 * Add general vengeance points (for retaliatory invasion and sat bomb fleets)
	 * @param addedPoints
	 * @param fromFactionId
	 */
	public void addPoints(float addedPoints, String fromFactionId)
	{
		if (!isRevengeanceEnabled()) return;
		
		if (!SectorManager.getManager().isHardMode())
			addedPoints *= 1/HARD_MODE_MULT;
		points += addedPoints;
		String debugStr = "Adding revengeance points: " + addedPoints;
		log.info(debugStr);
		if (DEBUG_MODE)
		{
			Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		float pointsToSpawn = Global.getSettings().getFloat("nex_revengeInvasionPointsToSpawn");
		if (points >= pointsToSpawn)
		{
			boolean success = generateRevengeInvasionFleet(fromFactionId);
			if (success) points -= pointsToSpawn;
		}
	}
	
	public float getFactionPoints(String factionId) {
		if (factionPoints.containsKey(factionId))
			return factionPoints.get(factionId);
		return 0;
	}
	
	/**
	 * Add vengeance points for a specific faction (for sat bomb and hunter-killer fleets)
	 * @param factionId
	 * @param points
	 */
	public void addFactionPoints(String factionId, float points)
	{
		if (!isRevengeanceEnabled()) return;
		if (VengeanceFleetIntel.EXCEPTION_LIST.contains(factionId)) return;
		if (!SectorManager.isFactionAlive(factionId)) return;
		
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
		
		points *= VengeanceFleetIntel.VengeanceDef.getDef(factionId).vengefulness * 2;
		points *= VENGEANCE_FLEET_POINT_MULT;
		
		// new style vengeance fleets are a lot likelier to actually catch player, so make them less frequent
		if (NexConfig.useNewVengeanceEncounters) points *= NEW_STYLE_VENGEANCE_POINT_MULT;
		
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
	 * Having incremented faction vengeance points, see if we should launch a sat bomb or hunter-killer fleet
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
	 * Increments the faction vengeance stage, and starts a sat bomb or H/K fleet event
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
		
		generateVengeanceIntel(factionId);
	}
	
	public void generateVengeanceIntel(String factionId) {
		MarketAPI source = pickMarketForFactionVengeance(factionId);
		if (source != null)
		{
			String debugStr = "Spawning faction vengeance fleet for " + factionId + " at " + source.getName();
			log.info(debugStr);
			if (Global.getSettings().isDevMode())
			{
				//Global.getSector().getCampaignUI().addMessage(debugStr);
			}

			VengeanceFleetIntel vengeance = new VengeanceFleetIntel(factionId, source, getVengeanceEscalation(factionId));
			vengeance.startEvent();
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
	public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
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
			String fleetType = NexUtilsFleet.getFleetType(killedFleet);
			if (IGNORED_FLEET_TYPES.contains(fleetType))
				continue;
			if (killedFleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_LOW_REP_IMPACT))
				continue;
			if (killedFleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_NO_REP_IMPACT))
				continue;
			
			List<FleetMemberAPI> killCurrent = killedFleet.getFleetData().getMembersListCopy();
			for (FleetMemberAPI member : killedFleet.getFleetData().getSnapshot()) {
				if (!killCurrent.contains(member)) {
					recentFpKilled += member.getFleetPointCost();
				}
			}
		}
		
		recentFpKilled *= involvedFraction;
		float points = recentFpKilled * NexConfig.revengePointsPerEnemyFP;
		if (points > 0)
		{
			addPoints(points, battle.getNonPlayerCombined().getFaction().getId());
			addFactionPoints(killedFleets.get(0).getFaction().getId(), points * 2f);
		}
	}
	
	public static boolean isRevengeanceEnabled()
	{
		int requiredSetting = 2;
		if (SectorManager.getManager().isHardMode()) requiredSetting = 1;
		log.info("Required revengeance setting: " + requiredSetting);
		if (requiredSetting <= NexConfig.enableRevengeFleets) {
			return true;
		}
		return false;
	}
	
	protected FactionAPI getTargetFactionForVengeance() {
		String factionId = PlayerFactionStore.getPlayerFactionId();
		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(factionId, true);
		if (!factionId.equals(Factions.PLAYER)) {
			markets.addAll(NexUtilsFaction.getFactionMarkets(Factions.PLAYER, true));
		}
		
		if (markets.isEmpty()) return Global.getSector().getPlayerFaction();
		return NexUtils.getRandomListElement(markets).getFaction();
	}
	
	/**
	 * Make a fleet to conquer one of player faction's markets (or potentially sat bomb it)
	 * @param triggeringFactionId The ID of the faction the player most recently committed a hostile act against.
	 * @return True if fleet was successfully created, false otherwise
	 */
	protected boolean generateRevengeInvasionFleet(String triggeringFactionId)
	{
		if (!NexConfig.enableHostileFleetEvents) return false;

		log.info("Trying to generate revenge invasion fleet");
		
		SectorAPI sector = Global.getSector();
		WeightedRandomPicker<String> attackerPicker = new WeightedRandomPicker();
		
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(playerAlignedFactionId, 
				NexConfig.allowPirateInvasions, true, false);
		if (enemies.isEmpty()) return false;
		
		for (String enemyId : enemies)
		{
			// only allow Templars to send counter-invasion fleet if we have no other enemies
			if (enemyId.equals("templars") && enemies.size() > 1)
				continue;
			// don't pick factions hostile to the guys we just pissed off
			if (sector.getFaction(enemyId).isHostileTo(triggeringFactionId))
				continue;
			
			float weight = enemyId.equals(triggeringFactionId) ? 3 : 1;
			
			attackerPicker.add(enemyId, weight);
		}
		String revengeFactionId = attackerPicker.pick();
		if (revengeFactionId == null || revengeFactionId.isEmpty())
			return false;
		FactionAPI revengeFaction = Global.getSector().getFaction(revengeFactionId);
		log.info("Picked enemy: " + revengeFaction.getDisplayName());
		
		// spawn our revengeance fleet
		String debugStr = "Preparing to spawn counter-invasion fleet for " + revengeFactionId;
		log.info(debugStr);
		if (Global.getSettings().isDevMode())
		{
			//Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		
		FactionAPI target = getTargetFactionForVengeance();
		
		boolean satBomb = NexConfig.allowNPCSatBomb && Math.random() < SAT_BOMB_CHANCE 
				&& InvasionFleetManager.canSatBomb(revengeFaction, target);
		InvasionFleetManager.EventType type = InvasionFleetManager.EventType.INVASION;
		if (satBomb) {
			type = InvasionFleetManager.EventType.SAT_BOMB;
		}
		else if (!NexConfig.enableInvasions || !NexConfig.getFactionConfig(revengeFactionId).canInvade) 
		{
			type = InvasionFleetManager.EventType.RAID;
		}
		
		RequisitionParams params = new RequisitionParams();
		params.thresholdBeforeAbort = -NexConfig.pointsRequiredForInvasionFleet;
		params.amountMult = 0.75f;
		
		OffensiveFleetIntel intel = InvasionFleetManager.getManager().generateInvasionOrRaidFleet(
				revengeFaction, getTargetFactionForVengeance(), type, 1.25f, params);
		if (intel != null && satBomb) {
			((SatBombIntel)intel).setVengeance(true);
		}
		if (intel != null) {
			// counter-invasions draw on the same points needed for invasions, but at half the normal rate
			float cost = InvasionFleetManager.getInvasionPointCost(intel);
			cost *= INVASION_POINT_COST_MULTIPLIER;
			intel.setInvPointsSpent((int)cost);
			InvasionFleetManager.getManager().modifySpawnCounterV2(revengeFactionId, -cost);
		}
		
		return true;
	}
	
	public boolean generateRetaliationForSatBomb(String factionId) {
		log.info("Processing revenge for sat bomb");
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI target = Global.getSector().getPlayerFaction();
		OffensiveFleetIntel intel = null;
				
		if (InvasionFleetManager.canSatBomb(faction, target, true)) {		
			InvasionFleetManager.EventType type = InvasionFleetManager.EventType.SAT_BOMB;
			
			RequisitionParams params = new RequisitionParams();
			//params.thresholdBeforeAbort = -NexConfig.pointsRequiredForInvasionFleet;
			params.amountMult = 0.5f;

			intel = InvasionFleetManager.getManager().generateInvasionOrRaidFleet(
					faction, getTargetFactionForVengeance(), type, 1.6f, params);
			if (intel != null) {
				((SatBombIntel)intel).setVengeance(true);
				intel.setQualityOverride(1.25f);
				// counter-invasions draw on the same points needed for invasions, but at half the normal rate
				float cost = InvasionFleetManager.getInvasionPointCost(intel);
				cost *= INVASION_POINT_COST_MULTIPLIER;
				intel.setInvPointsSpent((int)cost);
				InvasionFleetManager.getManager().modifySpawnCounterV2(factionId, -cost);
				log.info("Dispatching retaliatiory sat bomb fleet");
			}
		}
		
		// no intel? spawn kill fleet instead
		if (intel == null) {
			log.info("Dispatching sat bombing hunter fleet");
			float playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet());
			int capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus());
			VengeanceDef def = VengeanceDef.getDef(factionId);
			int escalationLevel = def.maxLevel;

			int combat = Math.round(playerStr + capBonus);
			
			DelayedFleetEncounter e = new DelayedFleetEncounter(null, "nex_vengeance_satbomb");
			e.setTypes(DelayedFleetEncounter.EncounterType.OUTSIDE_SYSTEM, 
					DelayedFleetEncounter.EncounterType.IN_HYPER_EN_ROUTE);
			e.setDelay(30, 40);
			e.setLocationAnywhere(true, factionId);
			e.setDoNotAbortWhenPlayerFleetTooStrong();
			e.beginCreate();
			e.triggerCreateFleet(HubMissionWithTriggers.FleetSize.LARGE, 
					HubMissionWithTriggers.FleetQuality.SMOD_2, 
					factionId, 
					"vengeanceFleet",
					new Vector2f());						
			NexUtilsFleet.setTriggerFleetFP(faction, combat, e);
			
			e.triggerFleetMakeFaster(true, 2, true);
			e.triggerSetStandardAggroInterceptFlags();
			e.triggerOrderFleetMaybeEBurn();
			e.triggerSetFleetMemoryValue("$clearCommands_no_remove", true);
			e.triggerSetFleetMemoryValue("$escalation", (float)escalationLevel);
			e.triggerSetFleetMemoryValue(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);			
			e.triggerFleetSetName(def.getFleetName(factionId, escalationLevel));
			e.endCreate();
		}
		
		return true;
	}
	
	
	/**
	 * Select a source market for faction vengeance fleets.
	 * @param factionId
	 * @return
	 */
	public MarketAPI pickMarketForFactionVengeance(String factionId) 
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
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
			if (market.isHidden()) continue;	// otherwise a vengeance fleet originating from a pirate base may lead to breaking pirate king deal
			if (!NexUtilsMarket.hasWorkingSpaceport(market)) continue;
			
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
	
	public void addVengeanceForMarketAttack(MarketAPI market, int size, float mult)
	{
		addVengeanceForMarketAttack(market, size, mult, true);
	}
	
	/**
	 * Increments vengeance points from raiding or bombarding a market.
	 * @param market
	 * @param size
	 * @param mult Varies based on attack type
	 * @param useSizeSquare If true, points are based on the square of market size; else based on just market size.
	 */
	public void addVengeanceForMarketAttack(MarketAPI market, int size, float mult, boolean useSizeSquare)
	{
		float addedPoints = size * mult;
		if (useSizeSquare) addedPoints *= size;
		String factionId = market.getFactionId();
		log.info("Adding vengeance points for market attack: " + market.getName() + ", " + addedPoints + " (mult " + mult + ")");
		addPoints(addedPoints/2, factionId);
		addFactionPoints(market.getFactionId(), addedPoints);
		
		// war weariness too
		// not really the right place for it, but saves us having to add its own listener
		DiplomacyManager.getManager().modifyWarWeariness(factionId, addedPoints * 5);
	}
		
	public void updateSatBombDeathToll(String factionId, int size) {
		double before = Math.pow(10, size);
		//double after = Math.pow(10, size - 1);
		//int deaths = (int)Math.round(before - after);
		int deaths = (int)Math.round(before);
		
		deathTollSinceLastVengeance += deaths;
		if (deathTollSinceLastVengeance >= SAT_BOMB_DEATHS_FOR_REVENGE) {
			log.info("Death toll reached " + Misc.getWithDGS(deathTollSinceLastVengeance) + ", triggering revengeance");
			RevengeanceManager.getManager().generateRetaliationForSatBomb(factionId);
			deathTollSinceLastVengeance = 0;
		}
		satBombDeathToll += deaths;
	}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {
		addVengeanceForMarketAttack(market, market.getSize(), 0.75f, false);
	}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {
		addVengeanceForMarketAttack(market, market.getSize(), 0.75f, false);
	}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
		addVengeanceForMarketAttack(market, market.getSize(), 1, true);
	}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
		addVengeanceForMarketAttack(market, market.getSize() + 1, 10, true);
	}
	
}
