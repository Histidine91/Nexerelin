package exerelin.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import exerelin.ExerelinUtils;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;

@SuppressWarnings("unchecked")
public class LogisticsConvoyFleet extends ExerelinFleetBase
{
	String convoyType;
    SectorEntityToken anchor;
    SectorEntityToken target;

	public LogisticsConvoyFleet(String faction, SectorEntityToken anchor, SectorEntityToken target)
	{
        this.anchor = anchor;
        this.target = target;

        setConvoyType();

        // Create fleet
        this.fleet = ExerelinUtilsFleet.createFleetForFaction(faction, ExerelinUtilsFleet.ExerelinFleetType.LOGISTICS, null);

        if (ExerelinUtilsFaction.getFactionsAtWarWithFaction(faction, false).size() > 0)
            ExerelinUtilsFleet.addEscortsToFleet(this.fleet, 4);
        else
            ExerelinUtilsFleet.addEscortsToFleet(this.fleet, 1);
        ExerelinUtilsFleet.resetFleetCargoToDefaults(fleet, 0.3f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(faction));
        ExerelinUtilsFleet.sortByHullSize(fleet);

        this.fleet.setPreferredResupplyLocation(anchor);
        this.fleet.getCommander().setPersonality("cautious");

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

        ((StarSystemAPI)anchor.getContainingLocation()).spawnFleet(anchor, 0, 0, this.fleet);
	}

	public void setTarget(SectorEntityToken target)
	{
        if(this.target != null && target != null && this.target.getFullName().equalsIgnoreCase(target.getFullName()))
            return;

        this.target = target;
        setFleetAssignments();
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

                    cargo.addFuel(200);
                    cargo.addSupplies(800);
                    cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
                    cargo.addMarines(100);

					if(convoyType.equalsIgnoreCase("fuel"))
						cargo.addFuel(200);
					else if(convoyType.equalsIgnoreCase("supplies"))
						cargo.addSupplies(800);
					else if(convoyType.equalsIgnoreCase("crew"))
						cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
					else if(convoyType.equalsIgnoreCase("marines"))
						cargo.addMarines(100);

                    ExerelinUtilsCargo.addFactionVariantsToCargo(cargo, fleet.getFaction().getId(), 1, false);
                    ExerelinUtilsCargo.addFactionWeaponsToCargo(cargo, fleet.getFaction().getId(), 2, 2);

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
        CargoAPI stationCargo = this.anchor.getCargo();

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






