package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;
import data.scripts.world.exerelin.ExerelinData;
import data.scripts.world.exerelin.ExerelinUtils;

@SuppressWarnings("unchecked")
public class DefenseFleetSpawnPoint extends BaseSpawnPoint
{
	String owningFactionId;
	SectorEntityToken defendStation;

	public DefenseFleetSpawnPoint(SectorAPI sector, LocationAPI location,
								 float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
		defendStation = anchor;
	}

	public void setFaction(String factionId)
	{
		owningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	public void setDefendStation(SectorEntityToken station)
	{
		defendStation = station;
		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
	}

	@Override
	public CampaignFleetAPI spawnFleet()
	{
		String type = "exerelinGenericFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);

		int remainingFleetsToSpawn = this.getMaxFleets()*2 - this.getFleets().size();
		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, remainingFleetsToSpawn, 0.5f, true))
		{
            // If faction is last, 5% chance to add a free elite ship to fleet
            if(SectorManager.getCurrentSectorManager().getLosingFaction().equalsIgnoreCase(this.owningFactionId) && ExerelinUtils.getRandomInRange(0, 19) == 0)
                ExerelinUtils.addEliteShipToFleet(fleet);

			getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
			fleet.setPreferredResupplyLocation(getAnchor());

			setFleetAssignments(fleet);

			ExerelinUtils.renameFleet(fleet, "defense");
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
		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation, 30);
		fleet.addAssignment(FleetAssignment.RESUPPLY, getAnchor(), 30);
		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation, 30);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 30);
	}
}






