package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.BUTTON_COMMAND;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.BUTTON_DEBUG;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.BUTTON_RENAME;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.FLEET_TYPE;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.getString;
import exerelin.utilities.StringHelper;
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
	
	@Setter protected CampaignFleetAPI tempFleet;
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
	}
			
	public void setFlagship(FleetMemberAPI member) {
		flagship = member;
		if (route != null && route.getActiveFleet() != null) {
			route.getActiveFleet().getFleetData().setFlagship(flagship);
		}
	}
	
	@Override
	public void setCommander(PersonAPI commander) {
		super.setCommander(commander);
		commander.setRankId(Ranks.SPACE_CAPTAIN);
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
		
		tempFleet = null;
		
		return fleet;
	}
	
	public float getMonthsSuppliesRemaining() {
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet == null) return 0;
		
		int supplies = (int)fleet.getCargo().getSupplies();
		supplies -= fleet.getLogistics().getTotalRepairAndRecoverySupplyCost();
		
		float suppliesPerDay = fleet.getLogistics().getShipMaintenanceSupplyCost();
		return (supplies/suppliesPerDay/30);
	}
	
	public float getFuelFractionRemaining() {
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet == null) return 0;
		
		int fuel = (int)fleet.getCargo().getFuel();
		return fuel/fleet.getCargo().getMaxFuel();
	}
		
	public void buySupplies(int wanted, MarketAPI market) {
		
	}
	
	public void buyFuel(int wanted, MarketAPI market) {
		
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
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
		updateFuelUse();
	}
	
	protected void updateFuelUse() {
		CampaignFleetAPI fleet = route.getActiveFleet();
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
		
		CampaignFleetAPI fleet = route.getActiveFleet();
				
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
		
		FDNode psfNode = processMonthlyReportNode(report, fleetNode, "nex_node_id_psf", 
				getString("intelTitle"), faction.getCrest(), 0, false);
		
		float commanderFee = Global.getSettings().getFloat("officerSalaryBase") * 5;
		FDNode feeNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_commFee", 
				"[temp] Commander's fee", commander.getPortraitSprite(), commanderFee * f, false);
		
		if (fleet == null) return;
		
		float crew = fleet.getFleetData().getMinCrew();
		float crewSalary = crew * Global.getSettings().getInt("crewSalary") * 1.25f;
		FDNode crewNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_crewSal", 
				"[temp] Crew payroll", commander.getPortraitSprite(), crewSalary * f, false);
		
		float suppMaint = fleet.getLogistics().getShipMaintenanceSupplyCost();
		CommoditySpecAPI suppliesSpec = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES);
		float maintCost = suppMaint * suppliesSpec.getBasePrice() * 1.25f;
		FDNode maintNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_suppliesCost", 
				"[temp] Supply cost", suppliesSpec.getIconName(), maintCost * f, false);
		
		CommoditySpecAPI fuelSpec = Global.getSettings().getCommoditySpec(Commodities.FUEL);
		float fuelCost = this.fuelUsedLastInterval;
		FDNode fuelNode = processMonthlyReportNode(report, psfNode, "nex_node_id_psf_fuelCost", 
				"[temp] Fuel cost", fuelSpec.getIconName(), fuelCost * f, false);
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
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		createSmallDescriptionPart1(info, width);
		
		// TODO: list dead members
		
		createSmallDescriptionPart2(info, width);
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
	public void reportEconomyTick(int iterIndex) {
		
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
}
