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

		CargoAPI stationCargo = getAnchor().getCargo();

		// Check cargo and set convoy type
		float suppliesNormalised = 0;
		float fuelNormalised = 0;
		float crewNormalised = 0;
		float marineNormalised = 0;

		if(stationCargo.getSupplies() >= 1600)
			suppliesNormalised = stationCargo.getSupplies()/8;
		if(stationCargo.getFuel() >= 400)
			fuelNormalised = stationCargo.getFuel()/2;
		if(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) >= 400)
			crewNormalised = stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR)/2;
		if(stationCargo.getMarines() >= 200)
			marineNormalised = stationCargo.getMarines();

		if(suppliesNormalised == 0 && fuelNormalised == 0 && crewNormalised == 0 && marineNormalised == 0)
			return null;

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

		// Create fleet
		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);

	    DiplomacyRecord diplomacyRecord = ExerelinData.getInstance().getSectorManager().getDiplomacyManager().getRecordForFaction(owningFactionId);
	    if (diplomacyRecord.hasWarTargetInSystem((StarSystemAPI)getLocation(), false))
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 3, 4, owningFactionId, getSector());
	    else
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, owningFactionId, getSector());

		theFleet = fleet;
		getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
		fleet.setPreferredResupplyLocation(getAnchor());
		fleet.setName("In-System Supply Convoy");

		// Remove cargo from station
		if(convoyType.equalsIgnoreCase("fuel"))
			ExerelinUtils.decreaseCargo(stationCargo, "fuel", 200);
		else if(convoyType.equalsIgnoreCase("supplies"))
			ExerelinUtils.decreaseCargo(stationCargo, "supplies", 800);
		else if(convoyType.equalsIgnoreCase("crew"))
			ExerelinUtils.decreaseCargo(stationCargo, "crewRegular", 200);
		else if(convoyType.equalsIgnoreCase("marines"))
			ExerelinUtils.decreaseCargo(stationCargo, "marines", 100);

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
						cargo.addFuel(200);
					else if(convoyType.equalsIgnoreCase("supplies"))
						cargo.addSupplies(800) ;
					else if(convoyType.equalsIgnoreCase("crew"))
						cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
					else if(convoyType.equalsIgnoreCase("marines"))
						cargo.addMarines(100) ;

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






