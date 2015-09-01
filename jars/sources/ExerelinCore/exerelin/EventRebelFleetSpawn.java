package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFleet;

import java.util.List;

@Deprecated
public class EventRebelFleetSpawn extends EventBase
{

	public EventRebelFleetSpawn()
	{
		setType(this.getClass().getName());
	}

	public void spawnRebelFleet(StarSystemAPI starSystemAPI)
	{
        // Get count of current rebel fleets in system
        int rebelFleetCount = 0;
        List fleets = starSystemAPI.getFleets();
		for(int i = 0; i < fleets.size(); i++)
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);
            if(fleet.getFaction().getId().equalsIgnoreCase("rebel"))
                rebelFleetCount++;
		}

        if(rebelFleetCount > fleets.size() / 4)
            return;

        String factionLeaderId = SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getLeadingFactionId();
        if(factionLeaderId == null || factionLeaderId.equalsIgnoreCase(""))
            return;

        CampaignFleetAPI newRebelFleet;

        if(!ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFaction.equalsIgnoreCase(""))
        {
            String rebelId = ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFaction;
            String customFleetId = ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFleetId;

            newRebelFleet = Global.getSector().createFleet(rebelId, customFleetId);
        }
        else
        {
            newRebelFleet = ExerelinUtilsFleet.createFleetForFaction(factionLeaderId, ExerelinUtilsFleet.ExerelinFleetType.WAR, ExerelinUtilsFleet.ExerelinFleetSize.SMALL);
            ExerelinUtilsFleet.addFreightersToFleet(newRebelFleet);
            newRebelFleet.setFaction("rebel");
        }

        SectorEntityToken planet = (SectorEntityToken)ExerelinUtils.getRandomListElement(starSystemAPI.getPlanets());

        ExerelinUtilsFleet.sortByHullSize(newRebelFleet);
        ExerelinUtilsFleet.resetFleetCargoToDefaults(newRebelFleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);

        newRebelFleet.setName(ExerelinConfig.getExerelinFactionConfig(factionLeaderId).rebelFleetSuffix);

        newRebelFleet.getCommander().setPersonality("fearless");

        newRebelFleet.addAssignment(FleetAssignment.ATTACK_LOCATION, ExerelinUtils.getRandomStationInSystemForFaction(factionLeaderId, starSystemAPI), 90);
        newRebelFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, planet, 60);

        newRebelFleet.setPreferredResupplyLocation(planet);

        //starSystemAPI.spawnFleet(planet, 0, 0, newRebelFleet);
        SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(planet, 0, 0, newRebelFleet));
        //System.out.println("EVENT: Spawned rebel fleet in " + starSystemAPI.getName());
	}
}






