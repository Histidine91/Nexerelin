package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import exerelin.utilities.ReflectionUtils;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseFleetEventListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantStationFleetManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Objectives;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.abilities.ai.AlwaysOnTransponderAI;
import exerelin.campaign.intel.missions.BuildStation;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinNewGameSetup;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;

// aka "Showdown"
public class RemnantBrawl extends HubMissionWithBarEvent implements FleetEventListener, CurrentLocationChangedListener {
	
	public static Logger log = Global.getLogger(RemnantBrawl.class);
	
	public static final float STRAGGLER_LOST_ATTACK_DELAY = 10;
	public static final float STAGING_AREA_FOUND_ATTACK_DELAY = 3.5f;
	public static final int BATTLE_MAX_DAYS = 45;
	public static final float DISTANCE_TO_SPAWN_STRAGGLER = 1f;
	public static final float STRAGGLER_WAIT_TIME = 3.5f;
	public static final float SUS_EXPIRE = 6;

	public static enum Stage {
		GO_TO_ORIGIN_SYSTEM,
		FOLLOW_STRAGGLER,
		GO_TO_TARGET_SYSTEM,
		BATTLE,
		SCOUT,
		BATTLE_DEFECTED,
		COMPLETED,
		FAILED,
	}
	
	//@Deprecated protected PersonAPI dissonant;
	protected MarketAPI stragglerOrigin;
	protected StarSystemAPI stagingArea;
	protected SectorEntityToken stagingPoint;
	protected SectorEntityToken scoutPoint;
	protected CampaignFleetAPI station;
	protected CampaignFleetAPI straggler;
	protected SectorEntityToken stationToken;
	protected boolean stationIsStrong = false;
	
	protected PersonAPI admiral;
	
	protected Set<CampaignFleetAPI> createdFleets = new HashSet<>();
	protected Set<CampaignFleetAPI> attackFleets = new HashSet<>();
	
	protected boolean spawnedStraggler;
	protected boolean spawnedAttackFleets;
	protected boolean checkedExtraDefenders2;
	protected boolean launchedAttack;
	protected boolean orderedHangAboveSystem;
	protected boolean knowStagingArea;
	protected boolean battleInited;
	protected boolean betrayed;
	protected boolean sentFalseInfo;
	
	protected float battleTimer = 0;
	
	// runcode exerelin.campaign.intel.missions.remnant.RemnantBrawl.fixDebug()
	public static void fixDebug() {
		RemnantBrawl mission = (RemnantBrawl)Global.getSector().getMemoryWithoutUpdate().get("$nex_remBrawl_ref");

		for (CampaignFleetAPI fleet : mission.attackFleets) {
			fleet.clearAssignments();
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, mission.stationToken, 20,
					StringHelper.getFleetAssignmentString("attacking", mission.station.getName()));
			fleet.addAssignment(FleetAssignment.INTERCEPT, mission.station, 20);
		}
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		
		if (!setGlobalReference("$nex_remBrawl_ref")) {
			return false;
		}
		
		/*
		dissonant = getImportantPerson(RemnantQuestUtils.PERSON_DISSONANT);
		
		if (dissonant == null) {
			log.info("Person is null");
			return false;
		}
		personOverride = dissonant;
		*/
		
		station = pickStation();
		if (station == null) return false;
		stationIsStrong = !station.getMemoryWithoutUpdate().getBoolean("$damagedStation");
		
		//log.info(String.format("Picked nexus in %s", station.getContainingLocation().getNameWithLowercaseTypeShort()));
		
		// pick straggler origin
		requireMarketFaction(Factions.HEGEMONY);
		requireMarketNotHidden();
		requireMarketNotInHyperspace();
		preferMarketSizeAtLeast(5);
		preferMarketIsMilitary();
		stragglerOrigin = pickMarket();
		if (stragglerOrigin == null) return false;
		
		// pick staging area
		//requireSystemHasNumPlanets(1);
		requireSystemNotHasPulsar();
		requireSystemNotBlackHole();
		requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_UNSAFE, Tags.THEME_CORE, Tags.THEME_REMNANT, 
				Tags.TRANSIENT, Tags.SYSTEM_CUT_OFF_FROM_HYPER, Tags.THEME_HIDDEN);
		requireSystemWithinRangeOf(station.getContainingLocation().getLocation(), 12);
		search.systemReqs.add(new BuildStation.SystemUninhabitedReq());
		preferSystemOutsideRangeOf(station.getContainingLocation().getLocation(), 7);
		// prefer staging areas closer to our start location than the station is
		preferSystemWithinRangeOf(stragglerOrigin.getLocationInHyperspace(), 
				Misc.getDistanceLY(station, stragglerOrigin.getPrimaryEntity()) - 1);
		preferSystemUnexplored();
		stagingArea = pickSystem();
		if (stagingArea == null) return false;
		
		LocData loc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, stagingArea);
		stagingPoint = spawnMissionNode(loc);
		if (!setEntityMissionRef(stagingPoint, "$nex_remBrawl_ref")) return false;
		makeImportant(stagingPoint, "$nex_remBrawl_target", Stage.FOLLOW_STRAGGLER);
		stagingPoint.setDiscoverable(true);
		stagingPoint.setSensorProfile(0f);
		stagingPoint.addTag(Tags.NON_CLICKABLE);
		
		setStoryMission();		
				
		makeImportant(station, "$nex_remBrawl_target", Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE, Stage.BATTLE_DEFECTED);
		//makeImportant(dissonant, "$nex_remM1_returnHere", Stage.RETURN_CORES);
		
		setStartingStage(Stage.GO_TO_ORIGIN_SYSTEM);
		addSuccessStages(Stage.COMPLETED);
		addFailureStages(Stage.FAILED);		
		
		beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$nex_remBrawl_missionCompleted", true);
		endTrigger();

		beginStageTrigger(Stage.BATTLE_DEFECTED);
		triggerSetGlobalMemoryValue("$nex_remBrawl_betrayedMidnight", true);
		endTrigger();
		
		beginStageTrigger(Stage.FAILED);
		triggerSetGlobalMemoryValue("$nex_remBrawl_missionFailed", true);
		endTrigger();
		
		// trigger: spawn straggler
		beginWithinHyperspaceRangeTrigger(stragglerOrigin.getPrimaryEntity(), DISTANCE_TO_SPAWN_STRAGGLER, 
				false, Stage.GO_TO_ORIGIN_SYSTEM);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnStragglerFleet();
			}			
		});
		endTrigger();
		
		// trigger: spawn attack fleets and execute attack on delay once player gets close enough to system
		beginWithinHyperspaceRangeTrigger(stagingArea.getHyperspaceAnchor(), 2, false, Stage.FOLLOW_STRAGGLER);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnAttackFleets();
			}
		});
		endTrigger();
		
		// trigger: make Remnant defenders non-hostile
		beginEnteredLocationTrigger(station.getContainingLocation(), Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		triggerRunScriptAfterDelay(0, new Script(){
			@Override
			public void run() {
				setDefendersNonHostile();
			}
		});
		endTrigger();
		
		admiral = OfficerManagerEvent.createOfficer(getHegemony(), 6);
		admiral.setRankId(Ranks.SPACE_ADMIRAL);
		admiral.setPostId(Ranks.POST_FLEET_COMMANDER);
		
		addRelayIfNeeded(station.getStarSystem());
		addRelayIfNeeded(stagingArea);
		
		setRepPersonChangesVeryHigh();
		setRepFactionChangesHigh();
		setCreditReward(CreditReward.VERY_HIGH);
		setCreditReward(this.creditReward * 4);		
		
		return true;
	}
	
	@Override
	public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		Global.getSector().getListenerManager().addListener(this);
	}
	
	protected FactionAPI getHegemony() {
		return Global.getSector().getFaction(Factions.HEGEMONY);
	}
	
	/**
	 * Finds a suitable Remnant station to serve as the mission target.
	 * @return
	 */
	public CampaignFleetAPI pickStation() {
		WeightedRandomPicker<CampaignFleetAPI> picker = new WeightedRandomPicker();
		WeightedRandomPicker<CampaignFleetAPI> pickerFallback = new WeightedRandomPicker();
		WeightedRandomPicker<CampaignFleetAPI> pickerFallback2 = new WeightedRandomPicker();
		Vector2f center = ExerelinNewGameSetup.SECTOR_CENTER;
		for (StarSystemAPI system : Global.getSector().getStarSystems()) 
		{
			if (!system.hasTag(Tags.THEME_REMNANT)) continue;
			boolean highPower = false;
			
			for (CampaignFleetAPI fleet : system.getFleets()) 
			{
				if (!Factions.REMNANTS.equals(fleet.getFaction().getId())) 
					continue;

				if (!fleet.isStationMode()) continue;
				if (fleet.getMemoryWithoutUpdate().getBoolean("$ArtilleryStation")) continue;

				//log.info(String.format("Checking nexus in %s, highPower %s", system.getNameWithLowercaseTypeShort(), highPower));
				float dist = MathUtils.getDistance(fleet.getLocation(), center);
				float weight = 50000/dist;
				highPower = !fleet.getMemoryWithoutUpdate().getBoolean("$damagedStation");

				if (weight > 20) weight = 20;
				if (weight < 0.1f) weight = 0.1f;
				if (highPower && dist <= 20000) picker.add(fleet, weight);
				else if (highPower) pickerFallback.add(fleet, weight);
				else pickerFallback2.add(fleet, weight);

			}
		}
		CampaignFleetAPI base = picker.pick();
		if (base == null) base = pickerFallback.pick();
		if (base == null) base = pickerFallback2.pick();
		return base;
	}
	
	/**
	 * Adds a relay to the system (if possible, and one does not already exist).<br/>
	 * This is so the scout mission makes more sense.
	 * @param system
	 */
	protected void addRelayIfNeeded(StarSystemAPI system) {
		boolean haveRelay = !system.getEntitiesWithTag(Tags.COMM_RELAY).isEmpty();
		if (haveRelay) return;
		
		for (SectorEntityToken sLoc : system.getEntitiesWithTag(Tags.STABLE_LOCATION)) {
			Objectives o = new Objectives(sLoc);
			o.build(Entities.COMM_RELAY_MAKESHIFT, Factions.REMNANTS);
			break;
		}
	}

	protected RemnantStationFleetManager getRemnantStationFleetManager() {
		LocationAPI stationSys = station.getContainingLocation();
		for (EveryFrameScript script : stationSys.getScripts()) {
			if (script instanceof RemnantStationFleetManager) {
				return (RemnantStationFleetManager)script;
			}
		}
		return null;
	}

	// damaged nexus has 2-5 patrols of size 6-12 as per RemnantThemeGenerator, intact has 8-13 patrols of size 8-24
	protected int getNumRemnantPatrols() {
		RemnantStationFleetManager rsfm = getRemnantStationFleetManager();

		if (rsfm == null) return 0;
		return (Integer)ReflectionUtils.getIncludingSuperclasses("maxFleets", rsfm, rsfm.getClass());
	}
	
	/**
	 * Spawns the fleet that player has to follow.
	 */
	protected void spawnStragglerFleet() {
		if (straggler != null) return;
		
		float fp = 120;
		FleetParamsV3 params = new FleetParamsV3(stragglerOrigin, FleetTypes.TASK_FORCE, 
				fp, // combat
				fp * 0.1f,	// freighters
				fp * 0.1f,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	// utility
				0.15f);	// quality mod
		params.officerNumberMult = 1.2f;
		params.averageSMods = 2;
		params.random = this.genRandom;
		params.maxShipSize = 3;	// more consistent top speed
		
		CampaignFleetAPI fleet = spawnFleet(params, stragglerOrigin.getPrimaryEntity());
		attackFleets.add(fleet);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, stragglerOrigin.getPrimaryEntity(), 
				STRAGGLER_WAIT_TIME, new SetStageScript(this, Stage.FOLLOW_STRAGGLER));
		fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, stagingPoint, 1000, 
				StringHelper.getFleetAssignmentString("travellingTo", StringHelper.getString("unknownLocation")), 
				new Script() {
					@Override
					public void run() {
						checkAttack();
					}
				});
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		makeImportant(fleet, "$nex_remBrawl_attackFleet", Stage.GO_TO_ORIGIN_SYSTEM, Stage.FOLLOW_STRAGGLER, Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
		fleet.removeAbility("sun_hd_hyperdrive");
		
		fleet.addEventListener(new BaseFleetEventListener() {
			public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
				log.info("Straggler fleet despawned");
				spawnAttackFleets();
				Global.getSector().addScript(new DelayedActionScript(STRAGGLER_LOST_ATTACK_DELAY) {
					@Override
					public void doAction() {
						log.info("Checking attack after straggler lost delay");
						checkAttack();
					}
				});
			}
		});
		
		//fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		addTugsToFleet(fleet, 2, genRandom);	// tugs instead of navigation to increase sensor profile
		fleet.getMemoryWithoutUpdate().set(AlwaysOnTransponderAI.MEMORY_KEY_ALWAYS_ON, true);
		
		straggler = fleet;
	}
		
	/**
	 * Spawns the Hegemony and allied fleets that will attack the station.
	 */
	protected void spawnAttackFleets() {
		if (spawnedAttackFleets) return;
		
		FactionAPI remnant = Global.getSector().getFaction(Factions.REMNANTS);
		
		/*
			Proposed composition:
			3 big Heg fleets, maybe the latter two are slightly smaller
			1 big LC fleet
			2 semi-big fleets, chance of being LC or Hegemony allies
			switch fleet to Hegemony if the faction isn't allied
		*/
		int fp = 120;
		int hegCount = 2;
		int allyCount = 1;
		int patrolCount = getNumRemnantPatrols();
		float medianPatrolCount = stationIsStrong ? 10.5f : 3.5f;
		float playerPower = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet());

		log.info(String.format("Remnant patrol count is %s, median is %.1f, player power is %.2f", patrolCount, medianPatrolCount, playerPower));

		if (stationIsStrong) {
			hegCount = 3;
			allyCount = 2;
			fp = 150;
		}

		float mult = patrolCount/medianPatrolCount;
		mult = (mult+1)/2;
		mult *= 2;	// compensate for not using market size mult
		log.info("Size multiplier for attack fleets based on defender strength: mult");

		fp = Math.round(fp * mult);
		fp += playerPower/40f;
		for (int i=0; i<hegCount; i++) {
			spawnAttackFleet(Factions.HEGEMONY, fp);
		}
		spawnAttackFleet(Factions.LUDDIC_CHURCH, fp);
		
		fp = stationIsStrong ? 120 : 90;
		fp = Math.round(fp * mult);
		fp += playerPower/60f;
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(this.genRandom);
		factionPicker.add(Factions.LUDDIC_CHURCH, 2);
		factionPicker.add(Factions.HEGEMONY);
		if (AllianceManager.getFactionAlliance(Factions.HEGEMONY) != null) {
			for (String allyId : AllianceManager.getFactionAlliance(Factions.HEGEMONY).getMembersCopy()) 
			{
				if (!remnant.isHostileTo(allyId)) continue;
				factionPicker.add(allyId);
			}
		}
		for (int i=0; i<allyCount; i++) {
			spawnAttackFleet(factionPicker.pick(), fp);
		}
		
		spawnExtraDefenders();
		
		spawnedAttackFleets = true;
	}
	
	/**
	 * Spawns one of the fleets that will attack the station.
	 * @param factionId
	 * @param fp
	 * @return
	 */
	protected CampaignFleetAPI spawnAttackFleet(String factionId, int fp) {
		FactionAPI heg = getHegemony();
		
		FleetParamsV3 params = new FleetParamsV3(stagingPoint.getLocationInHyperspace(),
				factionId,
				null,	// quality override
				FleetTypes.TASK_FORCE,
				fp, // combat
				fp * 0.1f,	// freighters
				fp * 0.1f,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	// utility
				0.15f);	// quality mod
		params.officerNumberMult = 1.2f;
		params.averageSMods = 2;
		params.maxNumShips = (int)(Global.getSettings().getInt("maxShipsInAIFleet") * 1.2f);
		params.ignoreMarketFleetSizeMult = true;
		params.random = this.genRandom;
		
		CampaignFleetAPI fleet = spawnFleet(params, stagingPoint);
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		attackFleets.add(fleet);
		// needs to be ORBIT_AGGRESSIVE to pursue player
		fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, stagingPoint, 99999, StringHelper.getFleetAssignmentString("rendezvous", null));
		fleet.getMemoryWithoutUpdate().set("$genericHail", true);
		
		makeImportant(fleet, "$nex_remBrawl_attackFleet", Stage.FOLLOW_STRAGGLER, Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		
		// don't keep original faction if it risks them becoming hostile to Hegemony shortly
		// or if they're not already hostile to Remnants
		if (heg.isAtBest(factionId, RepLevel.FAVORABLE) 
				|| factionId.equals(Misc.getCommissionFactionId())) 
		{
			fleet.setFaction(Factions.HEGEMONY, false);
		}
		
		// fleets are sus and interrogate player
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
				MemFlags.MEMORY_KEY_PURSUE_PLAYER, "nex_remBrawl_sus", true, 99999);
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
					MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, "nex_remBrawl_sus", true, 99999);
		fleet.getMemoryWithoutUpdate().set("$genericHail_openComms", "Nex_RemBrawlSusHail");
		
		return fleet;
	}
	
	/**
	 * Spawns some extra defenders (currently just two merc fleets) for the station.
	 */
	protected void spawnExtraDefenders() {
		float fp = 80;
		
		for (int i=0; i<2; i++) {
			FleetParamsV3 params = new FleetParamsV3(station.getLocationInHyperspace(),
					Factions.MERCENARY,
					1f,	// quality override
					FleetTypes.MERC_ARMADA,
					fp, // combat
					fp * 0.1f,	// freighters
					fp * 0.1f,		// tankers
					0,		// personnel transports
					0,		// liners
					3,	// utility
					0);	// quality mod
			params.averageSMods = 4;
			params.ignoreMarketFleetSizeMult = true;
			params.random = this.genRandom;
			CampaignFleetAPI fleet = spawnFleet(params, station);
			fleet.setFaction(Factions.INDEPENDENT, true);	// to set captain factions
			fleet.setFaction(Factions.REMNANTS, false);
			fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, station, 3000);
			fleet.getMemoryWithoutUpdate().set("$nex_remBrawl_merc", true);
		}
	}

	/**
	 * Spawns an additional fleet around the nexus if the regular ordos are below median size.
	 */
	protected void spawnExtraDefenders2() {
		checkedExtraDefenders2 = true;
		RemnantStationFleetManager rsfm = getRemnantStationFleetManager();
		if (rsfm == null) return;

		float fp = 0;
		int numFleets = 0;
		List<CampaignFleetAPI> fleets = (List<CampaignFleetAPI>)ReflectionUtils.getIncludingSuperclasses("fleets", rsfm, rsfm.getClass());
		for (CampaignFleetAPI fleet : fleets) {
			numFleets++;
			fp += fleet.getFleetPoints();
		}
		float pointsPerFleet = fp/numFleets/8;
		float medianPoints = stationIsStrong ? (6+12)/2 : (8+24)/2;
		log.info(String.format("Remnant patrol count is %s, points per fleet is %.1f, median points is %.1f", numFleets, pointsPerFleet, medianPoints));
		if (numFleets == 0) {
			//pointsPerFleet = medianPoints;
			//numFleets = getNumRemnantPatrols();
			return;
		}

		float powerDiff = (medianPoints - pointsPerFleet) * numFleets;
		if (powerDiff <= 15) return;

		log.info("Spawning Remnant task force of strength " + powerDiff + " as compensation");
		FleetParamsV3 params = new FleetParamsV3(station.getLocationInHyperspace(),
				Factions.REMNANTS,
				1f,	// quality override
				FleetTypes.TASK_FORCE,
				powerDiff, // combat
				0,	// freighters
				0,		// tankers
				0,		// personnel transports
				0,		// liners
				3,	// utility
				0);	// quality mod
		params.averageSMods = 1;
		params.random = this.genRandom;
		CampaignFleetAPI fleet = spawnFleet(params, station);
		fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, station, 3000);
	}
	
	protected CampaignFleetAPI spawnFleet(FleetParamsV3 params, SectorEntityToken loc) {
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		createdFleets.add(fleet);
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		fleet.getMemoryWithoutUpdate().set("$nex_remBrawl_ref", this);
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		
		loc.getContainingLocation().addEntity(fleet);
		fleet.setLocation(loc.getLocation().x, loc.getLocation().y);
		
		return fleet;
	}
	
	/**
	 * Check if we should order the attack. Called when the straggler arrives, 
	 * or after some days of it being killed without arriving.
	 */
	public void checkAttack() {
		// if player is scouting the system first, or we already ordered the attack, do nothing
		if (launchedAttack) return;
		if (currentStage == Stage.SCOUT) return;
		
		orderAttack();
	}
	
	public void orderAttack() {
		if (launchedAttack) return;
		
		stationToken = station.getContainingLocation().createToken(station.getLocation().x, station.getLocation().y);
		station.getContainingLocation().addEntity(stationToken);
		stationToken.setCircularOrbit(station, 0, 100, 60);
		
		for (CampaignFleetAPI fleet : attackFleets) {
			fleet.clearAssignments();
			boolean sittingOnSystem = currentStage == Stage.SCOUT;
			int time = sittingOnSystem ? 15 : 45;
			FleetAssignment assign = FleetAssignment.ATTACK_LOCATION;
			if (betrayed) assign = FleetAssignment.DELIVER_MARINES;
			else if (sentFalseInfo) {
				//assign = FleetAssignment.RAID_SYSTEM;	// too much difference between playing it "properly" and knowing to talk to the Hegemony?
			}

			fleet.addAssignment(assign, stationToken, time, StringHelper.getFleetAssignmentString("attacking", station.getName()));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationToken, 20,
					StringHelper.getFleetAssignmentString("attacking", station.getName()));
			fleet.addAssignment(FleetAssignment.INTERCEPT, station, 20);
		}

		straggler.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
		straggler.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
		
		//unsetFleetSus();
		setFleetSusExpire();
		
		launchedAttack = true;
	}
	
	public void orderHangAboveSystem() {
		if (orderedHangAboveSystem) return;
		//if (launchedAttack) return;
		if (currentStage == Stage.BATTLE) return;
		
		for (CampaignFleetAPI fleet : attackFleets) {
			fleet.clearAssignments();
			SectorEntityToken dest = station.getStarSystem().getHyperspaceAnchor();
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, dest, 80);
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, dest, 99999);			
		}
		
		orderedHangAboveSystem = true;
		launchedAttack = false;
	}
	
	/**
	 * Check if we've found the staging area, by seeing the point (which is invisible though) 
	 * or any fleets other than the straggler.
	 */
	public void checkStagingAreaFound() {
		if (knowStagingArea) return;
		
		boolean found = false;
		if (!stagingPoint.isDiscoverable()) {
			log.info("Found staging point");
			found = true;
		} else {
			for (CampaignFleetAPI fleet : attackFleets) {
				if (fleet == straggler) continue;
				if (fleet.getContainingLocation() != station.getContainingLocation() && !Misc.isNear(fleet, stagingArea.getLocation())) continue;
				if (fleet.getVisibilityLevelOfPlayerFleet() == VisibilityLevel.COMPOSITION_DETAILS
						|| fleet.getVisibilityLevelToPlayerFleet() == VisibilityLevel.COMPOSITION_DETAILS) {
					found = true;
					log.info("Spotted fleet " + fleet.getName());
					break;
				}
			}
		}
		if (found) {
			spawnAttackFleets();
			knowStagingArea = true;
			stagingPoint.setDiscoverable(false);
			if (currentStage == Stage.FOLLOW_STRAGGLER) {
				setCurrentStage(Stage.GO_TO_TARGET_SYSTEM, null, null);
			}
			Global.getSector().addScript(new DelayedActionScript(STAGING_AREA_FOUND_ATTACK_DELAY) {
				@Override
				public void doAction() {
					checkAttack();
				}
			});
			//Global.getSector().getCampaignUI().addMessage("Found staging area");
		}
	}
	
	/**
	 * Check if we should go to the battle stage, due to the attackers entering the target system.
	 */
	public void checkAdvanceToBattleStage() {
		if (battleInited) return;
		if (currentStage == Stage.BATTLE || currentStage == Stage.BATTLE_DEFECTED) return;
		for (CampaignFleetAPI fleet : attackFleets) {
			if (fleet.getContainingLocation() == station.getContainingLocation()) {
				initBattleStage(null, null);
				break;
			}
			/*
			else if (fleet.getContainingLocation().isHyperspace()) {
				unsetFleetSus(fleet);
			}
			*/
		}
	}
	
	/**
	 * Begin the battle stage.
	 * @param dialog
	 * @param memoryMap
	 */
	public void initBattleStage(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		if (battleInited) return;
		if (!betrayed) {
			makeAttackersHostile();
		}
		setCurrentStage(betrayed ? Stage.BATTLE_DEFECTED : Stage.BATTLE, dialog, memoryMap);
		
		if (!betrayed && knowStagingArea) {
			MilitaryResponseScript.MilitaryResponseParams params = new MilitaryResponseScript.MilitaryResponseParams(CampaignFleetAIAPI.ActionType.HOSTILE, 
					"nex_remBrawl_" + Misc.genUID() + station.getId(), 
					station.getFaction(),
					station,
					1f,
					15f);
			MilitaryResponseScript script = new MilitaryResponseScript(params);
			station.getContainingLocation().addScript(script);
		}
		unsetFleetComms();
		
		battleInited = true;

		LocationAPI loc = station.getContainingLocation();
		if (loc.hasTag("IndEvo_SystemHasArtillery")) {
			Misc.setFlagWithReason(loc.getMemoryWithoutUpdate(), "$IndEvo_SystemDisableWatchtowers", "nex_remnantBrawl", true, BATTLE_MAX_DAYS);
			String msg = String.format(getString("brawl_msg_disableWatchtowers"), loc.getNameWithLowercaseType());
			Global.getSector().getCampaignUI().addMessage(msg, Misc.getHighlightColor(), loc.getNameWithLowercaseType(),
					"", station.getFaction().getBaseUIColor(), Color.WHITE);

		}
	}
	
	/**
	 * After agreeing to scout the target system for the Hegemony admiral.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void gotoScoutStage(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		scoutPoint = spawnMissionNode(new LocData(EntityLocationType.ORBITING_PARAM, 
				station, station.getStarSystem()));
		setEntityMissionRef(scoutPoint, "$nex_remBrawl_ref");
		makeImportant(scoutPoint, "$nex_remBrawl_target", Stage.SCOUT);
		
		setCurrentStage(Stage.SCOUT, dialog, memoryMap);
	}
	
	public void makeAttackersHostile() {
		for (CampaignFleetAPI fleet : attackFleets) {
			Misc.makeLowRepImpact(fleet, "nex_remBrawl");
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "nex_remBrawl", true, 90);
		}
	}

	public void makeAttackersNonHostile() {
		for (CampaignFleetAPI fleet : attackFleets) {
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, "nex_remBrawl_def", true, 90);
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_ALLOW_PLAYER_BATTLE_JOIN_TOFF, "nex_remBrawl_def", true, 90);
		}
	}
	
	/**
	 * Make the attacker fleets no longer pursue the player for the scripted dialog.
	 */
	public void unsetFleetSus() {
		for (CampaignFleetAPI fleet : attackFleets) {
			unsetFleetSus(fleet);
		}
	}
	
	public void unsetFleetSus(CampaignFleetAPI fleet) {
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_PURSUE_PLAYER, "nex_remBrawl_sus", false, 0);
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, "pursue", false, 0);
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, "nex_remBrawl_sus", false, 0);
	}
	
	public void setFleetSusExpire() {
		for (CampaignFleetAPI fleet : attackFleets) {
			setFleetSusExpire(fleet);
		}
	}
	
	public void setFleetSusExpire(CampaignFleetAPI fleet) {
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_PURSUE_PLAYER, "nex_remBrawl_sus", true, SUS_EXPIRE);
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, "pursue", true, SUS_EXPIRE);
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, "nex_remBrawl_sus", true, SUS_EXPIRE);
	}
	
	public void unsetFleetComms() {
		for (CampaignFleetAPI fleet : attackFleets) {
			fleet.getMemoryWithoutUpdate().unset("$genericHail");
			fleet.getMemoryWithoutUpdate().unset("$genericHail_openComms");
		}
	}
	
	public void setDefendersNonHostile() {
		for (CampaignFleetAPI fleet : station.getContainingLocation().getFleets()) {
			if (fleet.getFaction().getId().equals(Factions.REMNANTS)) {
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, "nex_remBrawl_def", true, 90);
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_ALLOW_PLAYER_BATTLE_JOIN_TOFF, "nex_remBrawl_def", true, 90);
			}
		}
	}
	
	public void unsetDefendersNonHostile() {
		for (CampaignFleetAPI fleet : station.getContainingLocation().getFleets()) {
			if (fleet.getFaction().getId().equals(Factions.REMNANTS)) {
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, "nex_remBrawl_def", false, 0);
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_ALLOW_PLAYER_BATTLE_JOIN_TOFF, "nex_remBrawl_def", false, 0);
			}
		}
	}
	
	public void checkAttackFleetDefeated(CampaignFleetAPI fleet) {
		if (!attackFleets.contains(fleet)) return;
		
		int fp = fleet.getFleetPoints();
		if (fp < fleet.getMemoryWithoutUpdate().getFloat("$startingFP") * 0.4f) {
			log.info("Removing attacker fleet " + fleet.getFullName() + " due to excessive damage");
			attackFleets.remove(fleet);
			checkRemnantVictory();
		}
	}
	
	public void checkRemnantVictory() {
		boolean won = spawnedAttackFleets && attackFleets.isEmpty();
		
		if (!won) return;
		if (betrayed) {
			// do nothing, let player finish the job themselves
		} else {
			remnantVictory();
		}
	}
	
	public void remnantVictory() {
		setCurrentStage(Stage.COMPLETED, null, null);
		Global.getSector().getMemoryWithoutUpdate().set("$nex_remBrawl_missionCompleted", true);
		getPerson().setImportance(getPerson().getImportance().next());
		ContactIntel ci = ContactIntel.getContactIntel(getPerson());
		if (ci != null) ci.sendUpdateIfPlayerHasIntel(null, false, false);
	}
	
	public void hegemonyVictory() {
		if (betrayed) {
			PersonAPI midnight = getPerson();
			setPersonOverride(admiral);
			
			setCurrentStage(Stage.COMPLETED, null, null);
			Global.getSector().getMemoryWithoutUpdate().set("$nex_remBrawl_missionCompleted", true);
			NexUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(Factions.REMNANTS), 
					midnight, -0.15f, -0.3f, null, null);
			unsetDefendersNonHostile();
			
			// on betray route, remove Midnight and her intel
			if (midnight.getMarket() != null) 
				midnight.getMarket().getCommDirectory().removePerson(midnight);
			ContactIntel ci = ContactIntel.getContactIntel(midnight);
			if (ci != null) {
				ci.endAfterDelay();
				ci.sendUpdateIfPlayerHasIntel(null, false, false);
			}
			
		} else {
			setCurrentStage(Stage.FAILED, null, null);
		}
	}
	
	protected void cleanup() {
		Global.getSector().getListenerManager().removeListener(this);
		for (final CampaignFleetAPI fleet : createdFleets) {
			if (!fleet.isAlive()) continue;

			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "nex_remBrawl", false, 0);

			if (FleetTypes.MERC_ARMADA.equals(NexUtilsFleet.getFleetType(fleet))) {
				fleet.addScript(new DelayedActionScript(9) {
					@Override
					public void doAction() {
						if (!fleet.isAlive()) return;
						fleet.setFaction(Factions.INDEPENDENT, false);
					}
				});
				Misc.giveStandardReturnToSourceAssignments(fleet, true);
			} else if (Factions.REMNANTS.equals(fleet.getFaction().getId())) {
				RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(fleet, true);
			}
			else {
				Misc.giveStandardReturnToSourceAssignments(fleet, true);
			}
		}
		if (stationToken != null) stationToken.getContainingLocation().removeEntity(stationToken);
		stagingPoint.getContainingLocation().removeEntity(stagingPoint);
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		cleanup();
	}
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
		checkStagingAreaFound();
		checkAdvanceToBattleStage();
		
		if (currentStage == Stage.BATTLE) {
			battleTimer += Misc.getDays(amount);
			if (battleTimer >= BATTLE_MAX_DAYS) {
				remnantVictory();
			}
		}
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_remBrawl_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$nex_remBrawl_stragglerOriginName", stragglerOrigin.getName());
		set("$nex_remBrawl_targetSystemName", station.getStarSystem().getBaseName());
		set("$nex_remBrawl_stagingAreaName", stagingArea.getBaseName());
		
		set("$nex_remBrawl_admiral_heOrShe", admiral.getHeOrShe());
		set("$nex_remBrawl_admiral_HeOrShe", Misc.ucFirst(admiral.getHeOrShe()));
		set("$nex_remBrawl_admiral_himOrHer", admiral.getHimOrHer());
		set("$nex_remBrawl_admiral_HimOrHer", Misc.ucFirst(admiral.getHimOrHer()));
		set("$nex_remBrawl_admiral_hisOrHer", admiral.getHisOrHer());
		set("$nex_remBrawl_admiral_HisOrHer", Misc.ucFirst(admiral.getHisOrHer()));
		set("$nex_remBrawl_admiral", admiral);
		
		set("$nex_remBrawl_stage", getCurrentStage());	// not needed, already set by BaseHubMission?
	}
	
	@Override
	public boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{		
		switch (action) {
			case "showAdmiral":
				dialog.getVisualPanel().showSecondPerson(admiral);
				return true;
			case "violentEncounter":
				orderAttack();
				makeAttackersHostile();
				unsetFleetSus();
				unsetFleetComms();
				return true;
			case "agreeScout":
				gotoScoutStage(dialog, memoryMap);
				orderHangAboveSystem();
				unsetFleetSus();
				unsetFleetComms();
				return true;
			case "scoutTrue":
				betrayed = true;
				orderAttack();
				makeAttackersNonHostile();
				initBattleStage(dialog, memoryMap);
				scoutPoint.getContainingLocation().removeEntity(scoutPoint);
				return true;
			case "scoutFalse":
				sentFalseInfo = true;
				orderAttack();
				initBattleStage(dialog, memoryMap);
				scoutPoint.getContainingLocation().removeEntity(scoutPoint);
				return true;
			default:
				break;
		}
		
		return false;
	}
	
	@Override
	protected void addBulletPointsPre(TooltipMakerAPI info, Color tc, float initPad, ListInfoMode mode) {
		if (mode == ListInfoMode.MESSAGES && currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaUpdate"), initPad, tc, 
					Misc.getHighlightColor(), station.getContainingLocation().getNameWithTypeIfNebula());
		}
	}
	
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		String str = getString("brawl_boilerplateDesc");
		str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
		info.addPara(str, opad);
		
		if (ExerelinModPlugin.isNexDev) {
			info.addPara("[debug] We are now in stage: " + currentStage, opad);
			//info.addPara("[debug] Staging area found: " + knowStagingArea, opad);
			info.addPara("[debug] Station is in: " + station.getContainingLocation().getNameWithLowercaseTypeShort(), 0);
			info.addPara("[debug] Staging area: " + stagingArea.getNameWithLowercaseTypeShort(), 0);
			info.addPara(String.format("[debug] Straggler origin: %s in %s", stragglerOrigin.getName(), stragglerOrigin.getContainingLocation().getName()), 0);
			if (straggler != null && straggler.isAlive()) {
				info.addPara("[debug] Straggler currently in " + straggler.getContainingLocation().getNameWithLowercaseTypeShort(), 0);
			}
		}
		Color col = station.getStarSystem().getStar().getSpec().getIconColor();
		String sysName = station.getContainingLocation().getNameWithLowercaseTypeShort();
		
		if (currentStage == Stage.GO_TO_ORIGIN_SYSTEM ) 
		{
			info.addPara(getString("brawl_startDesc"), opad, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		}
		else if (currentStage == Stage.FOLLOW_STRAGGLER) 
		{
			info.addPara(getString("brawl_followStragglerDesc"), opad, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		}
		else if (currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaDesc"), opad, col, sysName);
		}
		else if (currentStage == Stage.SCOUT) {
			info.addPara(getString("brawl_scoutDesc"), opad, col, sysName);
		}
		else if (currentStage == Stage.BATTLE) {
			info.addPara(getString("brawl_battleDesc" + (knowStagingArea ? "" : "Unknown")), 
					opad, col, sysName);
		}
		else if (currentStage == Stage.BATTLE_DEFECTED) {
			info.addPara(getString("brawl_battleBetrayDesc"), opad, col, sysName);
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color col = station.getStarSystem().getStar().getSpec().getIconColor();
		String sysName = station.getContainingLocation().getNameWithLowercaseTypeShort();
		
		//info.addPara("[debug] Current stage: " + currentStage, tc, pad);
		
		if (currentStage == Stage.GO_TO_ORIGIN_SYSTEM) {
			info.addPara(getString("brawl_startNextStep"), 0, tc, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		}
		else if (currentStage == Stage.FOLLOW_STRAGGLER) {
			info.addPara(getString("brawl_followStragglerNextStep"), tc, 0);
		} 
		else if (currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaNextStep"), 0, tc, col, sysName);
			info.addPara(getString("brawl_foundStagingAreaNextStep2"), tc, 0);
		} 
		else if (currentStage == Stage.SCOUT) {
			info.addPara(getString("brawl_scoutNextStep"), 0, tc, col, sysName);
		}
		else if (currentStage == Stage.BATTLE) {
			info.addPara(getString("brawl_battleNextStep" + (knowStagingArea ? "" : "Unknown")), 0, tc, col, sysName);
		}
		else if (currentStage == Stage.BATTLE_DEFECTED) {
			info.addPara(getString("brawl_battleBetrayNextStep"), 0, tc, col, sysName);
		}
		return false;
	}
	
	@Override
	protected boolean shouldSendUpdateForStage(Object id) {
		return true;	//id != Stage.FOLLOW_STRAGGLER;
	}

	@Override
	public String getBaseName() {
		return getString("brawl_name");
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}	

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map, Object currentStage) {
		if (currentStage == Stage.FOLLOW_STRAGGLER) {
			if (straggler != null && straggler.isVisibleToPlayerFleet()) return straggler;
		}
		if (currentStage == Stage.FOLLOW_STRAGGLER || currentStage == Stage.GO_TO_ORIGIN_SYSTEM) {
			return stragglerOrigin.getPrimaryEntity();
		}
		if (knowStagingArea) {
			return station;
		}
		
		return null;
	}
	
	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		if ((currentStage == Stage.GO_TO_TARGET_SYSTEM || currentStage == Stage.SCOUT) && knowStagingArea) {
			List<ArrowData> result = new ArrayList<>();
			ArrowData arrow = new ArrowData(stagingArea.getHyperspaceAnchor(), station);
			arrow.color = getHegemony().getBaseUIColor();
			arrow.width = 10f;
			result.add(arrow);
			
			return result;
		}
		return null;
	}
	
	
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
		if (attackFleets.contains(fleet)) {
			attackFleets.remove(fleet);
			checkRemnantVictory();
		}
		if (fleet == station) {
			hegemonyVictory();
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		for (CampaignFleetAPI participant : battle.getBothSides()) {
			checkAttackFleetDefeated(participant);
		}
	}

	@Override
	public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
		if (this.checkedExtraDefenders2) return;
		if (curr == station.getContainingLocation()) {
			spawnExtraDefenders2();
		}
	}

	protected static class SetStageScript implements Script {
		
		public Stage stage;
		public RemnantBrawl mission;
		
		public SetStageScript(RemnantBrawl mission, Stage stage) {
			this.mission = mission;
			this.stage = stage;
		}
		
		@Override
		public void run() {
			mission.setCurrentStage(stage, null, null);
		}
	}
}