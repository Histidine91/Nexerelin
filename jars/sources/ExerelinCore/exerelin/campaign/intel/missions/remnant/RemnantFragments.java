package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.missions.BuildStation;
import exerelin.plugins.ExerelinCampaignPlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import second_in_command.SCData;
import second_in_command.SCUtils;
import second_in_command.specs.SCOfficer;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;

@Log4j
public class RemnantFragments extends HubMissionWithBarEvent implements FleetEventListener, DiscoverEntityListener {
	
	public static final int REQUIRED_HACK_SCORE = 5;
	public static final int MAX_SHARDS = 6;
	public static final float MOTHERSHIP_ORBIT_DIST = 12000;

	public static final Map<String, Integer> SKILL_HACK_SCORES = new HashMap<>();
	static {
		SKILL_HACK_SCORES.put(Skills.APT_TECHNOLOGY, 1);
		SKILL_HACK_SCORES.put(Skills.ELECTRONIC_WARFARE, 3);
		SKILL_HACK_SCORES.put(Skills.AUTOMATED_SHIPS, 3);
		SKILL_HACK_SCORES.put(Skills.CYBERNETIC_AUGMENTATION, 3);
		SKILL_HACK_SCORES.put(Skills.GUNNERY_IMPLANTS, 2);

		SKILL_HACK_SCORES.put("sc_technology", 5);
		SKILL_HACK_SCORES.put("sc_automated", 5);
		SKILL_HACK_SCORES.put("sc_dustkeeper", 5);
	}
	
	public enum Stage {
		GO_TO_SYSTEM,
		FOLLOW_MOTHERSHIP,
		BATTLE,
		SALVAGE_MOTHERSHIP,
		RETURN,
		COMPLETED,
		FAILED
	}	
	
	protected StarSystemAPI system;
	protected SectorEntityToken point1, point2;
	protected SectorEntityToken mothership;
	//protected SectorEntityToken derelictShip;
	protected CampaignFleetAPI attacker;
	protected CampaignFleetAPI ally;	
	
	protected PersonAPI engineer;
	protected String aiCore;
	protected boolean distressSent;
	protected float attackerBaseFP;
	protected boolean shardsDeployed;
	protected boolean decidedToFlee;
	protected boolean wonBattle;
	
	// runcode exerelin.campaign.intel.missions.remnant.RemnantFragments.fixDebug()
	public static void fixDebug() {
		RemnantFragments mission = (RemnantFragments)Global.getSector().getMemoryWithoutUpdate().get("$nex_remFragments_ref");
		mission.fixDebug2();
	}
	
	public void fixDebug2() {
		if (attacker != null) {
			attacker.getMemoryWithoutUpdate().set(ExerelinCampaignPlugin.MEM_KEY_BATTLE_PLUGIN, FragmentsBattleCreationPlugin.class.getName());
		}
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		if (!setGlobalReference("$nex_remFragments_ref")) {
			return false;
		}
		
		// pick star system
		requireSystemInterestingAndNotUnsafeOrCore();
		requireSystemNotHasPulsar();
		requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_UNSAFE, Tags.THEME_CORE, Tags.THEME_REMNANT, 
				Tags.TRANSIENT, Tags.SYSTEM_CUT_OFF_FROM_HYPER, Tags.THEME_HIDDEN);
		search.systemReqs.add(new BuildStation.SystemUninhabitedReq());
		preferSystemWithinRangeOf(createdAt.getLocationInHyperspace(), 25);
		preferSystemUnexplored();
		system = pickSystem();
		if (system == null) return false;
		
		// Mothership's original location, before it moved away. Has dead ships and debris.
		LocData loc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, system);
		point1 = spawnMissionNode(loc);
		if (!setEntityMissionRef(point1, "$nex_remFragments_ref")) return false;
		makeImportant(point1, "$nex_remFragments_target", Stage.GO_TO_SYSTEM);
		
		// Mothership's new location.
		// null EntityLocationType keeps LocData.updateLocIfNeeded from trying to regenerate the location
		EntityLocation el = new EntityLocation();
		el.type = LocationType.OUTER_SYSTEM;
		int tries = 0;
		do {
			el.location = MathUtils.getPointOnCircumference(system.getCenter().getLocation(), 
					MOTHERSHIP_ORBIT_DIST, genRandom.nextFloat() * 360f);
			tries++;
		} while (isNearCorona(system, el.location) && tries < 10);
		loc = new LocData(el, system);
		point2 = spawnMissionNode(loc);
		if (!setEntityMissionRef(point2, "$nex_remFragments_ref")) return false;
		makeImportant(point2, "$nex_remFragments_target2", Stage.FOLLOW_MOTHERSHIP);
		point2.setCircularOrbit(system.getCenter(), Misc.getAngleInDegrees(el.location), 
				MOTHERSHIP_ORBIT_DIST, MOTHERSHIP_ORBIT_DIST/24);
		
		//point2.addTag(Tags.NON_CLICKABLE);
		// hide point2 till it's needed
		point2.setDiscoverable(true);
		point2.setSensorProfile(0f);
		
		makeImportant(getPerson(), "$nex_remFragments_return", Stage.RETURN);
		
		setStoryMission();
				
		//makeImportant(station, "$nex_remFragments_target", Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE, Stage.BATTLE_DEFECTED);
		//makeImportant(dissonant, "$nex_remM1_returnHere", Stage.RETURN_CORES);
		
		setStartingStage(Stage.GO_TO_SYSTEM);
		addSuccessStages(Stage.COMPLETED);
		addFailureStages(Stage.FAILED);
		
		// don't use a completion stage trigger, it can't be trusted https://fractalsoftworks.com/forum/index.php?topic=5061.msg392175#msg392175
		/*
		beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$nex_remFragments_missionCompleted", true);
		endTrigger();
		*/
		
		beginStageTrigger(Stage.FAILED);
		triggerSetGlobalMemoryValue("$nex_remFragments_missionFailed", true);
		endTrigger();
		
		// trigger: spawn broken Pather ship and wrecks around the first point
		beginWithinHyperspaceRangeTrigger(system.getHyperspaceAnchor(), 3, false, Stage.GO_TO_SYSTEM);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnPatherDerelict();
				spawnMothership();
			}
		});
		triggerSpawnDebrisField(DEBRIS_MEDIUM, DEBRIS_DENSE, new LocData(point1, false));
		triggerSpawnShipGraveyard(Factions.LUDDIC_PATH, 4, 6, new LocData(point1, false));
		triggerSpawnShipGraveyard(Factions.SCAVENGERS, 2, 3, new LocData(point1, false));
		endTrigger();
				
		// trigger: spawn mothership? should this be in the previous part?
		beginStageTrigger(Stage.FOLLOW_MOTHERSHIP);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				if (mothership == null) spawnMothership();
				point2.setDiscoverable(false);
				point2.setSensorProfile(null);
			}
		});
		endTrigger();
		
		// trigger: spawn fleet
		beginStageTrigger(Stage.BATTLE, Stage.RETURN);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnAttackFleet();
			}
		});
		endTrigger();
		
		engineer = Global.getSector().getFaction(Factions.INDEPENDENT).createRandomPerson(genRandom);
		engineer.setRankId(Ranks.CITIZEN);
		engineer.setPostId(Ranks.POST_SCIENTIST);
				
		setRepPersonChangesVeryHigh();
		setRepFactionChangesHigh();
		setCreditReward(CreditReward.VERY_HIGH);	
		
		return true;
	}
	
	/**
	 * Spawns the Pather or TT fleet that attacks the player.
	 */
	public void spawnAttackFleet() {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		
		String factionId = Factions.LUDDIC_PATH;
		float playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet());
		int capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus());
		log.info("Player strength: " + playerStr);
		int fp = Math.round(playerStr + capBonus)/4;
		if (fp < 60) fp = 60;
		if (fp > 300) fp = 300;
		
		attackerBaseFP = fp;
		
		if (distressSent) {
			factionId = Factions.TRITACHYON;
		} else {
			fp *= 1.3f;
		}
		
		FleetParamsV3 params = new FleetParamsV3(system.getLocation(),
				factionId,
				null,	// quality override
				FleetTypes.TASK_FORCE,
				fp, // combat
				fp * 0.1f,	// freighters
				fp * 0.1f,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	// utility
				0);	// quality mod
		params.random = this.genRandom;
		if (distressSent) {
			params.averageSMods = 2;
			params.qualityMod = 0.3f;
		}
		else {
			params.averageSMods = 0;	// sometimes have one S-mod
			params.qualityMod = 0.45f;
		}
				
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		attacker = fleet;
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		fleet.getCommanderStats().setSkillLevel(Skills.SENSORS, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		fleet.getMemoryWithoutUpdate().set("$genericHail", true);
		fleet.getMemoryWithoutUpdate().set("$genericHail_openComms", "Nex_RemFragmentsHail");
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		makeImportant(fleet, "$nex_remFragments_attacker", Stage.BATTLE);
		if (!decidedToFlee)
			Misc.addDefeatTrigger(fleet, "Nex_RemFragments_AttackFleetDefeated");
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "nex_remFragments", true, 999);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
		
		if (distressSent) {
			Misc.makeLowRepImpact(fleet, "nex_remFragments");
		} else {
			fleet.getMemoryWithoutUpdate().set("$LP_titheAskedFor", true);
		}
				
		float dist = player.getMaxSensorRangeToDetect(fleet);
		if (dist > 2000) dist = 2000;
		system.addEntity(fleet);
		
		Vector2f pos;
		int tries = 0;
		do {
			pos = MathUtils.getPointOnCircumference(player.getLocation(), dist, genRandom.nextFloat() * 360);
			tries++;
		} while (isNearCorona(system, pos) && tries < 10);
		
		fleet.setLocation(pos.x, pos.y);
		
		fleet.getMemoryWithoutUpdate().set(ExerelinCampaignPlugin.MEM_KEY_BATTLE_PLUGIN, FragmentsBattleCreationPlugin.class.getName());
				
		// fleet assignments
		String targetName = StringHelper.getString("yourFleet");
		String actionStr = StringHelper.getFleetAssignmentString("travellingTo", mothership.getName());
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, mothership, 0.5f, actionStr);
		fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, player, 2,
				StringHelper.getFleetAssignmentString("intercepting", targetName));
		
		if (currentStage == Stage.RETURN) {
			// player has scuttled mothership, do nothing except engage the player
		}
		else if (currentStage == Stage.BATTLE)
		{
			// if the attack fleet wins, loot or destroy the mothership
			actionStr = StringHelper.getFleetAssignmentString(distressSent ? "scavenging" : "attacking", mothership.getName());
			fleet.addAssignment(FleetAssignment.DELIVER_CREW, mothership, 100, actionStr);
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, mothership, 0.5f, actionStr, new Script() {
				@Override
				public void run() {
					destroyMothership();
					if (currentStage == Stage.BATTLE || currentStage == Stage.SALVAGE_MOTHERSHIP) {
						setCurrentStage(Stage.FAILED, null, null);
					}
				}
			});
		}
		// finally, return to source
		Misc.giveStandardReturnToSourceAssignments(fleet, false);
		
		// old trigger spawning
		/*
		if (distressSent) {
			triggerCreateFleet(FleetSize.LARGE, FleetQuality.SMOD_2, Factions.TRITACHYON, FleetTypes.TASK_FORCE, point2);
		} else {
			triggerCreateFleet(FleetSize.HUGE, FleetQuality.HIGHER, Factions.LUDDIC_PATH, FleetTypes.TASK_FORCE, point2);
		}
		
		triggerPickLocationAroundEntity(point2, 1500);
		triggerSpawnFleetAtPickedLocation("$nex_remFragments_attackFleet", null);
		triggerMakeFleetIgnoreOtherFleets();
		triggerMakeFleetGoAwayAfterDefeat();
		triggerSetStandardAggroNonPirateFlags();
		triggerFleetMakeFaster(true, 1, false);		
		triggerSetFleetMissionRef("$nex_remFragments_ref"); // so they can be made unimportant
		triggerFleetAddDefeatTrigger("Nex_RemFragments_AttackFleetDefeated");
		triggerFleetPatherNoDefaultTithe();
		triggerSetFleetGenericHailPermanent("Nex_RemFragmentsHail");
		triggerFleetInterceptPlayerOnSight(false, Stage.BATTLE, Stage.RETURN);
		endTrigger();
		*/
	}
	
	/**
	 * Pather derelict in hyperspace above the system.
	 */
	protected void spawnPatherDerelict() {
		List<ShipRolePick> picks = Global.getSector().getFaction(Factions.LUDDIC_PATH).pickShip(ShipRoles.COMBAT_CAPITAL, 
				ShipPickParams.all(), null, genRandom);
		String variantId = picks.get(0).variantId;
		
		DerelictShipData params = new DerelictShipData(
				new ShipRecoverySpecial.PerShipData(variantId, 
						ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);

		SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(Global.getSector().getHyperspace(), 
				Entities.WRECK, Factions.NEUTRAL, params);
		ship.setDiscoverable(true);
		SectorEntityToken orbitFocus = getClosestJumpPointHyperspaceEnd(system);
		if (orbitFocus == null) orbitFocus = system.getHyperspaceAnchor();
		ship.setCircularOrbit(orbitFocus, genRandom.nextFloat() * 360f, 150f, 365f);
		makeImportant(ship, "$nex_remFragments_lpWreck_important", Stage.GO_TO_SYSTEM, Stage.FOLLOW_MOTHERSHIP);
		ship.getMemoryWithoutUpdate().set("$nex_remFragments_lpWreck", true);
	}
	
	public SectorEntityToken getClosestJumpPointHyperspaceEnd(StarSystemAPI system) {
		SectorEntityToken best = null;
		float bestDistSq = 500000000;
		for (SectorEntityToken jump : system.getJumpPoints()) {
			float distSq = MathUtils.getDistanceSquared(jump.getLocation(), point1.getLocation());
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = jump;
			}
		}
		//log.info("Best jump point is " + best);
		
		if (best == null) return null;
		JumpPointAPI bestJ = (JumpPointAPI)best;
		return bestJ.getDestinations().get(0).getDestination();
	}
	
	protected void spawnMothership() {
		mothership = BaseThemeGenerator.addSalvageEntity(system, Entities.DERELICT_MOTHERSHIP, Factions.NEUTRAL);
		mothership.setDiscoverable(true);
		mothership.setLocation(point2.getLocation().x, point2.getLocation().y);
		mothership.setOrbit(point2.getOrbit().makeCopy());
		makeImportant(mothership, "$nex_remFragments_mothership", Stage.GO_TO_SYSTEM, Stage.FOLLOW_MOTHERSHIP, 
				Stage.BATTLE, Stage.SALVAGE_MOTHERSHIP);
		mothership.setInteractionImage("illustrations", "abandoned_station");
		mothership.getMemoryWithoutUpdate().set("$defenderFleetDefeated", true);
		
		addMothershipDrops();
	}
	
	
	protected void addMothershipDrops() {
		DropData d = new SalvageEntityGenDataSpec.DropData();
		d.chances = 1;
		d.group = "rare_tech";
		mothership.addDropRandom(d);
		
		d = new SalvageEntityGenDataSpec.DropData();
		d.chances = 3;
		d.group = "omega_weapons_small";
		mothership.addDropRandom(d);
		
		d = new SalvageEntityGenDataSpec.DropData();
		d.chances = 1;
		d.group = "omega_weapons_medium";
		mothership.addDropRandom(d);
		
		d = new SalvageEntityGenDataSpec.DropData();
		d.chances = 8;
		d.group = "rem_weapons2";
		mothership.addDropRandom(d);
		
		CargoAPI temp = Global.getFactory().createCargo(true);
		temp.addCommodity(Commodities.GAMMA_CORE, 1);
		BaseSalvageSpecial.addExtraSalvage(temp, mothership.getMemoryWithoutUpdate(), -1);
	}
	
	protected void destroyMothership() {
		spawnDebrisField(DEBRIS_MEDIUM, DEBRIS_DENSE, new LocData(mothership, false));
		Misc.fadeAndExpire(mothership);
	}
	
	protected void spawnAndJoinShards(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.OMEGA, getString("fragments_fleetName"), true);
		ally = fleet;
		fleet.setNoFactionInName(true);
		
		int shards = (int)(attackerBaseFP/45);
		if (shards < 2) shards = 2;
		if (shards > MAX_SHARDS) shards = MAX_SHARDS;
		
		for (int i=0; i < shards; i++) {
			boolean left = i < shards/2;	// left shards will spawn on left side
			FleetMemberAPI member = fleet.getFleetData().addFleetMember(left ? "shard_left_Attack": "shard_right_Attack");
			if (aiCore != null) {
				member.setCaptain(Misc.getAICoreOfficerPlugin(aiCore).createPerson(aiCore, Factions.OMEGA, genRandom));
			}
			NexUtilsFleet.setClonedVariant(member, false);
			member.getVariant().addTag(Tags.SHIP_LIMITED_TOOLTIP);
			member.getVariant().addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS);
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		}
		fleet.setFaction(Factions.PLAYER, true);
		
		fleet.getMemoryWithoutUpdate().set("$ignorePlayerCommRequests", true);	// would be awkward for fleet to talk
		
		system.addEntity(fleet);
		fleet.setLocation(player.getLocation().x, player.getLocation().y);
		
		//FleetInteractionDialogPluginImpl fidpi = (FleetInteractionDialogPluginImpl)dialog.getPlugin();
		player.getBattle().join(fleet);
		fleet.getBattle().uncombine();
		fleet.getBattle().genCombined();
		
		Global.getSoundPlayer().playUISound("ui_char_spent_story_point_technology", 1.1f, 1.5f);
		
		shardsDeployed = true;
		Global.getSector().getMemoryWithoutUpdate().set("$nex_remFragments_deployedShards", true);
	}
	
	/**
	 * Called when the attack fleet is defeated in combat, or despawned.
	 * @param dialog
	 * @param memoryMap
	 */
	public void reportFleetDefeated(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		if (wonBattle || decidedToFlee) return;
		wonBattle = true;
		setCurrentStage(Stage.SALVAGE_MOTHERSHIP, dialog, memoryMap);
		mothership.getMemoryWithoutUpdate().set("$nex_remFragments_canSalvage", true);
		
		Misc.giveStandardReturnToSourceAssignments(attacker, true);
		if (ally != null) {
			ally.addAssignment(FleetAssignment.ORBIT_PASSIVE, mothership, 999999);
		}
	}
	
	public int getSkillValueForHack(String skillId) {
		if (SKILL_HACK_SCORES.containsKey(skillId)) {
			return SKILL_HACK_SCORES.get(skillId);
		}
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
		if (spec != null && SKILL_HACK_SCORES.containsKey(spec.getGoverningAptitudeId())) {
			return SKILL_HACK_SCORES.get(spec.getGoverningAptitudeId());
		}

		return 0;
	}
	
	public int getHackScore() {
		int score = 0;
		Set<String> skills = new HashSet<>();
		if (Global.getSettings().getModManager().isModEnabled("second_in_command")) {
			SCData data = SCUtils.getFleetData(Global.getSector().getPlayerFleet());
			for (SCOfficer active : data.getActiveOfficers()) {
				score += getSkillValueForHack(active.getAptitudeId());
			}
		}
		else {
			for (SkillLevelAPI skill : Global.getSector().getCharacterData().getPerson().getStats().getSkillsCopy())
			{
				if (skill.getLevel() <= 0) return 0;
				skills.add(skill.getSkill().getId());
			}
		}
		for (String skillId : skills) score += getSkillValueForHack(skillId);

		return score;
	}
	
	protected boolean haveAICore(String id) {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		if (id == null)
			return cargo.getCommodityQuantity(Commodities.ALPHA_CORE) >= 1 || cargo.getCommodityQuantity(Commodities.BETA_CORE) >= 1;
		else
			return cargo.getCommodityQuantity(id) >= 1;
	}
	
	protected boolean canSpawnShards() {
		if (this.aiCore == null) return false;
		return MathUtils.getDistance(Global.getSector().getPlayerFleet(), mothership) < 100;
	}
	
	@Override
	public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		Global.getSector().getListenerManager().addListener(this);
	}
		
	protected void cleanup() {
		Global.getSector().getListenerManager().removeListener(this);
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		cleanup();
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_remFragments_engineer_name", engineer.getNameString());
		set("$nex_remFragments_engineer_heOrShe", engineer.getHeOrShe());
		set("$nex_remFragments_engineer_HeOrShe", Misc.ucFirst(engineer.getHeOrShe()));
		
		set("$nex_remFragments_targetSystem", system.getNameWithLowercaseType());
		set("$nex_remFragments_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$nex_remFragments_stage", getCurrentStage());
		
		set("$nex_remFragments_giverName", getPerson().getNameString());
		set("$nex_remFragments_giverFirstName", getPerson().getName().getFirst());
		
		set("$nex_remFragments_bribeHighAmt", creditReward * 2f);
		set("$nex_remFragments_bribeHighStr", Misc.getWithDGS(creditReward * 2f));
		set("$nex_remFragments_bribeMediumAmt", creditReward * 1f);
		set("$nex_remFragments_bribeMediumStr", Misc.getWithDGS(creditReward * 1f));
		
		set("$nex_remFragments_shardsDeployed", shardsDeployed);
	}
	
	@Override
	public boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{		
		switch (action) {
			case "pursue_mothership":
				setCurrentStage(Stage.FOLLOW_MOTHERSHIP, dialog, memoryMap);
				Misc.fadeAndExpire(point1);
				return true;
			case "prepEngineer":
				dialog.getInteractionTarget().setActivePerson(engineer);
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				return true;
			case "setDistress":
				distressSent = true;
				return true;
			case "setNoDistress":
				distressSent = false;
				return true;
			case "setHackOptions":
				MemoryAPI mem = memoryMap.get(MemKeys.LOCAL);
				mem.set("$nex_remFragments_canHack", getHackScore() >= REQUIRED_HACK_SCORE, 0);
				dialog.getOptionPanel().setEnabled("nex_remFragments_engineerConvoHackAI", haveAICore(null));
				return true;
			case "setAIOptions":
				dialog.getOptionPanel().setEnabled("nex_remFragments_engineerConvoAlpha", haveAICore(Commodities.ALPHA_CORE));
				dialog.getOptionPanel().setEnabled("nex_remFragments_engineerConvoBeta", haveAICore(Commodities.BETA_CORE));
				return true;
			case "plugAlpha":
				aiCore = Commodities.ALPHA_CORE;
				return true;
			case "plugBeta":
				aiCore = Commodities.BETA_CORE;
				return true;
			case "plugGamma":
				aiCore = Commodities.GAMMA_CORE;
				return true;
			case "battle":
				setCurrentStage(Stage.BATTLE, dialog, memoryMap);
				return true;
			case "evacuate":
				decidedToFlee = true;
				setCurrentStage(Stage.RETURN, dialog, memoryMap);
				destroyMothership();
				return true;
			case "setShardSpawnEnabled":
				boolean inRange = canSpawnShards();
				memoryMap.get(MemKeys.LOCAL).set("$nex_remFragments_inSpawnRange", inRange, 0);
				//dialog.getOptionPanel().setEnabled("nex_remFragments_spawnShards", inRange);
				return true;
			case "acceptedBribe":
				setCurrentStage(Stage.FAILED, dialog, memoryMap);
				if (attacker != null) {
					attacker.removeFirstAssignmentIfItIs(FleetAssignment.GO_TO_LOCATION);
					attacker.removeFirstAssignmentIfItIs(FleetAssignment.INTERCEPT);
				}
				return true;
			case "enableSalvage":
				reportFleetDefeated(dialog, memoryMap);
				return true;
			case "spawnAndJoinShards":
				spawnAndJoinShards(dialog, memoryMap);
				return true;
			case "victory":
				reportFleetDefeated(dialog, memoryMap);
				return true;
			case "despawnShards":
				if (ally != null) {
					RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(ally, false);
					ally.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
				}
				setCurrentStage(Stage.RETURN, dialog, memoryMap);
				return true;
			case "complete":
				setCurrentStage(Stage.COMPLETED, dialog, memoryMap);
				Global.getSector().getMemoryWithoutUpdate().set("$nex_remFragments_missionCompleted", true);
				return true;
			default:
				break;
		}
		
		return false;
	}
		
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		String sysName = system.getNameWithLowercaseType();
		
		String str = getString("fragments_boilerplateDesc");
		str = StringHelper.substituteToken(str, "$name", getPerson().getName().getLast());
		info.addPara(str, opad);
		
		if (ExerelinModPlugin.isNexDev) {
			//info.addPara("[debug] We are now in stage: " + currentStage, opad);
		}
		
		if (currentStage == Stage.GO_TO_SYSTEM) 
		{
			info.addPara(getString("fragments_startDesc"), opad, h, sysName);
		} 
		else if (currentStage == Stage.FOLLOW_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_pursueDesc"), opad, h, sysName);
		} 
		else if (currentStage == Stage.BATTLE) 
		{
			info.addPara(getString("fragments_fightDesc"), opad);
		}
		else if (currentStage == Stage.SALVAGE_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_salvageDesc"), opad);
		}
		else if (currentStage == Stage.RETURN) 
		{
			str = getString("fragments_returnDesc");
			str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
			info.addPara(str, opad, h, getPerson().getMarket().getName());
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		//info.addPara("[debug] Current stage: " + currentStage, tc, pad);
		String sysName = system.getNameWithLowercaseTypeShort();
		
		if (currentStage == Stage.GO_TO_SYSTEM || currentStage == Stage.FOLLOW_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_startNextStep"), pad, tc, Misc.getHighlightColor(), sysName);
			return true;
		}
		else if (currentStage == Stage.BATTLE) 
		{
			info.addPara(getString("fragments_fightNextStep"), tc, pad);
			return true;
		}
		else if (currentStage == Stage.SALVAGE_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_salvageNextStep"), tc, pad);
			return true;
		}
		else if (currentStage == Stage.RETURN) 
		{
			String str = getString("fragments_returnNextStep");
			str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
			info.addPara(str, pad, tc, Misc.getHighlightColor(), getPerson().getMarket().getName());
			return true;
		}
		
		return false;
	}

	@Override
	public String getBaseName() {
		return getString("fragments_name");
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}
		
	
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
		if (fleet == attacker) {
			reportFleetDefeated(null, null);
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		
	}
		
	@Override
	public void reportEntityDiscovered(SectorEntityToken entity) {
		if (entity == mothership) {
			system.removeEntity(point2);
		}
	}
}





