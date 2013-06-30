package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;


@SuppressWarnings("unchecked")
public class AttackFleetSpawnPoint extends BaseSpawnPoint
{
	StationRecord stationTarget;
	CampaignFleetAPI theFleet;
	String ownerFactionId;

	public AttackFleetSpawnPoint(SectorAPI sector, LocationAPI location,
								 float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	public void setTarget(StationRecord target, StationRecord secondaryAssist)
	{
		if(target != null && stationTarget != null && stationTarget.getStationToken().getFullName().equalsIgnoreCase(target.getStationToken().getFullName()))
			return; // No change

		// If no proper target and station we are assisting has a good target, use that
		if((target == null || target.getOwner() == null)
				&& secondaryAssist != null
				&& secondaryAssist.getTargetStationRecord() != null
				&& secondaryAssist.getTargetStationRecord().getOwner() != null
				&& !secondaryAssist.getTargetStationRecord().getOwner().getFactionId().equalsIgnoreCase(ownerFactionId)
				&& secondaryAssist.getOwner().getGameRelationship(ownerFactionId) < 0)
			stationTarget = secondaryAssist.getTargetStationRecord();
		else
			stationTarget = target;

		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));

	}

	public void setFaction(String factionId)
	{
		ownerFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	@Override
	public CampaignFleetAPI spawnFleet()
	{
		String type = "exerelinGenericFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		if(stationTarget == null || stationTarget.getOwner() == null)
			return null;

		CampaignFleetAPI fleet = getSector().createFleet(ownerFactionId, type);

		theFleet = fleet;

		int remainingFleetsToSpawn = this.getMaxFleets()*2 - this.getFleets().size();
		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, remainingFleetsToSpawn))
		{
			getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
			fleet.setPreferredResupplyLocation(getAnchor());

			setFleetAssignments(fleet);

			ExerelinUtils.renameFleet(fleet, "attack");

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
		if(stationTarget == null)
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 90);
		}
		else
		{
			fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, stationTarget.getStationToken(), 90);
			fleet.addAssignment(FleetAssignment.RESUPPLY, stationTarget.getStationToken(), 90);
			fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, stationTarget.getStationToken(), 90);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 90);
		}
	}
}






