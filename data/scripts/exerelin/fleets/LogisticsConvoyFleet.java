package data.scripts.exerelin.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import data.scripts.world.exerelin.ExerelinUtils;
import data.scripts.world.exerelin.utilities.ExerelinConfig;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFaction;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFleet;

@SuppressWarnings("unchecked")
public class LogisticsConvoyFleet extends ExerelinFleetBase
{
	String convoyType;
    SectorEntityToken anchor;
    SectorEntityToken target;

	public LogisticsConvoyFleet()
	{

	}

	public void setTarget(SectorEntityToken target)
	{
        this.target = target;
        setFleetAssignments();
	}

	public CampaignFleetAPI createFleet(String faction, SectorEntityToken anchor, SectorEntityToken target)
    {
		String type = "exerelinInSystemSupplyConvoy";

        setConvoyType();

		// Create fleet
		CampaignFleetAPI fleet = Global.getSector().createFleet(faction, type);

	    if (ExerelinUtilsFaction.getFactionsAtWarWithFaction(faction).size() > 0)
	      ExerelinUtils.addRandomEscortShipsToFleet(fleet, 3, 4, faction, Global.getSector());
	    else
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, faction, Global.getSector());

        ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.3f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(faction));
        ExerelinUtilsFleet.sortByHullSize(fleet);

		this.fleet = fleet;
		fleet.setPreferredResupplyLocation(anchor);
        fleet.getCommander().setPersonality("cautious");
		fleet.setName(ExerelinConfig.getExerelinFactionConfig(faction).logisticsFleetName);

		// Remove cargo from station
		if(convoyType.equalsIgnoreCase("fuel"))
			ExerelinUtils.decreaseCargo(anchor.getCargo(), "fuel", 800);
		else if(convoyType.equalsIgnoreCase("supplies"))
			ExerelinUtils.decreaseCargo(anchor.getCargo(), "supplies", 3200);
		else if(convoyType.equalsIgnoreCase("crew"))
			ExerelinUtils.decreaseCargo(anchor.getCargo(), "crewRegular", 800);
		else if(convoyType.equalsIgnoreCase("marines"))
			ExerelinUtils.decreaseCargo(anchor.getCargo(), "marines", 400);

		setFleetAssignments();

        ((StarSystemAPI)anchor.getContainingLocation()).spawnFleet(anchor, 0, 0, fleet);

		return fleet;
	}

	public void setFleetAssignments()
	{
		fleet.clearAssignments();
		if(this.target != null)
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, this.target, 1000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, this.target, 10);
		}
		else
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, this.anchor, 10);
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(target != null &&
                        (target.getFaction().getId().equalsIgnoreCase(fleet.getFaction().getId())
                        || Global.getSector().getFaction(fleet.getFaction().getId()).getRelationship(target.getFaction().getId()) >= 1))
				{
					// Deliver resources and despawn
					CargoAPI cargo = target.getCargo();

                    cargo.addFuel(400);
                    cargo.addSupplies(1600);
                    cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 400);
                    cargo.addMarines(200);

					if(convoyType.equalsIgnoreCase("fuel"))
						cargo.addFuel(400);
					else if(convoyType.equalsIgnoreCase("supplies"))
						cargo.addSupplies(1600);
					else if(convoyType.equalsIgnoreCase("crew"))
						cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 400);
					else if(convoyType.equalsIgnoreCase("marines"))
						cargo.addMarines(200);

                    ExerelinUtils.addWeaponsToCargo(cargo, 2, fleet.getFaction().getId(), Global.getSector());
                    ExerelinUtils.addRandomFactionShipsToCargo(cargo, 1, fleet.getFaction().getId(), Global.getSector());

				}
				else
				{
					// Return home
					fleet.clearAssignments();
					fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 30);
				}
			}
		};
	}

    private void setConvoyType()
    {
        CargoAPI stationCargo = anchor.getCargo();

        // Check cargo and set convoy type
        float suppliesNormalised = 0;
        float fuelNormalised = 0;
        float crewNormalised = 0;
        float marineNormalised = 0;

        if(stationCargo.getSupplies() >= 3200)
            suppliesNormalised = stationCargo.getSupplies()/8;
        if(stationCargo.getFuel() >= 800)
            fuelNormalised = stationCargo.getFuel()/2;
        if(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) >= 800)
            crewNormalised = stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR)/2;
        if(stationCargo.getMarines() >= 400)
            marineNormalised = stationCargo.getMarines();

        if(suppliesNormalised >= fuelNormalised && suppliesNormalised >= crewNormalised && suppliesNormalised >= marineNormalised)
            convoyType = "supplies";
        else if(fuelNormalised >= suppliesNormalised && fuelNormalised >= crewNormalised && fuelNormalised >= marineNormalised)
            convoyType = "fuel";
        else if(crewNormalised >= suppliesNormalised && crewNormalised >= fuelNormalised && crewNormalised >= marineNormalised)
            convoyType = "crew";
        else if(marineNormalised >= suppliesNormalised && marineNormalised >= fuelNormalised && marineNormalised >= crewNormalised)
            convoyType = "marines";
        else
            convoyType = "supplies";
    }
}






