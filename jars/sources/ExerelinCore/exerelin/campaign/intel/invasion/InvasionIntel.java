package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.econ.GroundPoolManager;
import exerelin.campaign.econ.ResourcePoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.campaign.intel.fleets.*;
import exerelin.campaign.intel.groundbattle.*;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel.BattleOutcome;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class InvasionIntel extends OffensiveFleetIntel implements RaidDelegate, 
		GroundBattleCampaignListener {
	
	public static final boolean NO_STRIKE_FLEETS = true;
	public static final boolean USE_REAL_MARINES = false;	// puts actual marines in cargo; undesirable because they appear in salvage
	public static final int MAX_MARINES_PER_FLEET = 3000;
	public static final int WAIT_AFTER_SUCCESS_DAYS = 90;
	public static final int MAX_MARINES_TOTAL = 16000;
	public static final float MARINE_GARRISION_MULT = 0.75f;
	public static final float MARINE_NON_BOMBABLE_MULT = 1.3f;
	public static final float RESPAWN_MARINE_MULT = 1.4f;
	
	public static Logger log = Global.getLogger(InvasionIntel.class);
	
	@Deprecated protected int marinesPerFleet = 0;	// used for legacy invasions? maybe we should just use total marines too
	protected int marinesTotal = 0;	// used for new invasions
	protected float fpNoBrawlMult;
	protected DefenseFleetIntel brawlDefIntel;
	protected GroundBattleIntel groundBattle;
	protected WaitStage waitStage;
	
	protected boolean playerStoleOurTarget = false;
	protected ReputationAdjustmentResult stealRepPenalty;
	protected float stealRepAfter;
	
	public InvasionIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
		fpNoBrawlMult = fp;
	}
	
	protected Object readResolve() {
		if (marinesTotal == -1) {
			marinesTotal = (int)Math.ceil(marinesPerFleet * 1.5f);
		}
		if (fpNoBrawlMult == 0) {
			log.info("Resetting invasion FP value (before brawl mult)");
			fpNoBrawlMult = fp;
		}
		
		return this;
	}
	
	@Override
	public void init() {
		log.info("Creating invasion intel");
		
		initBrawlMode();
		
		SectorEntityToken gather = from.getPrimaryEntity();
		
		addStage(new NexOrganizeStage(this, from, orgDur));
		
		float successMult = 0.4f;
		InvAssembleStage assemble = new InvAssembleStage(this, gather);
		assemble.addSource(from);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());
		if (raidJump == null) {
			endImmediately();
			return;
		}

		NexTravelStage travel = new NexTravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new InvActionStage(this, target);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		// aggressive = false so invasion fleets stay orbiting the planet and only move if their buddies are under attack
		waitStage = new InvWaitStage(this, target.getPrimaryEntity(), WAIT_AFTER_SUCCESS_DAYS, false);
		addStage(waitStage);
		
		//addStage(new NexReturnStage(this));	// sending fleets home is handled by wait stage
		
		setMarineCount();
		
		if (brawlMode) {
			spawnBrawlDefenseFleet();
		}

		if (ExerelinModPlugin.isNexDev) {
			//Global.getSector().getCampaignUI().addMessage("init called in InvasionIntel");
		}
		
		Global.getSector().getListenerManager().addListener(this);

		int nexIntelQueued = NexConfig.nexIntelQueued;
		switch (nexIntelQueued) {

			case 0:
				addIntelIfNeeded();
				break;

			case 1:
				if ((isPlayerTargeted() || playerSpawned || targetFaction == Misc.getCommissionFaction()) || faction == Misc.getCommissionFaction())
					addIntelIfNeeded();
				else if (shouldDisplayIntel())
					queueIntelIfNeeded();
				break;

			case 2:
				if (playerSpawned)
					addIntelIfNeeded();
				else if (shouldDisplayIntel()) {
					Global.getSector().getIntelManager().queueIntel(this);
					intelQueuedOrAdded = true;
				}
				break;

			default:
				addIntelIfNeeded();
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in InvasionIntel, " +
					"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
					"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
	}
	
	/**
	 * Do we think we'll be allowed to tactically bombard the target when we get there?
	 * @return
	 */
	protected boolean expectBombable() {
		if (target.hasCondition(Conditions.HABITABLE) && !target.hasCondition(Conditions.POLLUTION))
			return false;
		float def = Nex_MarketCMD.getBombardmentCost(target, null);
		return def < 3000;
	}

	public void setMarineCount() {
		// based on vanilla ground defense strength
		/*
		float defenderStrength = InvasionRound.getDefenderStrength(target, 0.55f);
		marinesPerFleet = (int)(defenderStrength * InvasionFleetManager.DEFENDER_STRENGTH_MARINE_MULT);
		if (marinesPerFleet < 100) {
			marinesPerFleet = 100;
		}
		else if (marinesPerFleet > MAX_MARINES_PER_FLEET) {
			log.info("Capping marines per fleet (legacy) at " + MAX_MARINES_PER_FLEET + " (was " + marinesPerFleet + ")");
			marinesPerFleet = MAX_MARINES_PER_FLEET;
		}
		*/
		
		// marinesPerFleet is no longer used except for debugging
		{
			float defenderStrength = InvasionRound.getDefenderStrength(target, 0.55f);
			marinesPerFleet = (int)defenderStrength;
		}
		
		if (NexConfig.legacyInvasions) {
			float defenderStrength = InvasionRound.getDefenderStrength(target, 0.75f);
			marinesTotal = (int)(defenderStrength * InvasionFleetManager.DEFENDER_STRENGTH_MARINE_MULT);
			marinesTotal *= NexConfig.groundBattleInvasionTroopSizeMult;
		}

		// base on Nex new invasion mechanic garrison
		else {
			float garrison = GBUtils.estimateTotalDefenderStrength(target, faction, false);
			garrison /= NexConfig.groundBattleGarrisonSizeMult;
			marinesTotal = (int)Math.ceil(garrison * MARINE_GARRISION_MULT);
			
			if (!expectBombable()) {
				marinesTotal *= MARINE_NON_BOMBABLE_MULT;
			}

			marinesTotal *= NexConfig.groundBattleInvasionTroopSizeMult;
		}
		if (marinesTotal < 100) marinesTotal = 100;
		else if (marinesTotal > MAX_MARINES_TOTAL) {
			log.info("Capping total marines at " + MAX_MARINES_TOTAL + " (was " + marinesTotal + ")");
			marinesTotal = MAX_MARINES_TOTAL;
		}
		
		if (this instanceof RespawnInvasionIntel) {
			marinesTotal *= RESPAWN_MARINE_MULT;
		}

		// draw from ground pool
		ResourcePoolManager.RequisitionParams rp = new ResourcePoolManager.RequisitionParams(marinesTotal * GroundPoolManager.POOL_PER_MARINE);
		rp.abortIfNotAtLeast = 0;	// always have the marines we need
		GroundPoolManager.getManager().drawFromPool(faction.getId(), rp);
	}

	/*
	@Override
	public void setOutcome(OffensiveOutcome outcome) {
		super.setOutcome(outcome);
		// don't wait for WaitStage to finish
		// normal return stage doesn't need it since it completes immediately
		// ... no don't do this, this stops the current stage from advance()ing
		endAfterDelay();	
	}
	*/
	
	@Deprecated
	public int getMarinesPerFleet() {
		return marinesPerFleet;
	}
	
	@Deprecated
	public void setMarinesPerFleet(int marines) {
		marinesPerFleet = marines;
	}
	
	public int getMarinesPerFleetV2(RouteData route) {
		float fpShare = route.getExtra().fp/fpNoBrawlMult;
		return (int)Math.ceil(marinesTotal * fpShare);
	}
	
	public void setMarinesTotal(int marines) {
		marinesTotal = marines;
	}
	
	public GroundBattleIntel getGroundBattle() {
		return groundBattle;
	}
	
	public boolean hasOngoingNonJoinableBattle() {
		GroundBattleIntel curr = GroundBattleIntel.getOngoing(target);
		if (curr == null) return false;
		
		Boolean joinAttacker = curr.getSideToSupport(faction, false);
		return joinAttacker == null;
	}
	
	public GroundBattleIntel initGroundBattle() {
		if (groundBattle != null) return groundBattle;
		
		// if there's already a battle going on, use that if we can
		
		groundBattle = GroundBattleIntel.getOngoing(target);
		if (groundBattle != null && groundBattle.getOutcome() == null) {
			// terminate the entire invasion if we're not friendly to either side
			// no, just wait for it to end
			Boolean joinAttacker = groundBattle.getSideToSupport(faction, false);
			if (joinAttacker == null) {
				groundBattle = null;
				//terminateEvent(OffensiveOutcome.OTHER);
				return null;
			}
			
			return groundBattle;
		}
		
		GroundBattleIntel newBattle = new GroundBattleIntel(target, this.faction, target.getFaction());
		newBattle.init();
		newBattle.start();
		groundBattle = newBattle;
		return newBattle;
	}
	
	public void deployToGroundBattle(CampaignFleetAPI fleet) {
		deployToGroundBattle(getRouteFromFleet(fleet));
	}
	
	public void deployToGroundBattle(RouteData route) {
		if (route == null) return;
		
		if (isRouteActionDone(route)) {
			if (ExerelinModPlugin.isNexDev)
				Global.getSector().getCampaignUI().addMessage("Route double deploying to battle: " + route.toString());
			return;
		}
		
		if (groundBattle == null) {
			// TODO print error message
			return;
		}
		
		Boolean side = groundBattle.getSideToSupport(faction, false);
		if (side == null) {
			// TODO print error message
			return;
		}
				
		int marines = getMarinesPerFleetV2(route);
		int heavyArms = 0;
		
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet != null) {
			CargoAPI cargo = route.getActiveFleet().getCargo();
			if (USE_REAL_MARINES) {
				marines = cargo.getMarines();
			} else {
				int capacity = (int)cargo.getMaxPersonnel();
				int skeletonCrew = (int)fleet.getFleetData().getMinCrew();
				log.info(String.format("Fleet has %s personnel capacity, minus %s skeleton crew, leaving %s space for marines", 
						capacity, skeletonCrew, capacity - skeletonCrew));
				// drop fewer marines if there's an active fleet, since we have fire support from fleet existing
				marines = Math.round(marines * 0.75f);
				marines = Math.min(marines, capacity - skeletonCrew);
			}
			heavyArms = (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS);
		}
		else {
			heavyArms = marines/5;
			if (target.getPlanetEntity() == null) heavyArms /= 2;
		}
		heavyArms = Math.min(heavyArms, marines/GroundUnitDef.getUnitDef(GroundUnitDef.HEAVY).personnel.mult);
		
		// first boots to hit the ground
		boolean firstIn = groundBattle.getSide(side).getUnits().isEmpty();
		
		// create units
		log.info(String.format("Deploying units: %s marines, %s heavy arms", marines, heavyArms));
		groundBattle.autoGenerateUnits(marines, heavyArms, faction, side, false, fleet);
		
		// deploy the newly arrived units
		if (true || !firstIn) {
			groundBattle.runAI(side, false);
		}
	}
	
	public void initBrawlMode() {
		if (!Global.getSettings().getBoolean("nex_brawlMode"))
			return;
		
		if (this instanceof CounterInvasionIntel)
			return;
		
		brawlMode = true;
		float min = Global.getSettings().getFloat("nex_brawlMode_minMult");
		float max = Global.getSettings().getFloat("nex_brawlMode_maxMult");
		brawlMult = MathUtils.getRandomNumberInRange(min, max);
		log.info("Setting brawl mult: " + brawlMult);
		
		fp *= brawlMult;
	}
	
	public void spawnBrawlDefenseFleet() {
		float eta = getETA();
		
		float defFP = baseFP * (brawlMult - 1);
		defFP *= MathUtils.getRandomNumberInRange(0.8f, 0.9f);
		
		log.info("Preparing brawl defense fleet: strength " + defFP + ", ETA " + eta * 0.8f);
		brawlDefIntel = new DefenseFleetIntel(target.getFaction(), target, target, defFP, eta * 0.8f);
		if (!ExerelinModPlugin.isNexDev)
			brawlDefIntel.setSilent();
		brawlDefIntel.setRequiresSpaceportOrBase(false);
		brawlDefIntel.setBrawlMode(true);
		brawlDefIntel.init();
	}
	
	public DefenseFleetIntel getBrawlDefIntel() {
		return brawlDefIntel;
	}
	
	@Override
	public boolean shouldMakeImportantIfTargetingPlayer() {
		return true;
	}
	
	protected String getDescString() {
		return StringHelper.getString("exerelin_invasion", "intelDesc");
	}
	
	// intel long description in intel screen
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		//super.createSmallDescription(info, width, height);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);
		
		FactionAPI attacker = getFaction();
		FactionAPI defender = target.getFaction();
		if (defender == attacker) defender = targetFaction;
		String locationName = target.getContainingLocation().getNameWithLowercaseType();
		
		String strDesc = getRaidStrDesc();
		
		String string = getDescString();
		String attackerName = attacker.getDisplayNameWithArticle();
		String defenderName = defender.getDisplayNameWithArticle();
		int numFleets = (int) getOrigNumFleets();
				
		Map<String, String> sub = new HashMap<>();
		sub.put("$theFaction", attackerName);
		sub.put("$TheFaction", Misc.ucFirst(attackerName));
		sub.put("$theTargetFaction", defenderName);
		sub.put("$TheTargetFaction", Misc.ucFirst(defenderName));
		sub.put("$market", target.getName());
		sub.put("$isOrAre", attacker.getDisplayNameIsOrAre());
		sub.put("$location", locationName);
		sub.put("$strDesc", strDesc);
		sub.put("$numFleets", numFleets + "");
		sub.put("$fleetsStr", numFleets > 1 ? StringHelper.getString("fleets") : StringHelper.getString("fleet"));
		string = StringHelper.substituteTokens(string, sub);
		
		LabelAPI label = info.addPara(string, opad);
		label.setHighlight(attacker.getDisplayNameWithArticleWithoutArticle(), target.getName(), 
				defender.getDisplayNameWithArticleWithoutArticle(), strDesc, numFleets + "");
		label.setHighlightColors(attacker.getBaseUIColor(), h, defender.getBaseUIColor(), h, h);
		
		if (Global.getSettings().isDevMode() || ExerelinModPlugin.isNexDev) {
			float fpRound = Math.round(fp);
			float baseFP = Math.round(InvasionFleetManager.getWantedFleetSize(getFaction(), target, 0, false, 9999));
			info.addPara("DEBUG: The invasion's starting FP is " + fpRound 
					+ ". At current strength, the base FP desired for the target is approximately " 
					+ baseFP + ".", opad, Misc.getHighlightColor(), fpRound + "", baseFP + "");
		}
		
		if (outcome == null) {
			addStandardStrengthComparisons(info, target, targetFaction, true, false, null, null);
			if (brawlMode) {
				string = StringHelper.getString("exerelin_invasion", "intelBrawl");
				string = StringHelper.substituteToken(string, "$theTargetFaction", defenderName, true);
				info.addPara(string, opad);
			}
		}

		addStrategicActionInfo(info, width);
		
		info.addSectionHeading(StringHelper.getString("status", true), 
				attacker.getBaseUIColor(), attacker.getDarkUIColor(), Alignment.MID, opad);
		
		// write our own status message for certain cancellation cases
		boolean endDesc = addCustomOutcomeDesc(info, sub);
		if (endDesc) return;
		
		for (RaidStage stage : stages) {
			stage.showStageInfo(info);
			if (getStageIndex(stage) == failStage) break;
		}
		
		if (ExerelinModPlugin.isNexDev && (isEnding() || isEnded())) {
			info.addPara("The event is now over.", opad);
		}
	}
	
	/**
	 * Writes custom outcome text to the small description.
	 * @param info
	 * @param sub
	 * @return True if {@code createSmallDescription} should exit, false otherwise.
	 */
	protected boolean addCustomOutcomeDesc(TooltipMakerAPI info, Map<String, String> sub) {
		String string;
		float opad = 10;
		
		if (outcome == OffensiveOutcome.NO_LONGER_HOSTILE)
		{
			string = StringHelper.getString("nex_fleetIntel", "outcomeNoLongerHostile");
			string = StringHelper.substituteToken(string, "$target", target.getName());
			string = StringHelper.substituteToken(string, "$theAction", getActionNameWithArticle());
			//String factionName = target.getFaction().getDisplayName();
			//string = StringHelper.substituteToken(string, "$otherFaction", factionName);
			
			info.addPara(string, opad);
		
			if (stealRepPenalty != null) {
				string = StringHelper.getString("exerelin_invasion", "intelStealPenalty");
				string = StringHelper.substituteTokens(string, sub);
				string = StringHelper.substituteToken(string, "$thePlayerFaction", PlayerFactionStore.getPlayerFaction().getDisplayNameWithArticle());
				info.addPara(string, opad);
				DiplomacyIntel.addRelationshipChangePara(info, faction.getId(), 
						PlayerFactionStore.getPlayerFactionId(), stealRepAfter, stealRepPenalty, opad);
			}
			
			return true;
		}
		else if (outcome == OffensiveOutcome.MARKET_NO_LONGER_EXISTS)
		{
			string = StringHelper.getString("nex_fleetIntel", "outcomeNoLongerExists");
			string = StringHelper.substituteToken(string, "$target", target.getName());
			//string = StringHelper.substituteToken(string, "$theAction", getActionNameWithArticle());
			info.addPara(string, opad);
			return true;
		}
		return false;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		super.addBulletPoints(info, mode, isUpdate, tc, initPad);
		if (isUpdate && stealRepPenalty != null) {
			
			String reason = StringHelper.getString("exerelin_invasion", "intelStealBulletReason");
			CoreReputationPlugin.addAdjustmentMessage(stealRepPenalty.delta, faction, 
					null, null, null, info, tc, true, 0, reason);
		}
	}
	
	@Override
	public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
		if (random == null) random = new Random();
				
		RouteManager.OptionalFleetData extra = route.getExtra();
		
		// idea: only one fleet is the actual invasion fleet; rest are strike fleets supporting it
		// not sure that'll even work given the spawn/despawn behavior
		boolean isInvasionFleet = extra.fleetType.equals("exerelinInvasionFleet");
		float distance = NexUtilsMarket.getHyperspaceDistance(market, target);
		
		float myFP = extra.fp;
		if (!isInvasionFleet) myFP *= 0.75f;
		float fpBeforeDoctrineMult = myFP;
		
		if (!useMarketFleetSizeMult)
			myFP *= InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		
		int marines = 0;
		if (isInvasionFleet) {			
			if (false && NexConfig.legacyInvasions) {
				marines = marinesPerFleet;
			}
			else {
				marines = getMarinesPerFleetV2(route);
			}
		}
		
		float combat = myFP;
		float tanker = getWantedTankerFP(myFP, distance, random);
		if (tanker > myFP * 0.25f) tanker = myFP * 0.25f;
		float transport = Math.max(marines/100, 8);	// a bit more than enough for a Valk
		float freighter = getWantedFreighterFP(myFP, random);
		
		if (isInvasionFleet) freighter *= 2;
		
		float totalFp = combat + tanker + transport + freighter;
		
		FleetParamsV3 params = new FleetParamsV3(
				market, 
				locInHyper,
				factionId,
				route == null ? null : route.getQualityOverride(),
				extra.fleetType,
				combat, // combatPts
				freighter, // freighterPts 
				tanker, // tankerPts
				transport, // transportPts
				0f, // linerPts
				0f, // utilityPts
				0f // qualityMod, won't get used since routes mostly have quality override set
				);
		
		params.maxNumShips = Math.round(Global.getSettings().getMaxShipsInFleet() * 1.2f);
		//params.averageSMods = 1;	// once we adjust autoresolve strength?
		
		// we don't need the variability involved in this
		if (!useMarketFleetSizeMult)
			params.ignoreMarketFleetSizeMult = true;
		
		params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
		params.qualityOverride = this.qualityOverride;
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		fleet.setName(InvasionFleetManager.getFleetName(extra.fleetType, factionId, totalFp));
				
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		// makes it not piss around the system instead of heading to objective, see http://fractalsoftworks.com/forum/index.php?topic=5061.msg263438#msg263438
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);
		if (isInvasionFleet)
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);	// needed to do raids
		
		if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
		}
		
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		fleet.getMemoryWithoutUpdate().set("$nex_routeData", route);
		
		String postId = Ranks.POST_FLEET_COMMANDER;
		String rankId = isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_CAPTAIN;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		if (marines > 0) {
			if (USE_REAL_MARINES) {
				fleet.getCargo().addMarines(marines);
				log.info("Adding marines to cargo: " + marines);
			}
			int heavyArms = marines/5;
			if (target.getPlanetEntity() == null) heavyArms /= 2;
			fleet.getCargo().addCommodity(Commodities.HAND_WEAPONS, heavyArms);
		}
		
		// this makes some stuff not think that strike fleets are available as raiders
		if (!isInvasionFleet) {
			setRouteActionDone(fleet);
		}
		
		//log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		log.info("Created fleet " + fleet.getName() + ", FP used: " + fpBeforeDoctrineMult + "/" + fp);
		
		return fleet;
	}
	
	@Override
	public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
		RaidAssignmentAINoWander raidAI = new RaidAssignmentAINoWander(this, fleet, route, 
				outcome == null ? (InvActionStage)action : waitStage);
		return raidAI;
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("exerelin_invasion", "invasion", true);
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("exerelin_invasion", "invasion");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("exerelin_invasion", "theInvasion");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("exerelin_invasion", "invasionForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("exerelin_invasion", "theInvasionForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("exerelin_invasion", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("exerelin_invasion", "forceIsOrAre");
	}

	@Override
	public String getType() {
		return "invasion";
	}

	@Override
	public float getEstimatedRaidEffectiveness(MarketAPI target) {
		float re;
		if (NexConfig.legacyInvasions) {
			float ourGroundStr = marinesTotal * (1 + NexConfig.getFactionConfig(faction.getId())
					.invasionStrengthBonusAttack);
			re = Nex_MarketCMD.getRaidEffectiveness(target, ourGroundStr);
		}
		else {
			GroundBattleIntel temp = new GroundBattleIntel(target, faction, target.getFaction());
			temp.init();
			float enemyGroundStr = GBUtils.estimateTotalDefenderStrength(temp, true);
			float ourGroundStrAdj = marinesTotal * 1.25f;

			boolean canBomb = expectBombable();
			if (!canBomb) {
				float attrMult = 1 - temp.getSide(true).getDropAttrition().getModifiedValue()/100;
				ourGroundStrAdj *= (attrMult * attrMult);	// square the effect due to morale effects
			}

			re = ourGroundStrAdj/(ourGroundStrAdj + enemyGroundStr);
		}
		return re;
	}
	
	@Override
	public String getName() {
		boolean useDeployedText = outcome == OffensiveOutcome.SUCCESS;
		useDeployedText &= (groundBattle != null && groundBattle.getOutcome() != BattleOutcome.ATTACKER_VICTORY);
		
		if (useDeployedText) {
			String base = StringHelper.getString("nex_fleetIntel", "title");
			base = StringHelper.substituteToken(base, "$action", getActionName(), true);
			base = StringHelper.substituteToken(base, "$market", getTarget().getName());

			return base + " - " + StringHelper.getString("exerelin_invasion", "intelTitleSuffixDeployed", true);
		}
		return super.getName();
	}
		
	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "nex_invasion");
		//return faction.getCrest();
	}
	
	@Override
	public String getCommMessageSound() {
		if (isPlayerTargeted() && !isSendingUpdate()) {
			return "nex_alarm";
		}
		else if (target.getFaction() == Misc.getCommissionFaction() && !isSendingUpdate()) {
			return getSoundColonyThreat();
		}
		return super.getCommMessageSound();
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		if (outcome == OffensiveOutcome.SUCCESS) return 15;
		return 7;
	}
	
	@Override
	protected void notifyEnding() {
		log.info("Invasion event ending");
		if (ExerelinModPlugin.isNexDev) {
			//Global.getSector().getCampaignUI().addMessage("notifyEnding() called in InvasionIntel " + getName() + ", " + (getPlayerVisibleTimestamp() == null));
		}
		super.notifyEnding();
		if (brawlDefIntel != null && brawlDefIntel.getOutcome() == null) {
			if (outcome == null) return;
			log.info("Setting outcome for brawl defense, " + outcome.toString());
			if (outcome.isFailed()) {
				brawlDefIntel.reportOutcome(OffensiveOutcome.SUCCESS);
				brawlDefIntel.endAfterDelay();
				brawlDefIntel.sendOutcomeUpdate();
			}
			else {
				brawlDefIntel.terminateEvent(OffensiveOutcome.FAIL);
			}
			brawlDefIntel.giveReturnOrders();
		}
	}
	
	@Override
	public void checkForTermination() {
		if (outcome != null) return;
		
		// ground battle finished and we've already taken the planet, mark as success
		if (groundBattle != null && groundBattle.getOutcome() != null && !faction.isHostileTo(target.getFaction())) 
		{
			int currStage = getCurrentStage();
			int actStage = getStageIndex(action);
			if ((currStage == actStage) && action instanceof InvActionStage) {
				((InvActionStage)action).succeed(true);
				return;
			}
		}
		
		// to proceed, planet must either have an ongoing ground battle or be hostile
		if (!faction.isHostileTo(target.getFaction()) && GroundBattleIntel.getOngoing(target) == null) {
			terminateEvent(OffensiveOutcome.NO_LONGER_HOSTILE);
			return;
		}
		
		super.checkForTermination();
	}
	
	@Override
	public void terminateEvent(OffensiveOutcome outcome) {
		if (playerStoleOurTarget && outcome == OffensiveOutcome.NO_LONGER_HOSTILE) {
			// rep loss
			stealRepPenalty = DiplomacyManager.adjustRelations(this.faction, PlayerFactionStore.getPlayerFaction(), 
					-target.getSize() * 0.02f, null, null, RepLevel.INHOSPITABLE);
			stealRepAfter = faction.getRelToPlayer().getRel();
		} 
		super.terminateEvent(outcome);
	}

	@Override
	public InvasionFleetManager.EventType getEventType() {
		return InvasionFleetManager.EventType.INVASION;
	}

	@Override
	public void reportBattleStarted(GroundBattleIntel battle) {
		if (battle.getMarket() == target && battle.isPlayerInitiated()
				&& getCurrentStage() == getStageIndex(action)
				&& battle.getSideToSupport(faction, false) == null) {
			playerStoleOurTarget = true;
		}
	}

	@Override
	public void reportBattleBeforeTurn(GroundBattleIntel battle, int turn) {}

	@Override
	public void reportBattleAfterTurn(GroundBattleIntel battle, int turn) {}

	@Override
	public void reportBattleEnded(GroundBattleIntel battle) {}

	@Override
	public void reportPlayerJoinedBattle(GroundBattleIntel battle) {}
}
