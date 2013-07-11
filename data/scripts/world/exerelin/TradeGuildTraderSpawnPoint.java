package data.scripts.world.exerelin;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;

@SuppressWarnings("unchecked")
public class TradeGuildTraderSpawnPoint extends BaseSpawnPoint
{
	CampaignFleetAPI theFleet;
	String fromStationFactionId = "";
	StationRecord toStation;
	Boolean trading = false;

	public TradeGuildTraderSpawnPoint(SectorAPI sector, LocationAPI location,
									  float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	@Override
	public CampaignFleetAPI spawnFleet() {
		String type = "exerelinInSystemSupplyConvoy";

		String factions[] = ExerelinData.getInstance().getAvailableFactions(getSector());
		String factionShips = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];

		fromStationFactionId = "";
		this.setStationToTradeWith();

		if(toStation == null)
			return null;

		fromStationFactionId = toStation.getOwner().getFactionId();

		// Create fleet
		CampaignFleetAPI fleet = getSector().createFleet(factionShips, type);
		fleet.setFaction("tradeguild");
		fleet.setName("Trader");
		theFleet = fleet;
		getLocation().spawnFleet(ExerelinUtils.getRandomOffMapPoint(getLocation()), 0, 0, fleet);

		fleet.setPreferredResupplyLocation(getAnchor());

		trading = false;
		setFleetAssignments(fleet);

		return fleet;
	}

	// Get a station to trade with (not at war, not abandoned, not same one, not under attack)
	private void setStationToTradeWith()
	{
		StationRecord[] stations = ExerelinData.getInstance().systemManager.stationManager.getStationRecords();
		StationRecord station = null;
		int attempts = 0;
		while(station == null && attempts < 20)
		{
			attempts++;
			station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];
			if(station.getOwner() == null
					|| station.getOwner().getFactionAPI().getRelationship("tradeguild") < 0
					|| (toStation != null && station.getStationToken().getFullName().equalsIgnoreCase(toStation.getStationToken().getFullName()))
					|| station.getNumAttacking() > 0)
				station = null;
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
				if(toStation.getOwner() != null && toStation.getOwner().getFactionAPI().getRelationship("tradeguild") >= 0)
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
						cargo.addFuel(50); // Halved due to mining fleets
						cargo.addSupplies(200) ; // Halved due to mining fleets
						cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 100);
						cargo.addMarines(50);

						if(toStation.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
						{
							ExerelinUtils.addRandomFactionShipsToCargo(cargo, 1,  fromStationFactionId, getSector());
							ExerelinUtils.addWeaponsToCargo(cargo,  2, fromStationFactionId,  getSector());
						}

						// Finish trading
						trading = false;
						fromStationFactionId = toStation.getOwner().getFactionId();

						setStationToTradeWith();
						setFleetAssignments(theFleet);
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






