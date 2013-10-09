package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;
import data.scripts.world.exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFleet;


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
                && secondaryAssist.getOwner() != null
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

		if(stationTarget == null || stationTarget.getOwner() == null || stationTarget.getOwner().getFactionId().equalsIgnoreCase(this.ownerFactionId))
			return null;

		CampaignFleetAPI fleet = getSector().createFleet(ownerFactionId, type);
		theFleet = fleet;


        float eliteShipChance = 0.01f;

        // If leading, 5% chance to add a elite ship to fleet
        if(SectorManager.getCurrentSectorManager().getLeadingFaction().equalsIgnoreCase(this.ownerFactionId))
            eliteShipChance = eliteShipChance + 0.05f;

        // Add player chance
        if(ownerFactionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
            eliteShipChance = eliteShipChance + ExerelinUtilsPlayer.getPlayerFactionFleetEliteShipBonusChance();

        if(ExerelinUtils.getRandomInRange(0, (int)(99 / (eliteShipChance * 100))) == 0)
            ExerelinUtils.addEliteShipToFleet(fleet);

		int remainingFleetsToSpawn = this.getMaxFleets()*2 - this.getFleets().size();
		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, remainingFleetsToSpawn, 0.1f, true, ExerelinUtils.getCrewXPLevelForFaction(this.ownerFactionId)))
		{
            ExerelinUtils.renameFleet(fleet, "attack");
            ExerelinUtils.addFreightersToFleet(fleet);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(this.ownerFactionId));
            ExerelinUtilsFleet.sortByHullSize(fleet);

            if(((StarSystemAPI)stationTarget.getStationToken().getContainingLocation()).getName().equalsIgnoreCase(((StarSystemAPI)getAnchor().getContainingLocation()).getName()) || FactionDirector.getFactionDirectorForFactionId(this.ownerFactionId).getTargetResupplyEntityToken() == null)
			    fleet.setPreferredResupplyLocation(getAnchor());
            else
                fleet.setPreferredResupplyLocation(FactionDirector.getFactionDirectorForFactionId(this.ownerFactionId).getTargetResupplyEntityToken());

			setFleetAssignments(fleet);

            this.getFleets().add(fleet);

            //getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(getAnchor(), 0, 0, fleet));
			//return fleet;
            return null;
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
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
		}
		else
		{
			fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, stationTarget.getStationToken(), 1000);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);

            if(((StarSystemAPI)stationTarget.getStationToken().getContainingLocation()).getName().equalsIgnoreCase(((StarSystemAPI)getAnchor().getContainingLocation()).getName())
                    || FactionDirector.getFactionDirectorForFactionId(fleet.getFaction().getId()).getTargetResupplyEntityToken() == null)
                fleet.setPreferredResupplyLocation(getAnchor());
            else
                fleet.setPreferredResupplyLocation(FactionDirector.getFactionDirectorForFactionId(fleet.getFaction().getId()).getTargetResupplyEntityToken());
		}
	}
}






