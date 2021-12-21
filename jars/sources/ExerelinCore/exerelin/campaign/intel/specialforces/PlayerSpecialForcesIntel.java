package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.specialforces.SpecialForcesIntel.FLEET_TYPE;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public class PlayerSpecialForcesIntel extends SpecialForcesIntel implements EconomyTickListener {
	
	// for now
	// actually never turn this on? when we invoke combat costs this becomes a mess overall
	// on the other hand, should combat actually be free?
	// can't be helped, it _is_ free if far-autoresolved
	public static final boolean AI_MODE = true;
	
	@Setter protected CampaignFleetAPI tempFleet;
	@Getter protected Set<FleetMemberAPI> deadMembers = new LinkedHashSet<>();
	
	@Getter	protected boolean independentMode = true;
	protected boolean isAlive;
	
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
