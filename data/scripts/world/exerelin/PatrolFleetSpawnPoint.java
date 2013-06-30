package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;

@SuppressWarnings("unchecked")
public class PatrolFleetSpawnPoint extends BaseSpawnPoint
{
	String owningFactionId;
	StationRecord defendStation;

	public PatrolFleetSpawnPoint(SectorAPI sector, LocationAPI location,
								 float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	public void setFaction(String factionId)
	{
		owningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	public void setDefendStation(StationRecord station)
	{
		defendStation = station;
		if(defendStation != null)
		{
			for(int i = 0; i < this.getFleets().size();i++)
				setDefendAssignments((CampaignFleetAPI)this.getFleets().get(i));
		}
		else
		{
			for(int i = 0; i < this.getFleets().size();i++)
				setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
		}
	}

	@Override
	public CampaignFleetAPI spawnFleet()
	{
		String type = "exerelinGenericFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);


		int remainingFleetsToSpawn = this.getMaxFleets()*2 - this.getFleets().size();
		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, remainingFleetsToSpawn))
		{
			getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
			fleet.setPreferredResupplyLocation(getAnchor());

			if(defendStation != null)
				setDefendAssignments(fleet);
			else
				setFleetAssignments(fleet);

			ExerelinUtils.renameFleet(fleet, "patrol");
			this.getFleets().add(fleet);
			return fleet;
		}
		else
		{
			return null;
		}
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, getAnchor(), 90);
		fleet.addAssignment(FleetAssignment.RESUPPLY, getAnchor(), 90);
		fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, getAnchor(), 90);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 90);
	}

	private void setDefendAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation.getStationToken(), 90);
		fleet.addAssignment(FleetAssignment.RESUPPLY, defendStation.getStationToken(), 90);
		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation.getStationToken() , 90);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 90);
	}
}






