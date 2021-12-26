package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import java.awt.Color;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseFleetEventListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinNewGameSetup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class RemnantBrawl extends HubMissionWithBarEvent implements FleetEventListener {
	
	public static Logger log = Global.getLogger(RemnantBrawl.class);
	
	public static final float STRAGGLER_LOST_ATTACK_DELAY = 15;
	public static final float STAGING_AREA_FOUND_ATTACK_DELAY = 1.75f;

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
	
	@Deprecated protected PersonAPI dissonant;
	protected MarketAPI stragglerOrigin;
	protected StarSystemAPI stagingArea;
	protected SectorEntityToken stagingPoint;
	protected CampaignFleetAPI station;
	protected CampaignFleetAPI straggler;
	protected boolean betrayed;
	
	protected Set<CampaignFleetAPI> createdFleets = new HashSet<>();
	protected Set<CampaignFleetAPI> attackFleets = new HashSet<>();
	
	protected boolean spawnedStraggler;
	protected boolean spawnedAttackFleets;
	protected boolean spawnedExtraDefenders;
	protected boolean launchedAttack;
	protected boolean knowStagingArea;
	protected boolean battleInited;
	
	// runcode exerelin.campaign.intel.missions.remnant.RemnantBrawl.fixDebug()
	public static void fixDebug() {
		RemnantBrawl mission = (RemnantBrawl)Global.getSector().getMemoryWithoutUpdate().get("$nex_remBrawl_ref");
		log.info(mission.battleInited + " lol");
	}
	
	// TODO:
	// fail mission if station despawns (or succeed if we're on the betrayal route)
	// make Remnants in system non-hostile if player is helping defend
	
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
		
		requireMarketFaction(Factions.HEGEMONY);
		requireMarketNotHidden();
		requireMarketNotInHyperspace();
		preferMarketSizeAtLeast(5);
		preferMarketIsMilitary();
		stragglerOrigin = pickMarket();
		if (stragglerOrigin == null) return false;
		
		//requireSystemHasNumPlanets(1);
		requireSystemNotHasPulsar();
		requireSystemInterestingAndNotUnsafeOrCore();
		requireSystemWithinRangeOf(station.getContainingLocation().getLocation(), 12);
		preferSystemOutsideRangeOf(station.getContainingLocation().getLocation(), 7);
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
		
		beginStageTrigger(Stage.FAILED);
		triggerSetGlobalMemoryValue("$nex_remBrawl_missionFailed", true);
		endTrigger();
		
		// trigger: spawn straggler
		beginWithinHyperspaceRangeTrigger(stragglerOrigin.getPrimaryEntity(), 4, false, Stage.GO_TO_ORIGIN_SYSTEM);
		this.triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnStragglerFleet();
			}			
		});
		endTrigger();
		
		// trigger: spawn attack fleets and execute attack on delay once player gets close enough to system
		beginWithinHyperspaceRangeTrigger(stagingArea.getHyperspaceAnchor(), 2, false, Stage.FOLLOW_STRAGGLER);
		this.triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnAttackFleets();
			}
		});
		endTrigger();
		
		setRepPersonChangesVeryHigh();
		setRepFactionChangesHigh();
		setCreditReward(CreditReward.VERY_HIGH);
		setCreditReward(this.creditReward * 3);
		
		return true;
	}
	
	@Override
	public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		Global.getSector().getListenerManager().addListener(this);
	}
	
	public CampaignFleetAPI pickStation() {
		WeightedRandomPicker<CampaignFleetAPI> picker = new WeightedRandomPicker();
		WeightedRandomPicker<CampaignFleetAPI> pickerFallback = new WeightedRandomPicker();
		Vector2f center = ExerelinNewGameSetup.SECTOR_CENTER;
		for (StarSystemAPI system : Global.getSector().getStarSystems()) 
		{
			if (!system.hasTag(Tags.THEME_REMNANT)) continue;
			boolean highPower = system.hasTag(Tags.THEME_REMNANT_RESURGENT);
			
			for (CampaignFleetAPI fleet : system.getFleets()) 
			{
				if (fleet.isStationMode()) 
				{
					float dist = MathUtils.getDistance(fleet.getLocation(), center);
					float weight = 50000/dist;
					if (weight > 20) weight = 20;
					if (weight < 0.1f) weight = 0.1f;
					if (highPower && dist <= 20000) picker.add(fleet, weight);
					else pickerFallback.add(fleet, weight);
				}
			}
		}
		CampaignFleetAPI base = picker.pick();
		if (base == null) base = pickerFallback.pick();
		return base;
	}
	
	public void spawnStragglerFleet() {
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
		
		CampaignFleetAPI fleet = spawnFleet(params, stragglerOrigin.getPrimaryEntity());
		attackFleets.add(fleet);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, stragglerOrigin.getPrimaryEntity(), 3);
		fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, stagingPoint, 1000, 
				StringHelper.getFleetAssignmentString("travellingTo", StringHelper.getString("unknownLocation")), 
				new Script() {
					@Override
					public void run() {
						checkAttack();
					}
				});
		fleet.getMemoryWithoutUpdate().set("$nex_remBrawl_ref", this);
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		makeImportant(fleet, "$nex_remBrawl_attackFleet", Stage.FOLLOW_STRAGGLER, Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
		
		fleet.addEventListener(new BaseFleetEventListener() {
			public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
				spawnAttackFleets();
				Global.getSector().addScript(new DelayedActionScript(STRAGGLER_LOST_ATTACK_DELAY) {
					@Override
					public void doAction() {
						checkAttack();
					}
				});
			}
		});
		
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		setCurrentStage(Stage.FOLLOW_STRAGGLER, null, null);
		
		straggler = fleet;
	}
		
	public void spawnAttackFleets() {
		if (spawnedAttackFleets) return;
		
		/*
			Proposed composition:
			3 big Heg fleets, maybe the latter two are slightly smaller
			1 big LC fleet
			2 semi-big fleets, chance of being LC or Hegemony allies
			switch fleet to Hegemony if the faction isn't allied
		*/
		int fp = 150;
		for (int i=0; i<3; i++) {
			spawnAttackFleet(Factions.HEGEMONY, fp);
		}
		spawnAttackFleet(Factions.LUDDIC_CHURCH, fp);
		
		fp = 120;
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(this.genRandom);
		factionPicker.add(Factions.LUDDIC_CHURCH, 2);
		factionPicker.add(Factions.HEGEMONY);
		if (AllianceManager.getFactionAlliance(Factions.HEGEMONY) != null) {
			factionPicker.addAll(AllianceManager.getFactionAlliance(Factions.HEGEMONY).getMembersCopy());
		}
		for (int i=0; i<2; i++) {
			spawnAttackFleet(factionPicker.pick(), fp);
		}
		
		spawnedAttackFleets = true;
	}
	
	public CampaignFleetAPI spawnAttackFleet(String factionId, int fp) {
		FactionAPI heg = Global.getSector().getFaction(Factions.HEGEMONY);
		
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
		params.random = this.genRandom;
		
		CampaignFleetAPI fleet = spawnFleet(params, stagingPoint);
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		attackFleets.add(fleet);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, stagingPoint, 99999, StringHelper.getFleetAssignmentString("rendezvous", null));
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		fleet.getMemoryWithoutUpdate().set("$genericHail", true);
		fleet.getMemoryWithoutUpdate().set("$genericHail_openComms", "Nex_RemBrawlSusHail");
		fleet.getMemoryWithoutUpdate().set("$nex_remBrawl_ref", this);
		makeImportant(fleet, "$nex_remBrawl_attackFleet", Stage.FOLLOW_STRAGGLER, Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		
		
		if (heg.isAtBest(factionId, RepLevel.FAVORABLE) || factionId.equals(Misc.getCommissionFactionId())) 
		{
			fleet.setFaction(Factions.HEGEMONY, true);
		}
		// TODO: fleets are sus and interrogate player
		//Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_PURSUE_PLAYER, "$nex_remBrawl_sus", true, 99999);
		
		return fleet;
	}
	
	public void spawnExtraDefenders() {
		if (spawnedExtraDefenders) return;
		float fp = 45;
		
		for (int i=0; i<=2; i++) {
			FleetParamsV3 params = new FleetParamsV3(station.getLocationInHyperspace(),
					Factions.MERCENARY,
					1f,
					FleetTypes.MERC_ARMADA,
					fp, // combat
					fp * 0.1f,	// freighters
					fp * 0.1f,		// tankers
					0,		// personnel transports
					0,		// liners
					3,	// utility
					0);	// quality mod
			params.averageSMods = 4;
			params.random = this.genRandom;
			CampaignFleetAPI fleet = spawnFleet(params, station);
			fleet.setFaction(Factions.REMNANTS, true);
			fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, station, 3000);
		}
		
		spawnedExtraDefenders = true;
	}
	
	public CampaignFleetAPI spawnFleet(FleetParamsV3 params, SectorEntityToken loc) {
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		createdFleets.add(fleet);
		loc.getContainingLocation().addEntity(fleet);
		loc.setLocation(loc.getLocation().x, loc.getLocation().y);
		
		return fleet;
	}
	
	public void checkAttack() {
		// if player is scouting the system first, or we already ordered the attack, do nothing
		if (launchedAttack) return;
		if (currentStage == Stage.SCOUT) return;
		
		orderAttack();
	}
	
	public void orderAttack() {
		if (launchedAttack) return;
		
		SectorEntityToken targetToken = station.getContainingLocation().createToken(station.getLocation().x, station.getLocation().y);
		
		for (CampaignFleetAPI fleet : attackFleets) {
			fleet.clearAssignments();
			fleet.addAssignment(FleetAssignment.DELIVER_MARINES, targetToken, 60, 
					StringHelper.getFleetAssignmentString("attacking", station.getName()));
			fleet.addAssignment(FleetAssignment.INTERCEPT, station, 20);
			
			// unset player sus flag, focus on our mission
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_PURSUE_PLAYER, "$nex_remBrawl_sus", false, 0);
			fleet.getMemoryWithoutUpdate().unset("$genericHail");
			fleet.getMemoryWithoutUpdate().unset("$genericHail_openComms");
		}
		straggler.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
		straggler.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
		
		spawnExtraDefenders();
		
		launchedAttack = true;
	}
	
	public void checkStagingAreaFound() {
		if (knowStagingArea) return;
		
		boolean found = false;
		if (!stagingPoint.isDiscoverable()) {
			log.info("Found statging point");
			found = true;
		} else {
			for (CampaignFleetAPI fleet : attackFleets) {
				if (fleet == straggler) continue;
				if (fleet.getContainingLocation() != station.getContainingLocation() && !Misc.isNear(fleet, stagingArea.getLocation())) continue;
				if (fleet.isVisibleToPlayerFleet()) {
					found = true;
					log.info("Spotted fleet " + fleet.getName());
					break;
				}
			}
		}
		if (found) {
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
			Global.getSector().getCampaignUI().addMessage("Found staging area");
		}
	}
	
	public void checkAdvanceToBattleStage() {
		if (battleInited) return;
		if (currentStage == Stage.BATTLE || currentStage == Stage.BATTLE_DEFECTED) return;
		for (CampaignFleetAPI fleet : attackFleets) {
			if (fleet.getContainingLocation() == station.getContainingLocation()) {
				initBattleStage();
				break;
			}
		}
	}
	
	public void initBattleStage() {
		if (battleInited) return;
		for (CampaignFleetAPI fleet : attackFleets) {
			if (!betrayed) {
				Misc.makeLowRepImpact(fleet, "$nex_remBrawl");
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "$nex_remBrawl", true, 90);
			}
		}
		setCurrentStage(betrayed ? Stage.BATTLE_DEFECTED : Stage.BATTLE, null, null);
			
		battleInited = true;
	}
	
	public void checkAttackFleetDefeated(CampaignFleetAPI fleet) {
		if (!attackFleets.contains(fleet)) return;
		
		// TODO check FP
		int fp = fleet.getFleetPoints();
		if (fp < 0) {
			attackFleets.remove(fleet);
			checkRemnantVictory();
		}
	}
	
	public void checkRemnantVictory() {
		if (currentStage == Stage.BATTLE && attackFleets.isEmpty()) {
			setCurrentStage(Stage.COMPLETED, null, null);
			getPerson().setImportance(getPerson().getImportance().next());
			ContactIntel ci = ContactIntel.getContactIntel(getPerson());
			if (ci != null) ci.sendUpdateIfPlayerHasIntel(null, false, false);
		}
	}
	
	public void hegemonyVictory() {
		if (betrayed) {
			setCurrentStage(Stage.COMPLETED, null, null);
			// TODO: on betray route, remove Midnight and her intel
		} else {
			setCurrentStage(Stage.FAILED, null, null);
		}
	}
	
	public void cleanup() {
		Global.getSector().getListenerManager().removeListener(this);
		for (CampaignFleetAPI fleet : createdFleets) {
			if (!fleet.isAlive()) continue;
			Misc.giveStandardReturnToSourceAssignments(fleet, true);
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "$nex_remBrawl", false, 9999);
		}
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
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_remBrawl_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$nex_remBrawl_stragglerOriginName", stragglerOrigin.getName());
		set("$nex_remBrawl_targetSystemName", station.getStarSystem().getNameWithLowercaseTypeShort());
		//set("$nex_remBrawl_dist", getDistanceLY(station));
		
		set("$nex_remBrawl_stage", getCurrentStage());
	}
	
	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		
		if (null != action) switch (action) {
			
			default:
				break;
		}
		
		return super.callEvent(ruleId, dialog, params, memoryMap);
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
		Color h = Misc.getHighlightColor();
		
		String str = getString("brawl_boilerplateDesc");
		str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
		info.addPara(str, opad);
		
		info.addPara("[debug] We are now in stage: " + currentStage, opad);
		info.addPara("[debug] Staging area found: " + knowStagingArea, opad);
		info.addPara("[debug] Station is in: " + station.getContainingLocation().getNameWithLowercaseTypeShort(), opad);
		
		if (currentStage == Stage.GO_TO_ORIGIN_SYSTEM || currentStage == Stage.FOLLOW_STRAGGLER) 
		{
			info.addPara(getString("brawl_startDesc"), opad, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		}
		else if (currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaDesc"), opad, 
					station.getStarSystem().getStar().getSpec().getIconColor(), station.getContainingLocation().getNameWithLowercaseTypeShort());
		}
		else if (currentStage == Stage.BATTLE) {
			info.addPara(getString("brawl_battleDesc" + (knowStagingArea ? "" : "Unknown")), opad, 
					station.getStarSystem().getStar().getSpec().getIconColor(), station.getContainingLocation().getNameWithLowercaseTypeShort());
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color h = Misc.getHighlightColor();
		
		info.addPara("[debug] Current stage: " + currentStage, tc, pad);
		
		if (currentStage == Stage.GO_TO_ORIGIN_SYSTEM || currentStage == Stage.FOLLOW_STRAGGLER) {
			info.addPara(getString("brawl_startNextStep"), 0, tc, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		} 
		else if (currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaNextStep"), 0, tc, 
					station.getStarSystem().getStar().getSpec().getIconColor(), station.getContainingLocation().getNameWithLowercaseTypeShort());
			info.addPara(getString("brawl_foundStagingAreaNextStep2"), tc, 0);
		}
		else if (currentStage == Stage.BATTLE) {
			info.addPara(getString("brawl_battleDesc" + (knowStagingArea ? "" : "Unknown")), 0, 
					station.getStarSystem().getStar().getSpec().getIconColor(), station.getContainingLocation().getNameWithLowercaseTypeShort());
		}
		return false;
	}
	
	@Override
	protected boolean shouldSendUpdateForStage(Object id) {
		return id != Stage.FOLLOW_STRAGGLER;
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
		if (currentStage == Stage.GO_TO_TARGET_SYSTEM && knowStagingArea) {
			List<ArrowData> result = new ArrayList<>();
			ArrowData arrow = new ArrowData(stagingArea.getHyperspaceAnchor(), station);
			arrow.color = Global.getSector().getFaction(Factions.HEGEMONY).getBaseUIColor();
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
}





