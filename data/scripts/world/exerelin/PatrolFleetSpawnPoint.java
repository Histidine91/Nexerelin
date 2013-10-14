package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;
import data.scripts.world.exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFleet;

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
				setWarAssignments((CampaignFleetAPI) this.getFleets().get(i));
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

		if(this.getFleets().size() == this.getMaxFleets() || (defendStation == null && this.getFleets().size() > 0))
			return null; // Only build if at war, or no patrol fleets deployed

		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);


		int remainingFleetsToSpawn = this.getMaxFleets()*2 - this.getFleets().size();
		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, remainingFleetsToSpawn, 0.2f, true, ExerelinUtils.getCrewXPLevelForFaction(this.owningFactionId)))
		{
            float eliteShipChance = 0.01f;

            // Add player chance
            if(owningFactionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
                eliteShipChance = eliteShipChance + ExerelinUtilsPlayer.getPlayerFactionFleetEliteShipBonusChance();

            if(ExerelinUtils.getRandomInRange(0, (int)(99 / (eliteShipChance * 100))) == 0)
                ExerelinUtils.addEliteShipToFleet(fleet);

            ExerelinUtils.renameFleet(fleet, "patrol");
            ExerelinUtils.addFreightersToFleet(fleet);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(this.owningFactionId));
            ExerelinUtilsFleet.sortByHullSize(fleet);

			fleet.setPreferredResupplyLocation(getAnchor());

			if(defendStation != null)
				setWarAssignments(fleet);
			else
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
		fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, getAnchor(), 1000);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
	}

	private void setWarAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();

        // Check if home is under threat
        Boolean homeUnderThreat = false;
		if(defendStation.getStationToken().getFullName().equalsIgnoreCase(getAnchor().getFullName()))
            homeUnderThreat = true;

        // Only allow raid system choice if home is not under threat
        int action;
        if(homeUnderThreat)
            action = 0; //ExerelinUtils.getRandomInRange(0,1);
        else
            action = ExerelinUtils.getRandomInRange(0,1); //ExerelinUtils.getRandomInRange(0,2);

        if(action == 0)
        {
            // Defend station
            fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation.getStationToken(), 1000);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 100);
            fleet.setPreferredResupplyLocation(getAnchor());
        }
        /*else if (action == 1 && defendStation.getTargetStationRecord() != null)
        {
            // Attack station
            fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, defendStation.getTargetStationRecord().getStationToken(), 1000);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
            fleet.setPreferredResupplyLocation(defendStation.getStationToken());
        }*/
        else if(action == 1 && defendStation.getTargetStationRecord() != null)
        {
            // Raid our system if war target there, else raid support stations target system
            if(ExerelinUtils.doesSystemHaveEntityForFaction((StarSystemAPI)this.getLocation(), this.owningFactionId, -100000, -0.01f))
                fleet.addAssignment(FleetAssignment.RAID_SYSTEM, getAnchor(), 1000);
            else
                fleet.addAssignment(FleetAssignment.RAID_SYSTEM, defendStation.getTargetStationRecord().getStationToken(), 1000);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
            fleet.setPreferredResupplyLocation(defendStation.getStationToken());
        }
	}
}






