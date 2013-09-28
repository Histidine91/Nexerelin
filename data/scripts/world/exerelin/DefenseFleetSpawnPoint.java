package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.BaseSpawnPoint;
import data.scripts.world.exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFleet;

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
		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, remainingFleetsToSpawn, 0.2f, true, ExerelinUtils.getCrewXPLevelForFaction(this.owningFactionId)))
		{
            float eliteShipChance = 0.01f;

            // If faction is last, 5% chance to add a free elite ship to fleet
            if(SectorManager.getCurrentSectorManager().getLosingFaction().equalsIgnoreCase(this.owningFactionId))
                eliteShipChance = eliteShipChance + 0.05f;

            // Add player chance
            if(owningFactionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
                eliteShipChance = eliteShipChance + ExerelinUtilsPlayer.getPlayerFactionFleetEliteShipBonusChance();

            if(ExerelinUtils.getRandomInRange(0, (int)(99 / (eliteShipChance * 100))) == 0)
                ExerelinUtils.addEliteShipToFleet(fleet);

            ExerelinUtils.renameFleet(fleet, "defense");
            ExerelinUtils.addFreightersToFleet(fleet);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(this.owningFactionId));
            ExerelinUtilsFleet.fleetOrderReset(fleet);

			fleet.setPreferredResupplyLocation(getAnchor());

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
		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation, 1000);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
	}
}






