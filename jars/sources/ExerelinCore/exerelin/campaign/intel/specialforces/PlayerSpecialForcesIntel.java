package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_SpecialForcesConfig;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.hullmods.Automated;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.Setter;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import second_in_command.SCData;
import second_in_command.SCUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.intel.fleets.NexAssembleStage.getAdjustedStrength;

public class PlayerSpecialForcesIntel extends SpecialForcesIntel implements EconomyTickListener, ModPluginEventListener {
	
	// for now
	// actually never turn this on? when we invoke combat costs this becomes a mess overall
	// on the other hand, should combat actually be free?
	// can't be helped, it _is_ free if far-autoresolved
	public static final boolean AI_MODE = true;
	public static final boolean PRINT_VARIANT_WARNING = true;
	public static final String MEM_KEY_OVER_OFFICER_LIMIT_WARN = "$nex_overOfficerLimitWarn";
	
	public static final float CREW_SALARY_MULT = 1;	// should not be high since the utility of assets in a PSF fleet is much lower than in the player fleet
	public static final float SUPPLY_COST_MULT = 1;	// ditto, even though we're getting free combat out of the deal
	public static final float OFFICER_SALARY_MULT = 1f;
	public static final float FUEL_COST_MULT = 1f;
	
	public static final Object DESTROYED_UPDATE = new Object();	
	protected static final Object BUTTON_COMMAND = new Object();
	protected static final Object BUTTON_DISBAND = new Object();
	protected static final Object BUTTON_RECREATE = new Object();
	protected static final Object BUTTON_RECREATE_ALL = new Object();
	protected static final Object BUTTON_INDEPENDENT_MODE = new Object();
	protected static final Object BUTTON_UNSTICK = new Object();
	
	@Setter protected CampaignFleetAPI tempFleet;
	protected CampaignFleetAPI fleet;
	@Getter protected String id = UUID.randomUUID().toString();

	protected transient IntervalUtil variantCheckInterval = new IntervalUtil(1, 1);
	
	/**
	 * Stores all members added to the fleet (and not subsequently removed), dead or alive. For safety/debugging purposes.
	 */
	@Getter protected Set<FleetMemberAPI> membersBackup = new LinkedHashSet<>();
	@Getter protected Map<FleetMemberAPI, ShipVariantAPI> storedVariants = new LinkedHashMap<>();
	@Getter protected Set<FleetMemberAPI> deadMembers = new LinkedHashSet<>();
	
	@Getter	protected boolean independentMode = true;
	protected boolean isAlive;
	protected boolean waitingForSpawn;
	protected float fuelUsedLastInterval;
	protected int autoShipDP;
	protected transient Vector2f lastPos;
	
	protected Object readResolve() {
		// no, bad, don't do anything in readResolve that involves anything else
		//addListenerIfNeeded();
		variantCheckInterval = new IntervalUtil(1, 1);
		if (id == null) id = UUID.randomUUID().toString();
		return this;
	}
	
	public PlayerSpecialForcesIntel(MarketAPI origin, FactionAPI faction) {
		super(origin, faction, 0);
		isPlayer = true;
	}
	
	@Override
	public void init(PersonAPI commander) {
		super.init(commander);
		waitingForSpawn = true;
		addListenerIfNeeded();
	}

	public void addListenerIfNeeded() {
		if (isEnding() || isEnded()) return;
		if (!Global.getSector().getListenerManager().hasListener(this)) {
			Global.getSector().getListenerManager().addListener(this);
		}
	}
			
	public void setFlagship(FleetMemberAPI member) {
		flagship = member;
		if (fleet != null && member != null) {
			fleet.getFleetData().setFlagship(flagship);
		}
	}
	
	@Override
	public void setCommander(PersonAPI commander) {
		super.setCommander(commander);
	}
		
	public CampaignFleetAPI createTempFleet() {
		tempFleet = FleetFactoryV3.createEmptyFleet(faction.getId(), FLEET_TYPE, origin);
		tempFleet.getMemoryWithoutUpdate().set("$nex_psf_isTempFleet", true);
		tempFleet.getMemoryWithoutUpdate().set("$nex_sfIntel", this);
		return tempFleet;
	}

	public void notifyShipsAdded(Collection<FleetMemberAPI> members) {
		for (FleetMemberAPI member : members) {
			notifyShipAdded(member);
		}
		if (route != null && fleet != null) route.getExtra().fp = (float)fleet.getFleetPoints();
	}

	protected void notifyShipAdded(FleetMemberAPI member) {
		membersBackup.add(member);
		// weird hax because the variants stop being stock once they're used in the PSF, perhaps due to inflater
		// update: they will stay stock if they haven't been modified from the said stock variant
		//log.info(String.format("Adding ship %s (%s), variant source %s", member.getShipName(),
		//		member.getHullSpec().getHullNameWithDashClass(), member.getVariant().getSource()));
		if (member.getVariant().getSource() == VariantSource.STOCK) {
			NexUtilsFleet.setClonedVariant(member, true);
			//printVariant(member, member.getVariant());
		} else if (member.getVariant().getOriginalVariant() != null) {
			NexUtilsFleet.setClonedVariant(member, true);
			//log.info("Forcing null variant");
		}
		storedVariants.put(member, member.getVariant());
	}

	public void notifyShipsRemoved(Collection<FleetMemberAPI> members) {
		for (FleetMemberAPI member : members) {
			notifyShipRemoved(member);
		}
		if (route != null && fleet != null) route.getExtra().fp = (float)fleet.getFleetPoints();
	}

	protected void notifyShipRemoved(FleetMemberAPI member) {
		membersBackup.remove(member);
		storedVariants.remove(member);
	}
	
	public CampaignFleetAPI createFleet(RouteData thisRoute) 
	{
		if (tempFleet == null) return null;
		
		CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(faction.getId(), FLEET_TYPE, origin);
		if (fleet == null) return null;
		
		// these values should already have been set during PSF creation
		//commander = tempFleet.getCommander();
		//flagship = tempFleet.getFlagship();
		
		fleet.setCommander(commander);
		fleet.getFleetData().setFlagship(flagship);
		//flagship.setCaptain(commander);	// safety in case it's lost?
		
		for (OfficerDataAPI od : tempFleet.getFleetData().getOfficersCopy()) {
			fleet.getFleetData().addOfficer(od);
		}
		for (FleetMemberAPI live : tempFleet.getFleetData().getMembersListCopy()) {
			fleet.getFleetData().addFleetMember(live);
			if (live.getVariant().getSource() != VariantSource.REFIT) {
				NexUtilsFleet.setClonedVariant(live, true);
				live.getVariant().addTag(Tags.TAG_NO_AUTOFIT_UNLESS_PLAYER);
			}
		}
		
		fleet.setInflated(true);
		fleet.setInflater(null);
		
		commander.setRankId(Ranks.SPACE_CAPTAIN);
		
		fleet.setNoAutoDespawn(true);
		
		fleet.setFaction(faction.getId(), false);
				
		syncFleet(fleet);
		this.startingFP = fleet.getFleetPoints();
		route.getExtra().fp = this.startingFP;
		
		if (fleetName == null) {
			fleetName = pickFleetName(fleet, origin, commander);
		}
		
		updateFleetName(fleet);
		fleet.setNoFactionInName(true);
				
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);		
		fleet.getMemoryWithoutUpdate().set(FLEET_MEM_KEY_INTEL, this);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MAY_GO_INTO_ABYSS, true);
		
		fleet.addEventListener(new SFFleetEventListener(this));
		
		fleet.setAIMode(AI_MODE);
		fleet.setTransponderOn(false);

		if (Global.getSettings().getModManager().isModEnabled("second_in_command")) {
			handleSecondInCommand(fleet);
		}
		
		isAlive = true;
		waitingForSpawn = false;
		
		this.fleet = fleet;
		tempFleet = null;
		
		return fleet;
	}

	protected void handleSecondInCommand(CampaignFleetAPI fleet) {
		// manually generate our own, maxed-out XO set
		fleet.addTag("sc_do_not_generate_skills");
		SCData data = SCUtils.getFleetData(fleet);
		NexUtilsSIC.generateRandomXOs(data, fleet, 3, 15);
	}
	
	public float getMonthsSuppliesRemaining() {
		if (fleet == null) return 0;
		
		int supplies = (int)fleet.getCargo().getSupplies();
		supplies -= fleet.getLogistics().getTotalRepairAndRecoverySupplyCost();
		
		float suppliesPerDay = fleet.getLogistics().getShipMaintenanceSupplyCost();
		return (supplies/suppliesPerDay/30);
	}
	
	public float getFuelFractionRemaining() {
		if (fleet == null) return 0;
		
		int fuel = (int)fleet.getCargo().getFuel();
		return fuel/fleet.getCargo().getMaxFuel();
	}
		
	public void buySupplies(int wanted, MarketAPI market) {
		
	}
	
	public void buyFuel(int wanted, MarketAPI market) {
		
	}
	
	public boolean reviveDeadMember(FleetMemberAPI member) {
		boolean isDead = deadMembers.remove(member);
		if (!isDead) return false;
		fleet.getFleetData().addFleetMember(member);
		member.getStatus().repairFully();
		member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		
		route.getExtra().fp = (float)fleet.getFleetPoints();
		
		return true;
	}
	
	@Override
	protected void generateFlagshipAndCommanderIfNeeded(RouteData thisRoute) {
		// do nothing
	}
	
	@Override
	protected void injectFlagship(CampaignFleetAPI fleet) {
		// do nothing
	}

	@Override
	protected boolean checkRebuild(float damage) {
		return false;
	}
	
	@Override
	protected void endEvent() {
		// you die on my command, not before
	}
	
	public void disband() {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		CampaignFleetAPI toDisband = this.fleet;
		if (toDisband == null) toDisband = tempFleet;
		
		//float reviveCostDebug = getReviveCost(deadMembers);
		//log.info("Reviving dead ships would cost " + reviveCostDebug + " credits");

		List<FleetMemberAPI> checkForSurplusOfficers = new ArrayList<>();
		
		for (OfficerDataAPI officer : toDisband.getFleetData().getOfficersCopy()) {
			if (officer.getPerson().isAICore()) continue;
			player.getFleetData().addOfficer(officer);
		}
		for (FleetMemberAPI member : toDisband.getFleetData().getMembersListCopy()) {
			player.getFleetData().addFleetMember(member);
			checkForSurplusOfficers.add(member);
		}
		for (FleetMemberAPI member : getDeadMembers()) {
			player.getFleetData().addFleetMember(member);
			// so they don't vanish if not repaired immediately
			member.getRepairTracker().performRepairsFraction(0.001f);
			checkForSurplusOfficers.add(member);
		}
		
		player.getCargo().addAll(toDisband.getCargo());

		stripOfficersIfExcess(Global.getSector().getCampaignUI().getCurrentInteractionDialog(), checkForSurplusOfficers, true);
				
		player.forceSync();
				
		endAfterDelay();
		toDisband.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
	}
	
	public void unstickFleet() {
		log.info("Beginning fleet unstick");
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet == null) {
			log.warn("Attempted to unstick nonexistent fleet");
			return;
		}
		if (routeAI.currentTask == null) {
			log.warn("No current task, cannot unstick");
			return;
		}
		SectorEntityToken dest = routeAI.currentTask.getEntity();
		if (dest == null) {
			log.info("Task has no destination, translocating fleet to player instead");
			dest = Global.getSector().getPlayerFleet();
		}
		
		LocationAPI from = fleet.getContainingLocation();
		if (from != null) from.removeEntity(fleet);
		LocationAPI to = dest.getContainingLocation();
		to.addEntity(fleet);
		fleet.setLocation(dest.getLocation().x, dest.getLocation().y);
		log.info("Fleet teleported to " + to.getName());
	}

	/*
		Bug: If a fresh-from-custom-production variant is added to a special task group, the variant in the fleet will revert to stock on game load
		until this method reverts it to the variant saved in intel
		This happens every game load

		To prevent this, the variant source needs to be marked as REFIT *and* its originalVariant needs to be set to null;
		the latter prevents reverting to stock variant on fleet deflation
	 */
	public void checkVariants() {
		if (fleet == null) return;
		int errorCount = 0;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			ShipVariantAPI variant = member.getVariant();
			ShipVariantAPI saved = storedVariants.get(member);
			if (saved == null) {
				log.warn("Missing stored variant for fleet member " + member.getShipName() + ", "
						+ member.getHullSpec().getNameWithDesignationWithDashClass());
				continue;
			}
			if (variant != saved) {
				String warn = "Warning: Variant for " + member.getShipName() + ", " + member.getHullSpec().getNameWithDesignationWithDashClass()
						+ " does not match saved variant, restoring";
				//Global.getSector().getCampaignUI().addMessage(warn, Misc.getNegativeHighlightColor(), member.getShipName(), "", Misc.getHighlightColor(), Color.WHITE);
				if (PRINT_VARIANT_WARNING) {
					log.warn(warn);
					//printVariant(member, variant);
					//printVariant(member, saved);
				}
				saved.setOriginalVariant(null);
				member.setVariant(saved, false, true);
				errorCount++;
			}
		}
		if (PRINT_VARIANT_WARNING && errorCount > 0) {
			String warn = String.format("Warning: Variants for %s ship(s) do not match saved variant, restoring", errorCount);
			Global.getSector().getCampaignUI().addMessage(warn, Misc.getNegativeHighlightColor(),
					errorCount + "", "", Misc.getHighlightColor(), Color.WHITE);
		}
	}

	// was going to do something with this but decided not to
	@Deprecated
	protected void checkAutomatedShips() {
		if (fleet == null) return;
		if (commander == null || commander.getStats().getSkillLevel(Skills.AUTOMATED_SHIPS) >= 1) return;

		log.info("Checking PSF automated ship status");
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			if (!Misc.isAutomated(member)) continue;
			if (Automated.isAutomatedNoPenalty(member)) continue;
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
			log.info("Applying max CR for automated ship " + member.getShipName() + " (" + member.getHullSpec().getHullNameWithDashClass() + ")");
		}
	}
	
	protected void recreate(boolean all) {
		InteractionDialogAPI dial = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
		
		// safety
		if (flagship == null) {
			for (FleetMemberAPI member : deadMembers) {
				if (member.isFlagship()) {
					flagship = member;
					break;
				}
			}
		}
		if (flagship == null) {
			Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Error: Flagship not found, cannot revive");
			return;
		}
		
		int cost = all ? getReviveCost(deadMembers) : getReviveCost(flagship);
		
		if (all) {
			for (FleetMemberAPI dead : new ArrayList<>(deadMembers)) {
				reviveDeadMember(dead);
			}
		}
		else {
			reviveDeadMember(flagship);
		}
		
		if (dial != null) AddRemoveCommodity.addCreditsLossText(cost, dial.getTextPanel());
		Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
		
		tempFleet = fleet;
		
		// create new route
		if (dial != null && dial.getInteractionTarget() != null && dial.getInteractionTarget().getMarket() != null) {
			origin = dial.getInteractionTarget().getMarket();
		}
		
		RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(origin);
		extra.factionId = faction.getId();
		extra.fp = startingFP;
		extra.fleetType = FLEET_TYPE;
		extra.strength = getAdjustedStrength(startingFP, origin);
		route = RouteManager.getInstance().addRoute(SOURCE_ID, origin, spawnSeed, extra, this);
		routeAI.addInitialTask();
		waitingForSpawn = true;

		addListenerIfNeeded();
	}

	protected void reverseCompatibility() {
		if (membersBackup == null) {
			membersBackup = new LinkedHashSet<>();
			CampaignFleetAPI fleet = this.fleet;
			if (fleet == null) fleet = this.tempFleet;
			if (fleet != null) membersBackup.addAll(fleet.getFleetData().getMembersListCopy());
			membersBackup.addAll(deadMembers);
		}
		if (storedVariants == null) {
			storedVariants = new LinkedHashMap<>();
			CampaignFleetAPI fleet = this.fleet;
			if (fleet == null) fleet = this.tempFleet;
			if (fleet != null) {
				for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
					storedVariants.put(member, member.getVariant());
				}
			}
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
		updateFuelUse();

		float days = Misc.getDays(amount);
		variantCheckInterval.advance(days);
		if (variantCheckInterval.intervalElapsed()) {
			//checkVariants();
		}
	}
	
	protected void updateFuelUse() {
		if (fleet == null || !fleet.isAlive()) return;
		
		if (!fleet.getContainingLocation().isHyperspace()) {
			lastPos = null;
			return;
		}
		
		if (lastPos != null) {
			float distLY = Misc.getDistanceLY(lastPos, fleet.getLocation());
			float fuelPerLY = fleet.getLogistics().getFuelCostPerLightYear();
			float fuelUsed = distLY * fuelPerLY;
			if (fuelUsed > 0) {
				//log.info(this.getName() + " registering fuel use: " + fuelUsed);
				fuelUsedLastInterval += fuelUsed;
			}
			
		}
		lastPos = new Vector2f(fleet.getLocation());
	}
	
	protected void processCosts() {
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float timeMult = 1f / numIter;
		
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
		
		String nodeId = "nex_node_id_psf_" + id;
		FDNode psfNode = processMonthlyReportNode(report, fleetNode, nodeId, 
				this.getName(), faction.getCrest(), 0, false);
		
		float commanderFee = Global.getSettings().getFloat("officerSalaryBase") * 5;
		commanderFee *= OFFICER_SALARY_MULT;
		FDNode feeNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_commFee", 
				getString("reportNode_commander"), commander.getPortraitSprite(), commanderFee * timeMult, false);
		
		if (fleet == null) return;

		float maintMult = NexConfig.specialForcesMaintMult;

		float totalMult = timeMult * maintMult;

		float officerSalary = 0;
		for (OfficerDataAPI officer : fleet.getFleetData().getOfficersCopy()) {
			officerSalary += Misc.getOfficerSalary(officer.getPerson());
		}
		officerSalary *= OFFICER_SALARY_MULT;
		FDNode officerNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_offSal", 
				getString("reportNode_officer"), Global.getSettings().getSpriteName("income_report", "officers"), 
				officerSalary * totalMult, false);
		
		CommoditySpecAPI crewSpec = Global.getSettings().getCommoditySpec(Commodities.CREW);
		float crew = fleet.getFleetData().getMinCrew();
		float crewSalary = crew * Global.getSettings().getInt("crewSalary") * CREW_SALARY_MULT;
		FDNode crewNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_crewSal", 
				getString("reportNode_crew"), crewSpec.getIconName(), crewSalary * totalMult, false);
		
		float suppMaint = NexUtilsFleet.getTrueMonthlyMaintenanceCost(fleet);
		CommoditySpecAPI suppliesSpec = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES);
		//log.info("Special task group uses " + suppMaint + " per month");
		float maintCost = suppMaint * suppliesSpec.getBasePrice() * SUPPLY_COST_MULT;
		FDNode maintNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_suppliesCost", 
				getString("reportNode_supplies"), suppliesSpec.getIconName(), maintCost * totalMult, false);
		
		CommoditySpecAPI fuelSpec = Global.getSettings().getCommoditySpec(Commodities.FUEL);
		float fuelCost = this.fuelUsedLastInterval * fuelSpec.getBasePrice() * FUEL_COST_MULT * totalMult;
		FDNode fuelNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_fuelCost", 
				getString("reportNode_fuel"), fuelSpec.getIconName(), fuelCost, false);
		fuelUsedLastInterval = 0;
	}
	
	protected FDNode processMonthlyReportNode(MonthlyReport rpt, FDNode parent, 
			String id, String name, String icon, float amount, boolean isIncome) 
	{
		FDNode node = rpt.getNode(parent, id);
		node.name = name;
		node.custom = id;
		node.icon = icon;
		if (amount != 0) {
			if (isIncome) node.income += amount;
			else node.upkeep += amount;
		}
		
		return node;
	}

	/**
	 * Check if a particular 'revive ships' button in this intel item should be enabled or disabled.
	 * @param button {@code ButtonAPI} that should be disabled if necessary.
	 * @param info
	 * @param fromScratch True if the fleet was wiped out and is now being resurrected. False if still alive.
	 * @param cost Cost of revival (may be for a single ship, or the whole fleet).
	 * @return False if button was disabled, true otherwise.
	 */
	protected boolean validateReviveButton(ButtonAPI button, TooltipMakerAPI info, boolean fromScratch, float cost) {
		float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();

		if (fromScratch) {
			InteractionDialogAPI dial = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			boolean allow = dial != null && dial.getInteractionTarget() != null && dial.getInteractionTarget().getMarket() != null;
			if (!allow) {
				disableReviveButton(button, info, getString("intelTooltipDisbandNotDocked"));
				return false;
			}
		} else {
			BattleAPI bat = fleet.getBattle();
			if (bat != null && !bat.isPlayerInvolved()) {
				disableReviveButton(button, info, getString("intelTooltipDisbandNotDocked"));
				return false;
			}
			boolean haveMarket = false;
			for (MarketAPI market : Misc.getMarketsInLocation(fleet.getContainingLocation())) {
				if (market.getFaction().isAtBest(fleet.getFaction(), RepLevel.INHOSPITABLE))
					continue;
				if (MathUtils.getDistance(market.getPrimaryEntity(), fleet) > Nex_SpecialForcesConfig.MAX_REVIVE_DISTANCE)
					continue;

				haveMarket = true;
				break;
			}

			if (!haveMarket) {
				disableReviveButton(button, info, getString("dialogTooltipNoMarketForRevive"));
				return false;
			}
		}

		if (cost > credits) {
			String reason = String.format(getString("intelTooltipRecreateNotEnoughCredits"), Misc.getWithDGS(cost));
			disableReviveButton(button, info, reason);
			return false;
		}
		return true;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		if (!isEnding() && !isEnded()) {
			boolean modeOK = mode == ListInfoMode.INTEL || mode == ListInfoMode.MAP_TOOLTIP;
			if (isUpdate && listInfoParam == DESTROYED_UPDATE || (modeOK && !isAlive && !waitingForSpawn))
			{
				info.addPara(getString("intelBulletDestroyed"), tc, 3);
				return;
			}
		}
		
		super.addBulletPoints(info, mode, isUpdate, tc, initPad);
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float pad = 3, opad = 10;
		
		createSmallDescriptionPart1(info, width);
		
		if (route == null || isEnding() || isEnded()) {
			return;
		}
		
		if (isAlive) {
			boolean inBattle = fleet != null && fleet.getBattle() != null;
			ButtonAPI command = info.addButton(getString("intelButtonCommand"), 
					BUTTON_COMMAND, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			if (inBattle) {
				command.setEnabled(false);
				info.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(getString("intelTooltipCommandInBattle"), 360), 
						TooltipMakerAPI.TooltipLocation.BELOW);
			}

			// revive all
			if (!deadMembers.isEmpty()) {
				ButtonAPI revive = info.addButton(getString("intelButtonRecreateAll"),
						BUTTON_RECREATE_ALL, faction.getBaseUIColor(), faction.getDarkUIColor(),
						(int)(width), 20f, pad);
				float cost = getReviveCost(deadMembers);
				validateReviveButton(revive, info, false, cost);
			}

			ButtonAPI check = info.addAreaCheckbox(getString("intelButtonCheckIndependent"), BUTTON_INDEPENDENT_MODE, 
					faction.getBaseUIColor(), faction.getDarkUIColor(), faction.getBrightUIColor(),
					(int)width, 20f, opad);
			check.setChecked(independentMode);
						
			// unstick
			info.addButton(getString("intelButtonUnstick"), 
					BUTTON_UNSTICK, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			
		} else if (!waitingForSpawn) {
			// dead mode
			
			info.addButton(getString("intelButtonDisband"),
					BUTTON_DISBAND, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			
			// revive flagship
			ButtonAPI button = button = info.addButton(getString("intelButtonRecreate"),
					BUTTON_RECREATE, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			float cost = getReviveCost(flagship);
			validateReviveButton(button, info, true, cost);
			
			// revive all
			button = info.addButton(getString("intelButtonRecreateAll"), 
					BUTTON_RECREATE_ALL, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, pad);
			cost = getReviveCost(deadMembers);
			validateReviveButton(button, info, true, cost);
		}

		// list dead members
		if (!deadMembers.isEmpty()) {
			info.addPara(getString("intelDescLostShips"), opad);
			NexUtilsGUI.addShipList(info, width, new ArrayList<>(deadMembers), 48, pad);
		}
		if (Global.getSettings().isDevMode() || ExerelinModPlugin.isNexDev) {
			info.addPara("All members", opad);
			NexUtilsGUI.addShipList(info, width, new ArrayList<>(membersBackup), 48, pad);
		}
		
		if (isAlive)
			createSmallDescriptionPart2(info, width);
	}
	
	public void disableReviveButton(ButtonAPI button, TooltipMakerAPI info, String reason) {
		button.setEnabled(false);
		info.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(reason, 360), 
				TooltipMakerAPI.TooltipLocation.BELOW);
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		if (buttonId == BUTTON_RECREATE || buttonId == BUTTON_RECREATE_ALL) {
			boolean all = buttonId == BUTTON_RECREATE_ALL;
			float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
			float cost;
			if (all) cost = getReviveCost(deadMembers);
			else cost = getReviveCost(flagship);
			
			LabelAPI txt = prompt.addPara(getString("intelConfirmPromptRecreate" + (all ? "All" : "")), 
					0, Misc.getHighlightColor(),
					Misc.getDGSCredits(cost), 
					Misc.getDGSCredits(credits));
			txt.setHighlightColors(Misc.getHighlightColor(), credits >= cost ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
		}
		else if (buttonId == BUTTON_UNSTICK) {
			prompt.addPara(getString("intelConfirmPromptUnstick"), 0);
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return buttonId == BUTTON_RECREATE || buttonId == BUTTON_RECREATE_ALL || buttonId == BUTTON_UNSTICK;
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_COMMAND) {
			RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl();
			ui.showDialog(route.getActiveFleet(), plugin);
			plugin.getMemoryMap().get(MemKeys.LOCAL).set("$nex_remoteCommand", true, 0);
			plugin.getMemoryMap().get(MemKeys.LOCAL).set("$nex_uiToRefresh", ui, 0);
			plugin.getMemoryMap().get(MemKeys.LOCAL).set("$option", "nex_commandSF_main", 0);
			plugin.fireBest("DialogOptionSelected");
		}
		else if (buttonId == BUTTON_DISBAND) {
			disband();
			ui.updateUIForItem(this);
		}
		else if (buttonId == BUTTON_RECREATE) {
			recreate(false);
			ui.updateUIForItem(this);
		}
		else if (buttonId == BUTTON_RECREATE_ALL) {
			if (isAlive) {
				for (FleetMemberAPI dead : new ArrayList<>(deadMembers)) {
					reviveDeadMember(dead);
				}
			}
			else recreate(true);
			ui.updateUIForItem(this);
		}
		else if (buttonId == BUTTON_INDEPENDENT_MODE) {
			independentMode = !independentMode;
		}
		else if (buttonId == BUTTON_UNSTICK) {
			unstickFleet();
			ui.updateUIForItem(this);
		}
		else {
			super.buttonPressConfirmed(buttonId, ui); //To change body of generated methods, choose Tools | Templates.
		}
	}
	
	@Override
	public int getDamage() {
		if (fleet == null) return 0;
		int liveFP = fleet.getFleetPoints();
		int deadFP = 0;
		for (FleetMemberAPI dead : deadMembers) {
			deadFP += dead.getFleetPointCost();
		}
		return (int)(deadFP/(deadFP+liveFP) * 100);
	}
	
	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_2;
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		Global.getSector().getListenerManager().removeListener(this);
	}

	public static List<PlayerSpecialForcesIntel> getActiveIntelList() {
		List<PlayerSpecialForcesIntel> list = new ArrayList<>();
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(PlayerSpecialForcesIntel.class)) {
			if (intel.isEnding() || intel.isEnded()) continue;
			list.add((PlayerSpecialForcesIntel) intel);
		}
		return list;
	}
	
	public static int getActiveIntelCount() {
		return getActiveIntelList().size();
	}
	
	public void reportShipDeath(FleetMemberAPI member) {
		deadMembers.add(member);
	}
	
	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		super.reportBattleOccurred(fleet, primaryWinner, battle);
		List<FleetMemberAPI> losses = Misc.getSnapshotMembersLost(fleet);
		deadMembers.addAll(losses);
	}
	
	@Override
	public void reportFleetDespawned(CampaignEventListener.FleetDespawnReason reason, Object param) {
		isAlive = false;
		
		// remove economy tick listener, no longer needed
		Global.getSector().getListenerManager().removeListener(this);
		
		if (reason != CampaignEventListener.FleetDespawnReason.DESTROYED_BY_BATTLE) {
			Global.getSector().getCampaignUI().showConfirmDialog(getString("warnMsg"), 
					getString("warnMsgButton1"), getString("warnMsgButton2"), 640, 320, null, null);
		}
		deadMembers.addAll(membersBackup);
		
		sendUpdateIfPlayerHasIntel(DESTROYED_UPDATE, false, false);
	}
	

	@Override
	public void reportEconomyTick(int iterIndex) {
		processCosts();
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
	
	public static float getReviveSupplyCost(FleetMemberAPI member) {
		if (member == null) return 0;
		float deployCost = member.getDeployCost();
		if (deployCost <= 1) {
			log.error(String.format("Ship %s has <1 deployment cost, applying safety", member.getShipName()));
			deployCost = 1f;
		}
		float suppliesPerCRPoint = member.getDeploymentCostSupplies()/deployCost;
		float suppliesPerDay = suppliesPerCRPoint * member.getRepairTracker().getRecoveryRate();
		//member.getRepairTracker().setSuspendRepairs(false);
		float daysToRepair = member.getRepairTracker().getRemainingRepairTime();

		float suppliesNeeded = daysToRepair * suppliesPerDay;
			
		return suppliesNeeded;
	}
	
	public static int getReviveCost(FleetMemberAPI member) {
		if (member == null) return 0;
		float cost = getReviveSupplyCost(member) * Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getBasePrice();
		return Math.round(cost);
	}
	
	public static int getReviveCost(Collection<FleetMemberAPI> members) {
		float supplies = 0;
		for (FleetMemberAPI member : members) {
			float suppliesNeeded = getReviveSupplyCost(member);
			supplies += suppliesNeeded;
		}
		//log.info("Total supplies: " + supplies);
		float supplyCost = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getBasePrice();
		
		return Math.round(supplies * supplyCost);
	}

	public static Set<PersonAPI> getOfficersAsPeople(CampaignFleetAPI fleet) {
		Set<PersonAPI> results = new LinkedHashSet<>();
		List<OfficerDataAPI> officers = fleet.getFleetData().getOfficersCopy();
		for (OfficerDataAPI officer : officers) {
			results.add(officer.getPerson());
		}
		return results;
	}

	/**
	 * Call after moving ships back into player fleet. Fixes exploit to have more officers on ships than actually permitted.
	 * @param dialog
	 * @param members
	 */
	public static void stripOfficersIfExcess(InteractionDialogAPI dialog, List<FleetMemberAPI> members, boolean disband) {
		if (members.isEmpty()) return;

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		int max = Global.getSector().getPlayerStats().getOfficerNumber().getModifiedInt();
		int count = playerFleet.getFleetData().getOfficersCopy().size();
		if (Global.getSettings().getModManager().isModEnabled("officerExtension")) {
			count = 0;

			Set<PersonAPI> officers = getOfficersAsPeople(playerFleet);
			for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
				if (officers.contains(member.getCaptain())) count++;
			}
		}

		if (count <= max) {
			return;
		}

		if (Global.getSettings().getBoolean("nex_specialForces_overOfficerLimitWarningOnly")) {
			String str = getString("dialogMsgOfficerOverLimitWarning");
			// already shown warning?
			if (Global.getSector().getCharacterData().getMemoryWithoutUpdate().getBoolean(MEM_KEY_OVER_OFFICER_LIMIT_WARN))
				return;

			if (!disband && dialog != null) {
				dialog.getTextPanel().setFontSmallInsignia();
				dialog.getTextPanel().addPara(str, Misc.getNegativeHighlightColor());
				dialog.getTextPanel().setFontInsignia();
				Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(MEM_KEY_OVER_OFFICER_LIMIT_WARN, true, 0);
			} else {
				Global.getSector().getCampaignUI().addMessage(str, Misc.getNegativeHighlightColor());
			}
			return;
		}

		float over = count - max;
		int num = 0;	// brain too cooked to figure out how to compute this directly from i
		if (dialog != null) dialog.getTextPanel().setFontSmallInsignia();
		for (int i=members.size() - 1; i >=0; i--) {
			FleetMemberAPI member = members.get(i);
			PersonAPI captain = member.getCaptain();
			//log.info(String.format("Check for %s: null %s, default %s, AI %s, merc %s, unremovable %s", member.getShipName() + " " + member.getHullSpec().getHullNameWithDashClass(),
			//		captain == null, captain.isDefault(), captain.isAICore(), Misc.isMercenary(captain), Misc.isUnremovable(captain)));
			if (captain == null || captain.isDefault() || captain.isAICore() || Misc.isMercenary(captain) || Misc.isUnremovable(captain)) continue;

			member.setCaptain(null);
			String str = getString("dialogMsgStrippedOfficer");
			str = String.format(str, captain.getNameString(), member.getShipName(), member.getHullSpec().getHullNameWithDashClass());
			if (dialog != null) {
				LabelAPI label = dialog.getTextPanel().addPara(str);
				label.setHighlight(captain.getNameString(), member.getShipName());
			}

			num++;
			if (num >= over) break;
		}
		if (dialog != null) dialog.getTextPanel().setFontInsignia();
	}

	@Override
	public void onGameLoad(boolean newGame) {
		addListenerIfNeeded();
		checkVariants();
		if (fleet != null && !fleet.getMemoryWithoutUpdate().contains(MemFlags.MAY_GO_INTO_ABYSS)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MAY_GO_INTO_ABYSS, true);
		}
	}

	@Override
	public void beforeGameSave() {}

	@Override
	public void afterGameSave() {}

	@Override
	public void onGameSaveFailed() {}

	@Override
	public void onNewGameAfterProcGen() {}

	@Override
	public void onNewGameAfterEconomyLoad() {}

	@Override
	public void onNewGameAfterTimePass() {}

	public static void printVariant(FleetMemberAPI member, ShipVariantAPI variant) {
		StringBuilder sb = new StringBuilder();
		String str = "\r\n--------------------";
		sb.append(str);
		str = String.format("Variant %s for ship %s (%s)", variant.getDisplayName(), member.getShipName(), member.getHullSpec().getHullNameWithDashClass());
		sb.append("\r\n" + str);
		str = String.format("Source: %s", variant.getSource());
		sb.append("\r\n" + str);
		sb.append("\r\n" + variant.toString());
		log.info(sb.toString());
	}
}
