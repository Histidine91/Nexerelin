package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel.PirateBaseTier;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantStationFleetManager;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.intel.bases.NexPirateBaseIntel;
import exerelin.campaign.intel.bases.Nex_PirateBaseManager;
import exerelin.campaign.intel.missions.remnant.RemnantQuestUtils;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.convertOrbitWithSpin;
import static com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.setEntityLocation;
import static com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantThemeGenerator.addRemnantStationInteractionConfig;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_PlayerOutpost.COST_HEIGHT;

@Log4j
public class BuildStation extends HubMissionWithBarEvent implements FleetEventListener {
	
	public static final boolean DEBUG_MODE = false;
	public static final float FLEET_CHANCE = 0.9f;
	public static final float COMMODITY_PRICE_MULT = 1.1f;
	
	public static final Map<String, Integer> COMMODITIES_REQUIRED = new HashMap<>();
	static {
		COMMODITIES_REQUIRED.put(Commodities.SUPPLIES, 300);
		COMMODITIES_REQUIRED.put(Commodities.HEAVY_MACHINERY, 150);
		COMMODITIES_REQUIRED.put(Commodities.METALS, 1000);
		COMMODITIES_REQUIRED.put(Commodities.RARE_METALS, 200);
	}
	
	public static int MISSION_DAYS = 240;
	
	public static final List<String> ALLOWED_FACTIONS = new ArrayList<String>(Arrays.asList(new String[] {
		Factions.PIRATES, Factions.REMNANTS
	}));
	
	protected String factionId;
	protected LocData loc;
	protected StarSystemAPI system;
	protected SectorEntityToken target;
	
	protected CampaignFleetAPI builderFleet;	
	
	public static enum Stage {
		DELIVER,
		COMPLETED,
		FAILED,
	}
	
	protected Object readResolve() {
		return this;
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		//genRandom = Misc.random;
		
		factionId = getPerson().getFaction().getId();
		if (!ALLOWED_FACTIONS.contains(factionId)) {
			return false;
		}
		
		if (!setPersonMissionRef(getPerson(), "$nex_buildStation_ref")) {
			return false;
		}
			
		requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_UNSAFE, Tags.THEME_CORE, Tags.TRANSIENT, 
				Tags.SYSTEM_CUT_OFF_FROM_HYPER, Tags.THEME_HIDDEN);
		requireSystemHasNumPlanets(1);
		requireSystemNotHasPulsar();
		if (DEBUG_MODE) {
			preferSystemWithinRangeOf(createdAt.getLocationInHyperspace(), 8);
		} else {
			preferSystemOutsideRangeOf(createdAt.getLocationInHyperspace(), 10);
		}		
		if (factionId.equals(Factions.REMNANTS))
			preferSystemTags(ReqMode.ANY, Tags.THEME_REMNANT_DESTROYED);
		search.systemReqs.add(new SystemUninhabitedReq());
		search.systemReqs.add(new CanBuildPirateBaseReq());
		
		system = pickSystem();
		if (system == null) return false;
		
		loc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, system);
		target = spawnMissionNode(loc);
		if (!setEntityMissionRef(target, "$nex_buildStation_ref")) return false;
		//target.addTag(Tags.NON_CLICKABLE);
		makeImportant(target, "$nex_buildStation_target", Stage.DELIVER);
		
		//loc = BaseThemeGenerator.pickCommonLocation(genRandom, system, 200, true, null);
		//if (loc == null) return false;
		
		setStartingStage(Stage.DELIVER);
		setSuccessStage(Stage.COMPLETED);
		setFailureStage(Stage.FAILED);
		
		setTimeLimit(Stage.FAILED, MISSION_DAYS, null);
		
		// fleet intercepts player en route to meeting point
		if (new Random().nextFloat() < FLEET_CHANCE)
		{
			String complFaction = getComplicationFaction();
			boolean isMerc = genRandom.nextFloat() < 0.35f;
			
			if (DEBUG_MODE) {
				complFaction = Factions.HEGEMONY;
				isMerc = false;
			}
			
			beginWithinHyperspaceRangeTrigger(system.getHyperspaceAnchor(), 7, true, Stage.DELIVER);
			FleetQuality qual = FleetQuality.SMOD_1;
			FleetSize size = FleetSize.LARGE;
			String type = FleetTypes.TASK_FORCE;
			if (isMerc) {
				type = FleetTypes.MERC_ARMADA;
				size = FleetSize.MEDIUM;
				qual = FleetQuality.SMOD_2;
			}
			else if (complFaction.equals(Factions.LUDDIC_PATH)) {
				qual = FleetQuality.HIGHER;
				size = FleetSize.VERY_LARGE;
			}
			
			triggerCreateFleet(size, qual, isMerc ? Factions.MERCENARY : complFaction,
					type, system.getHyperspaceAnchor());
			triggerSetFleetOfficers(OfficerNum.MORE, OfficerQuality.HIGHER);
			if (isMerc) {
				triggerSetFleetFaction(Factions.INDEPENDENT);
				triggerSetFleetFlagPermanent("$nex_buildStation_merc");
				triggerMakeNoRepImpact();
			}
			triggerMakeHostileAndAggressive();
			//triggerFleetMakeFaster(true, 2, true);
			triggerSetFleetAlwaysPursue();
			triggerSetFleetMissionRef("$nex_buildStation_ref");
			if (complFaction.equals(Factions.LUDDIC_PATH)) {
				triggerFleetPatherNoDefaultTithe();
			}			
			triggerSetFleetGenericHailPermanent("Nex_BuildStationInterceptHail");
			triggerFleetMakeImportant(null, Stage.DELIVER);
			
			triggerPickLocationTowardsEntity(system.getHyperspaceAnchor(), 60, getUnits(1));
			triggerSpawnFleetAtPickedLocation("$nex_buildStation_interception", null);
			triggerOrderFleetInterceptPlayer();
			
			endTrigger();
		}
		
		// spawn builder fleet on arrival
		{
			beginWithinHyperspaceRangeTrigger(system.getHyperspaceAnchor(), 1f, false, Stage.DELIVER);
			// don't do it using triggers, we need more control
			/*
			triggerCreateFleet(FleetSize.VERY_LARGE, FleetQuality.HIGHER, Factions.REMNANTS, FleetTypes.TASK_FORCE, 
					system.getHyperspaceAnchor());
			triggerMakeNonHostile();
			triggerOrderFleetPatrol(target);
			triggerPickLocationAroundEntity(target, 200f);
			triggerFleetMakeImportant(null, Stage.DELIVER);
			triggerSetRemnantConfig();
			*/
			triggerRunScriptAfterDelay(0, new Script() {
				@Override
				public void run() {
					createBuilderFleet();
				}
			});
			endTrigger();
		}
		
		
		setCreditReward(CreditReward.VERY_HIGH);
		setCreditReward(creditReward + Math.round(getTotalCost() * COMMODITY_PRICE_MULT));
		setRepFactionChangesHigh();
		setRepPersonChangesVeryHigh();
		
		return true;
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		//set("$nex_buildStation_ref2", this);	// "2" in case the base memory value is already set?
		set("$nex_buildStation_factionId", factionId);
		set("$nex_buildStation_reward", Misc.getWithDGS(getCreditsReward()));
		set("$nex_buildStation_system", system.getNameWithLowercaseType());
		set("$nex_buildStation_dist", getDistanceLY(system));
		set("$nex_buildStation_stage", currentStage);
	}
	
	public String getComplicationFaction() {
		if (getPerson().getFaction().getId().equals(Factions.REMNANTS))
			return RemnantQuestUtils.getComplicationFaction(genRandom, false);
		
		List<String> factions = DiplomacyManager.getFactionsAtWarWithFaction(factionId, true, true, false);
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(genRandom);
		picker.addAll(factions);
		return picker.pick();
	}
	
	public int getTotalCost() {
		float cost = getMaterialsCost(Commodities.SUPPLIES)
				+ getMaterialsCost(Commodities.HEAVY_MACHINERY)
				+ getMaterialsCost(Commodities.METALS)
				+ getMaterialsCost(Commodities.RARE_METALS);
		return Math.round(cost);
	}
	
	public float getMaterialsCost(String commodityId) {
		return getCommodityRequired(commodityId) * Global.getSettings().getCommoditySpec(commodityId).getBasePrice();
	}
	
	/**
	 * Returns true if the cargo contains less than half the required amount of at least two commodities.
	 * @param cargo
	 * @param memoryMap
	 * @return
	 */
	protected boolean canDenyCargo(CargoAPI cargo, Map<String, MemoryAPI> memoryMap) {
		int numShortages = 0;
		for (String commodityId : COMMODITIES_REQUIRED.keySet()) 
		{
			if (!haveAtLeastHalfRequirement(commodityId, cargo))
				numShortages++;
		}
		boolean canDeny = numShortages >= 2;
		if (memoryMap != null)
			memoryMap.get(MemKeys.LOCAL).set("$nex_buildStation_canDenyCargo", canDeny);
		
		return canDeny;
	}
	
	protected boolean tryDenyCargo(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {} 
		else {return false;}
		CampaignFleetAPI fleet = (CampaignFleetAPI)(dialog.getInteractionTarget());
		
		CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
		// check cargo
		Map<String, Float> commodities = new HashMap<>();
		for (String commodityId : COMMODITIES_REQUIRED.keySet()) {
			commodities.put(commodityId, playerCargo.getCommodityQuantity(commodityId));
		}
		
		int numShortages = 0;
		for (String commodityId : commodities.keySet()) 
		{
			if (!haveAtLeastHalfRequirement(commodityId, commodities.get(commodityId)))
				numShortages++;
		}
		//boolean successInitial = numShortages >= 2;
		
		addCargoPodContentsToMap(commodities, fleet);
		
		numShortages = 0;
		for (String commodityId : commodities.keySet()) 
		{
			if (!haveAtLeastHalfRequirement(commodityId, commodities.get(commodityId)))
				numShortages++;
		}
		
		boolean success = numShortages >= 2;
		
		if (memoryMap != null)
			memoryMap.get(MemKeys.LOCAL).set("$nex_buildStation_deceiveSuccessAfterCargoPods", success, 0);
		return success;
	}
	
	/**
	 * Does {@code cargo} contain at least half the amount required for the specified commodity?
	 * @param commodityId
	 * @param cargo
	 * @return
	 */
	protected boolean haveAtLeastHalfRequirement(String commodityId, CargoAPI cargo) 
	{
		return haveAtLeastHalfRequirement(commodityId, cargo.getCommodityQuantity(commodityId));
	}
	
	/**
	 * Is {@code amount} at least half the amount required for the specified commodity?
	 * @param commodityId
	 * @param amount
	 * @return
	 */
	protected boolean haveAtLeastHalfRequirement(String commodityId, float amount) 
	{
		return amount >= 0.5f * getCommodityRequired(commodityId);
	}
	
	/**
	 * Adds the contents of the specified cargo pods to the commodities total.
	 * @param commodities
	 * @param pods
	 */
	protected void addCargoPodContentsToMap(Map<String, Float> commodities, CargoAPI pods) 
	{
		for (String commodityId : COMMODITIES_REQUIRED.keySet()) {
			NexUtils.modifyMapEntry(commodities, commodityId, pods.getCommodityQuantity(commodityId));
		}
	}
	
	/**
	 * Gets the cargo of every cargo pod within {@code enemyFleet}'s sensor range, and adds it to the total.
	 * @param commodities
	 * @param enemyFleet
	 */
	protected void addCargoPodContentsToMap(Map<String, Float> commodities, CampaignFleetAPI enemyFleet) {
		for (SectorEntityToken entity : enemyFleet.getContainingLocation().getAllEntities()) {
			if (Entities.CARGO_PODS.equals(entity.getCustomEntityType())) {
				SectorEntityToken.VisibilityLevel level = entity.getVisibilityLevelTo(enemyFleet);
				if (level == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS ||
						level == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS) {
					this.addCargoPodContentsToMap(commodities, entity.getCargo());
				}
			}
		}
	}
	
	protected ResourceCostPanelAPI makeCostPanel(TextPanelAPI text, Color color, Color color2) 
	{
		String key = "costPanelTitle";
        ResourceCostPanelAPI cost = text.addCostPanel(StringHelper.getString("nex_buildMission", key),
			COST_HEIGHT, color, color2);
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		cost.setComWidthOverride(120);
		return cost;
    }
	
	protected boolean addCostEntry(ResourceCostPanelAPI cost, String commodityId)
	{
		int needed = COMMODITIES_REQUIRED.get(commodityId);
		int available = (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(commodityId);
		Color curr = Global.getSector().getPlayerFaction().getColor();
		if (needed > available) {
			curr = Misc.getNegativeHighlightColor();
		}
		cost.addCost(commodityId, "" + needed + " (" + available + ")", curr);
		return available >= needed;
	}
	
	public boolean addCostPanel(InteractionDialogAPI dialog) {
		FactionAPI player = Global.getSector().getPlayerFaction();
		Color color = player.getColor();
		Color darkColor = player.getDarkUIColor();
		ResourceCostPanelAPI panel = makeCostPanel(dialog.getTextPanel(), color, darkColor);
		boolean enough = true;
		enough &= addCostEntry(panel, Commodities.SUPPLIES);
		enough &= addCostEntry(panel, Commodities.HEAVY_MACHINERY);
		enough &= addCostEntry(panel, Commodities.METALS);
		enough &= addCostEntry(panel, Commodities.RARE_METALS);
		panel.update();
		
		return enough;
	}
	
	public float getCost(String commodityId, int count) {
		return Global.getSettings().getCommoditySpec(commodityId).getBasePrice();
	}
	
	public void createBuilderFleet() {
		float randMult = MathUtils.getRandomNumberInRange(0.85f, 1.15f);
		String type = FleetTypes.TASK_FORCE;
		if (Misc.isPirateFaction(Global.getSector().getFaction(factionId)) || NexUtilsFaction.isPirateFaction(factionId)) {
			type = FleetTypes.PATROL_LARGE;
		}			
		
		FleetParamsV3 params = new FleetParamsV3(system.getLocation(),
				factionId,
				null,
				type,
				120 * randMult, // combatPts
				15, // freighterPts
				15, // tankerPts
				0f, // transportPts
				0f, // linerPts
				8, // utilityPts
				0f);
		if (factionId.equals(Factions.REMNANTS)) {
			params.qualityOverride = 0.7f;
		}
		
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		builderFleet = fleet;
		
		system.addEntity(fleet);
		fleet.setLocation(target.getLocation().x, target.getLocation().y);
		Misc.makeImportant(fleet, "$nex_buildStation");
		fleet.getMemoryWithoutUpdate().set("$nex_buildStation_ref", this);
		fleet.getMemoryWithoutUpdate().set("$nex_buildStation_builder", true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 99999);
		
		Global.getSector().getListenerManager().addListener(this);
	}
	
	public void deliver(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		removeCommodities(Global.getSector().getPlayerFleet().getCargo(), true, dialog);
		if (builderFleet != null) {
			addCommodities(builderFleet.getCargo());
			orderBuild();
		}	
		setCurrentStage(Stage.COMPLETED, dialog, memoryMap);
	}
	
	public void build() {
		removeCommodities(builderFleet.getCargo(), false, null);
		
		switch (factionId) {
			case Factions.PIRATES:
				PirateBaseIntel pb = new NexPirateBaseIntel(system, factionId, PirateBaseTier.TIER_2_1MODULE);
				if (!pb.isEnded()) {
					pb.getMarket().getPrimaryEntity().setOrbit(loc.loc.orbit);
					Nex_PirateBaseManager.getInstance().addActive(pb);
				}
					
				break;
			case Factions.REMNANTS:
				RemnantThemeGenerator.RemnantSystemType type = RemnantThemeGenerator.RemnantSystemType.SUPPRESSED;
				String variant = "remnant_station2_Damaged";
				
				final CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(Factions.REMNANTS, FleetTypes.BATTLESTATION, null);
				
				FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
				fleet.getFleetData().addFleetMember(member);
				
				//fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
				fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
				fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
				fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
				fleet.addTag(Tags.NEUTRINO_HIGH);
				
				fleet.setStationMode(true);
				
				addRemnantStationInteractionConfig(fleet);
				
				//fleet.setTransponderOn(true);
				fleet.clearAbilities();
				fleet.addAbility(Abilities.TRANSPONDER);
				fleet.getAbility(Abilities.TRANSPONDER).activate();
				fleet.getDetectedRangeMod().modifyFlat("gen", 1000f);
				
				fleet.setAI(null);
				
				system.addEntity(fleet);
				setEntityLocation(fleet, loc.loc, null);
				convertOrbitWithSpin(fleet, 5f);
				
				boolean damaged = variant.toLowerCase().contains("damaged");
				String coreId = Commodities.ALPHA_CORE;
				if (damaged) {
					// alpha for both types; damaged is already weaker
					//coreId = Commodities.BETA_CORE;
					fleet.getMemoryWithoutUpdate().set("$damagedStation", true);
					fleet.setName(fleet.getName() + " " + StringHelper.getString("exerelin_misc", "damagedStationSuffix"));
				}
					
				AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(coreId);
				PersonAPI commander = plugin.createPerson(coreId, fleet.getFaction().getId(), genRandom);
				
				fleet.setCommander(commander);
				fleet.getFlagship().setCaptain(commander);
				
				if (!damaged) {
					RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(fleet.getFlagship());
					RemnantOfficerGeneratorPlugin.addCommanderSkills(commander, fleet, null, 3, genRandom);
				}
				
				member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
				
				system.addTag(Tags.THEME_INTERESTING);
				system.addTag(Tags.THEME_REMNANT);
				system.addTag(Tags.THEME_UNSAFE);
				system.addTag(Tags.THEME_REMNANT_MAIN);
				system.addTag(type.getTag());
				
				system.addScript(new DelayedActionScript(1) {
					@Override
					public void doAction() {
						RemnantStationFleetManager activeFleets = new RemnantStationFleetManager(
											fleet, 1f, 0, 3, 25f, 6, 12);
						system.addScript(activeFleets);
					}
				});
				
				
				break;
			default:
				break;
		}
	}
	
	public void orderBuild() {
		String actText = StringHelper.getString("exerelin_fleetAssignments", "buildingBase");
		builderFleet.clearAssignments();
		float time = DEBUG_MODE ? 0.1f : 3;
		builderFleet.addAssignment(FleetAssignment.HOLD, target, time, actText, new Script() {
			@Override
			public void run() {
				build();
			}
		});
	}
	
	public void surrenderCargo(InteractionDialogAPI dialog) 
	{
		// is this needed?
		//memoryMap.get(MemKeys.ENTITY).unset("$entity.genericHail_openComms");
		removeCommodities(Global.getSector().getPlayerFleet().getCargo(), true, dialog);
		addCommodities(dialog.getInteractionTarget().getCargo());
	}
	
	public void addCommodities(CargoAPI cargo) {
		for (String commodityId : COMMODITIES_REQUIRED.keySet()) 
		{
			int needed = COMMODITIES_REQUIRED.get(commodityId);
			cargo.addCommodity(commodityId, needed);
		}
	}
	
	public void removeCommodities(CargoAPI cargo, boolean player, InteractionDialogAPI dialog) {
		TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;
		for (String commodityId : COMMODITIES_REQUIRED.keySet()) 
		{			
			int have = Math.round(cargo.getCommodityQuantity(commodityId));
			if (have <= 0) continue;
			
			int needed = COMMODITIES_REQUIRED.get(commodityId);
			if (needed > have) needed = have;
			cargo.removeCommodity(commodityId, needed);
			if (player) AddRemoveCommodity.addCommodityLossText(commodityId, needed, text);
		}
	}
	
	public CargoAPI getCargoForDisplay() {
		CargoAPI cargo = Global.getFactory().createCargo(true);
		addCommodities(cargo);
		return cargo;
	}
	
	@Override
	protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
								Map<String, MemoryAPI> memoryMap) {
		switch (action) {
			case "showCostPanel":
				addCostPanel(dialog);
				return true;
			case "showCostPanelAndCheckCost":
				boolean enough = addCostPanel(dialog);
				memoryMap.get(MemKeys.LOCAL).set("$nex_buildStation_haveEnough", enough, 0);
				return true;
			case "deliver":
				deliver(dialog, memoryMap);
				return true;
			case "surrenderCargo":
				surrenderCargo(dialog);
				return true;
			case "canDenyCargo":
				canDenyCargo(Global.getSector().getPlayerFleet().getCargo(), memoryMap);
				return true;
			case "tryDenyCargo":
				tryDenyCargo(dialog, memoryMap);
				return true;
		}
		return false;
	}
	
	
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) 
	{
		// if that was the builder fleet, and we haven't finished the mission, fail
		if (fleet != builderFleet) return;
		
		if (getCurrentStage() == Stage.DELIVER) {
			setCurrentStage(Stage.FAILED, null, null);
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) 
	{		
		// if that was the builder fleet, and we attacked it, fail
		// fleet seems to always be null? I think it's because listener isn't attached to the builder fleet
		if (!battle.getBothSides().contains(builderFleet)) return;
		if (!battle.isPlayerInvolved()) return;
		if (getCurrentStage() != Stage.DELIVER) return;
		if (!battle.isOnPlayerSide(fleet)) {
			setCurrentStage(Stage.FAILED, null, null);
		}
	}
	
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		if (currentStage == Stage.DELIVER) {
			String str = StringHelper.getString("nex_buildMission", "stageDesc");
			info.addPara(str, opad, system.getStar().getSpec().getIconColor(), system.getNameWithLowercaseType());
			info.showCargo(getCargoForDisplay(), 10, true, opad);
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		if (currentStage == Stage.DELIVER) {
			info.addPara(StringHelper.getString("nex_buildMission", "nextStepText"), pad, tc,
					system.getStar().getSpec().getIconColor(), system.getNameWithLowercaseTypeShort());
			return true;
		}
		return false;
	}
		
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		
		Global.getSector().getListenerManager().removeListener(this);
		
		if (builderFleet != null) {
			// build command should have been given before
			Misc.giveStandardReturnToSourceAssignments(builderFleet, !isSucceeded());
			Misc.makeUnimportant(builderFleet, "$nex_buildStation");
		}
	}
	
	@Override
	public String getBaseName() {
		return StringHelper.getString("nex_buildMission", "missionName");
	}
	
	public static int getCommodityRequired(String commodityId) {
		if (COMMODITIES_REQUIRED.containsKey(commodityId))
			return COMMODITIES_REQUIRED.get(commodityId);
		return 0;
	}
	
	public static class CanBuildPirateBaseReq implements StarSystemRequirement {
		
		@Override
		public boolean systemMatchesRequirement(StarSystemAPI system) {
			return havePirateBaseLoc(system);
		}
		
		/**
		 * Should match the logic in {@code PirateBaseIntel}'s constructor.
		 * @param system
		 * @return
		 */
		public boolean havePirateBaseLoc(StarSystemAPI system) {
			LinkedHashMap<BaseThemeGenerator.LocationType, Float> weights = new LinkedHashMap<BaseThemeGenerator.LocationType, Float>();
			weights.put(BaseThemeGenerator.LocationType.IN_ASTEROID_BELT, 10f);
			weights.put(BaseThemeGenerator.LocationType.IN_ASTEROID_FIELD, 10f);
			weights.put(BaseThemeGenerator.LocationType.IN_RING, 10f);
			weights.put(BaseThemeGenerator.LocationType.IN_SMALL_NEBULA, 10f);
			weights.put(BaseThemeGenerator.LocationType.GAS_GIANT_ORBIT, 10f);
			weights.put(BaseThemeGenerator.LocationType.PLANET_ORBIT, 10f);
			WeightedRandomPicker<EntityLocation> locs = BaseThemeGenerator.getLocations(null, system, null, 100f, weights);
			return !locs.isEmpty();
		}
	}
	
	public static class SystemUninhabitedReq implements StarSystemRequirement {
		public boolean systemMatchesRequirement(StarSystemAPI system) {
			if (!Misc.getMarketsInLocation(system).isEmpty()) return false;
			
			List<CampaignFleetAPI> fleets = system.getFleets();
			for (CampaignFleetAPI fleet : fleets) {
				if (fleet.isStationMode()) return false;
			}
			
			return true;
		}
	}
}
