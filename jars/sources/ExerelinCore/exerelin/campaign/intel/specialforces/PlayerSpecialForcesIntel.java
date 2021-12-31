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
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.FLEET_TYPE;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.getString;
import exerelin.utilities.NexUtilsGUI;
import java.awt.Color;
import java.util.ArrayList;
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
	
	public static final float CREW_SALARY_MULT = 1.25f;
	public static final float SUPPLY_COST_MULT = 1.25f;
	public static final float REVIVE_COST_MULT = 0.7f;
	
	public static final Object DESTROYED_UPDATE = new Object();	
	protected static final Object BUTTON_COMMAND = new Object();
	protected static final Object BUTTON_RECREATE = new Object();
	protected static final Object BUTTON_INDEPENDENT_MODE = new Object();
	
	@Setter protected CampaignFleetAPI tempFleet;
	protected CampaignFleetAPI fleet;
	@Getter protected Set<FleetMemberAPI> deadMembers = new LinkedHashSet<>();
	
	@Getter	protected boolean independentMode = true;
	protected boolean isAlive;
	protected float fuelUsedLastInterval;
	protected transient Vector2f lastPos;
	
	public PlayerSpecialForcesIntel(MarketAPI origin, FactionAPI faction) {
		super(origin, faction, 0);
		isPlayer = true;
	}
	
	public void init() {
		super.init(commander);
		Global.getSector().getListenerManager().addListener(this);
	}
			
	public void setFlagship(FleetMemberAPI member) {
		flagship = member;
		if (fleet != null) {
			fleet.getFleetData().setFlagship(flagship);
		}
	}
	
	@Override
	public void setCommander(PersonAPI commander) {
		super.setCommander(commander);
		if (commander != null) commander.setRankId(Ranks.SPACE_CAPTAIN);
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
		
		for (OfficerDataAPI od : tempFleet.getFleetData().getOfficersCopy()) {
			//log.info("Adding officer " + officer.getNameString());
			fleet.getFleetData().addOfficer(od);
		}
		for (FleetMemberAPI live : tempFleet.getFleetData().getMembersListCopy()) {
			fleet.getFleetData().addFleetMember(live);
		}
		commander = tempFleet.getCommander();
		flagship = tempFleet.getFlagship();
		fleet.setCommander(commander);
		fleet.getFleetData().setFlagship(flagship);
		fleet.setInflated(true);
		fleet.setInflater(null);
		
		fleet.setNoAutoDespawn(true);
		
		fleet.setFaction(faction.getId(), true);
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
				
		syncFleet(fleet);
		this.startingFP = fleet.getFleetPoints();
		
		if (fleetName == null) {
			fleetName = pickFleetName(fleet, origin, commander);
		}
		
		fleet.setName(faction.getFleetTypeName(FLEET_TYPE) + " – " + fleetName);
		fleet.setNoFactionInName(true);
		
		fleet.addEventListener(new SFFleetEventListener(this));
		fleet.getMemoryWithoutUpdate().set("$nex_sfIntel", this);
		
		fleet.setAIMode(AI_MODE);		
		
		isAlive = true;
		
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
		
		for (OfficerDataAPI officer : toDisband.getFleetData().getOfficersCopy()) {
			if (officer.getPerson().isAICore()) continue;
			player.getFleetData().addOfficer(officer);
		}
		for (FleetMemberAPI member : toDisband.getFleetData().getMembersListCopy()) {
			player.getFleetData().addFleetMember(member);
		}
		for (FleetMemberAPI member : getDeadMembers()) {
			player.getFleetData().addFleetMember(member);
		}
		
		player.getCargo().addAll(toDisband.getCargo());
				
		player.forceSync();
				
		endAfterDelay();
		toDisband.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
	}
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
		updateFuelUse();
	}
	
	protected void updateFuelUse() {
		if (fleet == null || fleet.isAlive()) return;
		
		if (!fleet.getContainingLocation().isHyperspace()) {
			lastPos = null;
			return;
		}
		
		if (lastPos != null) {
			float distLY = Misc.getDistance(lastPos, fleet.getLocation());
			float fuelPerLY = fleet.getLogistics().getFuelCostPerLightYear();
			fuelUsedLastInterval += distLY * fuelPerLY;
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
				getString("reportNode_officer"), faction.getCrest(), officerSalary * f, false);
		
		float crew = fleet.getFleetData().getMinCrew();
		float crewSalary = crew * Global.getSettings().getInt("crewSalary") * 1.25f;
		FDNode crewNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_crewSal", 
				getString("reportNode_crew"), commander.getPortraitSprite(), crewSalary * f, false);
		
		float suppMaint = fleet.getLogistics().getShipMaintenanceSupplyCost();
		CommoditySpecAPI suppliesSpec = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES);
		float maintCost = suppMaint * suppliesSpec.getBasePrice() * 1.25f;
		FDNode maintNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_suppliesCost", 
				getString("reportNode_supplies"), suppliesSpec.getIconName(), maintCost * f, false);
		
		CommoditySpecAPI fuelSpec = Global.getSettings().getCommoditySpec(Commodities.FUEL);
		float fuelCost = this.fuelUsedLastInterval;
		FDNode fuelNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_fuelCost", 
				getString("reportNode_fuel"), fuelSpec.getIconName(), fuelCost * f, false);
		fuelUsedLastInterval = 0;
	}
	
	protected FDNode processMonthlyReportNode(MonthlyReport rpt, FDNode parent, 
			String id, String name, String icon, float amount, boolean isIncome) 
	{
		FDNode node = rpt.getNode(parent, id);
		node.name = name;
		node.custom = id;
		node.icon = faction.getCrest();
		if (amount != 0) {
			if (isIncome) node.income += amount;
			else node.upkeep += amount;
		}
		
		return node;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		if (!isEnding() && !isEnded()) {
			if (isUpdate && listInfoParam == DESTROYED_UPDATE || (mode == ListInfoMode.INTEL && !isAlive))
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
			info.addButton(getString("intelButtonCommand"), 
					BUTTON_COMMAND, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			ButtonAPI check = info.addAreaCheckbox(getString("intelButtonCheckIndependent"), BUTTON_INDEPENDENT_MODE, 
					faction.getBaseUIColor(), faction.getDarkUIColor(), faction.getBrightUIColor(),
					(int)width, 20f, opad);
			check.setChecked(independentMode);			
		} else {
			ButtonAPI button = info.addButton(getString("intelButtonDisband"), 
					BUTTON_RECREATE, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
			
			InteractionDialogAPI dial = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			boolean allow = dial != null && dial.getInteractionTarget() != null && dial.getInteractionTarget().getMarket() != null
					&& dial.getInteractionTarget().getMarket().getFaction().isPlayerFaction();
			if (!allow) {
				button.setEnabled(false);
				info.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip("[temp] Must be docked at a player market.", 360), 
						TooltipMakerAPI.TooltipLocation.BELOW);
			}
		}

		// list dead members
		if (!deadMembers.isEmpty()) {
			info.addPara(getString("intelDescLostShips"), opad);
			NexUtilsGUI.addShipList(info, width, new ArrayList<>(deadMembers), 48, pad);
		}		
		
		createSmallDescriptionPart2(info, width);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_COMMAND) {
			RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl();
			ui.showDialog(route.getActiveFleet(), plugin);
			plugin.getMemoryMap().get(MemKeys.LOCAL).set("$nex_remoteCommand", true, 0);
			plugin.getMemoryMap().get(MemKeys.LOCAL).set("$option", "nex_commandSF_main", 0);			
			plugin.fireBest("DialogOptionSelected");
			ui.updateUIForItem(this);
		}
		else if (buttonId == BUTTON_RECREATE) {
			disband();
			ui.updateUIForItem(this);
		}
		else if (buttonId == BUTTON_INDEPENDENT_MODE) {
			independentMode = !independentMode;
		}
		else {
			super.buttonPressConfirmed(buttonId, ui); //To change body of generated methods, choose Tools | Templates.
		}
		
	}
	
	public static int getActiveIntelCount() {
		int num = 0;
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(PlayerSpecialForcesIntel.class)) {
			if (intel.isEnding() || intel.isEnded()) continue;
			num++;
		}
		return num;
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
		Global.getSector().getListenerManager().removeListener(this);
		sendUpdateIfPlayerHasIntel(DESTROYED_UPDATE, false, false);
	}
	

	@Override
	public void reportEconomyTick(int iterIndex) {
		processCosts();
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
	
	public static int getReviveCost(List<FleetMemberAPI> members) {
		float cost = 0;
		for (FleetMemberAPI member : members) {
			cost += member.getBaseValue();
		}
		return Math.round(cost * REVIVE_COST_MULT);
	}
}
