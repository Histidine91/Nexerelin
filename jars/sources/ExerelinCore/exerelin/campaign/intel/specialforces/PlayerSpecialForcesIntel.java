package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.fleets.NexAssembleStage.getAdjustedStrength;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.FLEET_TYPE;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.SOURCE_ID;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.getString;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsGUI;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.util.vector.Vector2f;

public class PlayerSpecialForcesIntel extends SpecialForcesIntel implements EconomyTickListener {
	
	// for now
	// actually never turn this on? when we invoke combat costs this becomes a mess overall
	// on the other hand, should combat actually be free?
	// can't be helped, it _is_ free if far-autoresolved
	public static final boolean AI_MODE = true;
	
	public static final float CREW_SALARY_MULT = 1f;	// should not be high since the utility of assets in a PSF fleet is much lower than in the player fleet
	public static final float SUPPLY_COST_MULT = 1f;	// ditto, even though we're getting free combat out of the deal
	
	public static final Object DESTROYED_UPDATE = new Object();	
	protected static final Object BUTTON_COMMAND = new Object();
	protected static final Object BUTTON_DISBAND = new Object();
	protected static final Object BUTTON_RECREATE = new Object();
	protected static final Object BUTTON_RECREATE_ALL = new Object();
	protected static final Object BUTTON_INDEPENDENT_MODE = new Object();
	
	@Setter protected CampaignFleetAPI tempFleet;
	protected CampaignFleetAPI fleet;
	
	/**
	 * Stores all members added to the fleet (and not subsequently removed), dead or alive. For safety/debugging purposes.
	 */
	@Getter protected Set<FleetMemberAPI> membersBackup = new LinkedHashSet<>();
	@Getter protected Set<FleetMemberAPI> deadMembers = new LinkedHashSet<>();
	
	@Getter	protected boolean independentMode = true;
	protected boolean isAlive;
	protected boolean waitingForSpawn;
	protected float fuelUsedLastInterval;
	protected transient Vector2f lastPos;
	
	protected Object readResolve() {
		if (!Global.getSector().getListenerManager().hasListener(this)) {
			Global.getSector().getListenerManager().addListener(this);
		}
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
		Global.getSector().getListenerManager().addListener(this);
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
		}
		fleet.setInflated(true);
		fleet.setInflater(null);		
		
		commander.setRankId(Ranks.SPACE_CAPTAIN);
		
		fleet.setNoAutoDespawn(true);
		
		fleet.setFaction(faction.getId(), false);
				
		syncFleet(fleet);
		this.startingFP = fleet.getFleetPoints();
		
		if (fleetName == null) {
			fleetName = pickFleetName(fleet, origin, commander);
		}
		
		fleet.setName(faction.getFleetTypeName(FLEET_TYPE) + " – " + fleetName);
		fleet.setNoFactionInName(true);
				
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);		
		fleet.getMemoryWithoutUpdate().set(FLEET_MEM_KEY_INTEL, this);
		
		fleet.addEventListener(new SFFleetEventListener(this));
		
		fleet.setAIMode(AI_MODE);
		fleet.setTransponderOn(false);
		
		isAlive = true;
		waitingForSpawn = false;
		
		this.fleet = fleet;
		tempFleet = null;
		
		return fleet;
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
		
		for (OfficerDataAPI officer : toDisband.getFleetData().getOfficersCopy()) {
			if (officer.getPerson().isAICore()) continue;
			player.getFleetData().addOfficer(officer);
		}
		for (FleetMemberAPI member : toDisband.getFleetData().getMembersListCopy()) {
			player.getFleetData().addFleetMember(member);
		}
		for (FleetMemberAPI member : getDeadMembers()) {
			player.getFleetData().addFleetMember(member);
			// so they don't vanish if not repaired immediately
			member.getRepairTracker().performRepairsFraction(0.001f);
		}
		
		player.getCargo().addAll(toDisband.getCargo());
				
		player.forceSync();
				
		endAfterDelay();
		toDisband.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
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
	}
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
		updateFuelUse();
		
		if (membersBackup == null) {
			membersBackup = new LinkedHashSet<>();
			CampaignFleetAPI fleet = this.fleet;
			if (fleet == null) fleet = this.tempFleet;
			if (fleet != null) membersBackup.addAll(fleet.getFleetData().getMembersListCopy());
			membersBackup.addAll(deadMembers);
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
		float f = 1f / numIter;
		
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
		
		FDNode psfNode = processMonthlyReportNode(report, fleetNode, "nex_node_id_psf", 
				this.getName(), faction.getCrest(), 0, false);
		
		float commanderFee = Global.getSettings().getFloat("officerSalaryBase") * 5;
		FDNode feeNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_commFee", 
				getString("reportNode_commander"), commander.getPortraitSprite(), commanderFee * f, false);
		
		if (fleet == null) return;
		
		float officerSalary = 0;
		for (OfficerDataAPI officer : fleet.getFleetData().getOfficersCopy()) {
			officerSalary += Misc.getOfficerSalary(officer.getPerson());
		}
		FDNode officerNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_offSal", 
				getString("reportNode_officer"), Global.getSettings().getSpriteName("income_report", "officers"), 
				officerSalary * f, false);
		
		CommoditySpecAPI crewSpec = Global.getSettings().getCommoditySpec(Commodities.CREW);
		float crew = fleet.getFleetData().getMinCrew();
		float crewSalary = crew * Global.getSettings().getInt("crewSalary") * 1.25f;
		FDNode crewNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_crewSal", 
				getString("reportNode_crew"), crewSpec.getIconName(), crewSalary * f, false);
		
		float suppMaint = fleet.getLogistics().getShipMaintenanceSupplyCost() * 30;
		CommoditySpecAPI suppliesSpec = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES);
		log.info("Special task group uses " + suppMaint + " per month");
		float maintCost = suppMaint * suppliesSpec.getBasePrice() * 1.25f;
		FDNode maintNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_suppliesCost", 
				getString("reportNode_supplies"), suppliesSpec.getIconName(), maintCost * f, false);
		
		CommoditySpecAPI fuelSpec = Global.getSettings().getCommoditySpec(Commodities.FUEL);
		float fuelCost = this.fuelUsedLastInterval * fuelSpec.getBasePrice();
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
			ButtonAPI command = info.addButton(getString("intelButtonCommand"), 
					BUTTON_COMMAND, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			if (fleet != null && fleet.getBattle() != null) {
				command.setEnabled(false);
				info.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(getString("intelTooltipCommandInBattle"), 360), 
						TooltipMakerAPI.TooltipLocation.BELOW);
			}
			ButtonAPI check = info.addAreaCheckbox(getString("intelButtonCheckIndependent"), BUTTON_INDEPENDENT_MODE, 
					faction.getBaseUIColor(), faction.getDarkUIColor(), faction.getBrightUIColor(),
					(int)width, 20f, opad);
			check.setChecked(independentMode);			
		} else if (!waitingForSpawn) {
			// dead mode
			
			ButtonAPI button = info.addButton(getString("intelButtonDisband"), 
					BUTTON_DISBAND, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
						
			InteractionDialogAPI dial = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			boolean allow = dial != null && dial.getInteractionTarget() != null && dial.getInteractionTarget().getMarket() != null;
			if (!allow) {
				disableReviveButton(button, info, getString("intelTooltipDisbandNotDocked"));
			}
			
			float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
			
			// revive flagship
			button = info.addButton(getString("intelButtonRecreate"), 
					BUTTON_RECREATE, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			float cost = getReviveCost(flagship);
			if (!allow) {
				disableReviveButton(button, info, getString("intelTooltipDisbandNotDocked"));
			}
			else if (cost > credits) {
				String reason = String.format(getString("intelTooltipRecreateNotEnoughCredits"), Misc.getWithDGS(cost));
				disableReviveButton(button, info, reason);
			}
			
			// revive all
			button = info.addButton(getString("intelButtonRecreateAll"), 
					BUTTON_RECREATE_ALL, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, pad);
			cost = getReviveCost(deadMembers);
			if (!allow) {
				disableReviveButton(button, info, getString("intelTooltipDisbandNotDocked"));
			}
			else if (cost > credits) {
				String reason = String.format(getString("intelTooltipRecreateNotEnoughCredits"), Misc.getWithDGS(cost));
				disableReviveButton(button, info, reason);
			}
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
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return buttonId == BUTTON_RECREATE || buttonId == BUTTON_RECREATE_ALL;
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
			recreate(true);
			ui.updateUIForItem(this);
		}
		else if (buttonId == BUTTON_INDEPENDENT_MODE) {
			independentMode = !independentMode;
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
	
	public static int getActiveIntelCount() {
		int num = 0;
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(PlayerSpecialForcesIntel.class)) {
			if (intel.isEnding() || intel.isEnded()) continue;
			num++;
		}
		return num;
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
		float suppliesPerCRPoint = member.getDeploymentCostSupplies()/member.getDeployCost();
		float suppliesPerDay = suppliesPerCRPoint * member.getRepairTracker().getRecoveryRate();
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
}
