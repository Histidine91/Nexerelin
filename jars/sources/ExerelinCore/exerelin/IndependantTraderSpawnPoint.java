package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.ExerelinUtilsFleet;

@SuppressWarnings("unchecked")
public class IndependantTraderSpawnPoint extends BaseSpawnPoint
{
	CampaignFleetAPI theFleet;
	String fromStationFactionId = "";
	StationRecord toStation;
	Boolean trading = false;

	public IndependantTraderSpawnPoint(SectorAPI sector, LocationAPI location,
                                       float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	@Override
	public CampaignFleetAPI spawnFleet() {
		String type = "exerelinInSystemSupplyConvoy";

		String factions[] = SectorManager.getCurrentSectorManager().getFactionsInSector();

        if(factions.length == 0)
            return null;

		String factionShips = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];

		fromStationFactionId = "";
		this.setStationToTradeWith();

		if(toStation == null)
			return null;

		fromStationFactionId = toStation.getOwner().getFactionId();

		// Create fleet
		CampaignFleetAPI fleet = ExerelinUtilsFleet.createFleetForFaction(factionShips, ExerelinUtilsFleet.ExerelinFleetType.LOGISTICS, ExerelinUtilsFleet.ExerelinFleetSize.SMALL);
        ExerelinUtilsFleet.resetFleetCargoToDefaults(fleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);

		fleet.setFaction("independent");
		fleet.setName("Trader");
		theFleet = fleet;
		getLocation().spawnFleet(ExerelinUtils.getRandomOffMapPoint(getLocation()), 0, 0, fleet);

		fleet.setPreferredResupplyLocation(toStation.getStationToken());

		trading = false;
		setFleetAssignments(fleet);

		return fleet;
	}

	// Get a random station in random to trade with (not at war, not abandoned, not same one, not under attack)
	private void setStationToTradeWith()
	{
        SystemManager[] systemManagers = SectorManager.getCurrentSectorManager().getSystemManagers();

		StationRecord station = null;
		int attempts = 0;
		while(station == null && attempts < 20)
		{
            SystemStationManager systemStationManager = systemManagers[ExerelinUtils.getRandomInRange(0, systemManagers.length - 1)].getSystemStationManager();
            StationRecord[] stations = systemStationManager.getStationRecords();
            station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];

			attempts++;
			if(station.getOwner() == null
					|| station.getOwner().getFactionAPI().getRelationship("independent") < 0
					|| (toStation != null && station.getStationToken().getFullName().equalsIgnoreCase(toStation.getStationToken().getFullName()))
					|| station.getNumAttacking() > 0)
            {
				station = null;
            }
		}

		toStation = station;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		if(toStation != null)
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, toStation.getStationToken(), 1000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, ExerelinUtils.getRandomOffMapPoint(getLocation()), 1000);
		}
		else
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, ExerelinUtils.getRandomOffMapPoint(getLocation()), 1000);
		}
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(toStation.getOwner() != null && toStation.getOwner().getFactionAPI().getRelationship("independent") >= 0)
				{
					if(!trading || ExerelinUtils.getRandomInRange(0,400) > 0)
					{
						// Hover in place for a bit
						trading = true;
						setFleetAssignments(theFleet); // keep coming here
					}
					else if(trading)
					{
						// Deliver resources and leave
						CargoAPI cargo = toStation.getStationToken().getCargo();
						cargo.addFuel(50); // Halved due to mining exerelin.fleets
						cargo.addSupplies(200) ; // Halved due to mining exerelin.fleets
						cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 100);
						cargo.addMarines(50);

                        String factionShips = fromStationFactionId;
                        Boolean includeLargeShips = false;
                        if(ExerelinUtils.getRandomInRange(0, 1) == 1 && ExerelinUtils.isToreUpPlentyInstalled())
                        {
                            factionShips = "scavengers";
                            includeLargeShips = true;
                        }

                        ExerelinUtilsCargo.addFactionVariantsToCargo(cargo, factionShips, 1, includeLargeShips);
                        ExerelinUtilsCargo.addFactionWeaponsToCargo(cargo, factionShips, 2, 2);

						// Finish trading
						trading = false;
						fromStationFactionId = toStation.getOwner().getFactionId();

						setStationToTradeWith();
						setFleetAssignments(theFleet);
                        ExerelinUtilsFleet.resetFleetCargoToDefaults(theFleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);
					}
				}
				else
				{
					// Return home
					setStationToTradeWith();
					setFleetAssignments(theFleet);
				}
			}
		};
	}
}






