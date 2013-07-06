package data.scripts.world.exerelin;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import data.scripts.world.BaseSpawnPoint;

@SuppressWarnings("unchecked")
public class InSystemSupplyConvoySpawnPoint extends BaseSpawnPoint
{
	String owningFactionId;
	String convoyType;
	LocationAPI theLocation;
	CampaignFleetAPI theFleet;
	StationRecord friendlyStation;

	public InSystemSupplyConvoySpawnPoint(SectorAPI sector, LocationAPI location,
										  float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
		theLocation = location;
	}

	public void setFaction(String factionId)
	{
		owningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	public void setFriendlyStation(StationRecord station)
	{
		if(station != null && friendlyStation != null && station.getStationToken().getFullName().equalsIgnoreCase(friendlyStation.getStationToken().getFullName()))
			return; // No change

		friendlyStation = station;
		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
	}

	@Override
	public CampaignFleetAPI spawnFleet() {
		String type = "exerelinInSystemSupplyConvoy";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		if(friendlyStation == null)
			return null;

		// Check cargo and set convoy type
		CargoAPI stationCargo = getAnchor().getCargo();
		if(stationCargo.getFuel() >= 400)
			convoyType = "fuel";
		else if(stationCargo.getSupplies() >= 1600)
			convoyType = "supplies";
		else if(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) >= 400)
			convoyType = "crew";
		else if(stationCargo.getMarines() >= 200)
			convoyType = "marines";
		else
			return null;

		// Create fleet
		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);

	    DiplomacyRecord diplomacyRecord = ExerelinData.getInstance().systemManager.diplomacyManager.getRecordForFaction(owningFactionId);
	    if (diplomacyRecord.hasWarTargetInSystem(false))
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 2, 3, owningFactionId, getSector());
	    else
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, owningFactionId, getSector());

		theFleet = fleet;
		getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
		fleet.setPreferredResupplyLocation(getAnchor());
		fleet.setName("In-System Supply Convoy");

		// Remove cargo from station
		if(convoyType.equalsIgnoreCase("fuel"))
			ExerelinUtils.decreaseCargo(stationCargo, "fuel", 100);
		else if(convoyType.equalsIgnoreCase("supplies"))
			ExerelinUtils.decreaseCargo(stationCargo, "supplies", 400);
		else if(convoyType.equalsIgnoreCase("crew"))
			ExerelinUtils.decreaseCargo(stationCargo, "crewRegular", 100);
		else if(convoyType.equalsIgnoreCase("marines"))
			ExerelinUtils.decreaseCargo(stationCargo, "marines", 50);

		setFleetAssignments(fleet);

		this.getFleets().add(fleet);
		return fleet;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		if(friendlyStation != null && friendlyStation.getOwner() != null)
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, friendlyStation.getStationToken(), 1000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, friendlyStation.getStationToken(), 10);
		}
		else
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(friendlyStation != null && friendlyStation.getOwner() != null && (friendlyStation.getOwner().getFactionId().equalsIgnoreCase(owningFactionId) || friendlyStation.getOwner().getGameRelationship(owningFactionId) >= 1))
				{
					// Deliver resources and despawn
					CargoAPI cargo = friendlyStation.getStationToken().getCargo();
					if(convoyType.equalsIgnoreCase("fuel"))
						cargo.addFuel(100);
					else if(convoyType.equalsIgnoreCase("supplies"))
						cargo.addSupplies(400) ;
					else if(convoyType.equalsIgnoreCase("crew"))
						cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 100);
					else if(convoyType.equalsIgnoreCase("marines"))
						cargo.addMarines(50) ;

					if(friendlyStation.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					{
						ExerelinUtils.addWeaponsToCargo(cargo, 2, owningFactionId, getSector());
						ExerelinUtils.addRandomFactionShipsToCargo(cargo, 1, owningFactionId, getSector());
					}
				}
				else
				{
					// Return home
					theFleet.clearAssignments();
					theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 30);
				}
			}
		};
	}
}






