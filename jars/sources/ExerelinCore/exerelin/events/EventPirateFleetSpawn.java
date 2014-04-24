package exerelin.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import exerelin.EventBase;
import exerelin.ExerelinUtils;
import exerelin.SectorManager;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFleet;

import java.util.List;

public class EventPirateFleetSpawn extends EventBase
{
	public EventPirateFleetSpawn()
	{
		setType(this.getClass().getName());
	}

	public void spawnPirateFleet(StarSystemAPI starSystemAPI)
	{
        // Get count of current rebel fleets in system
        int pirateFleetCount = 0;
        List fleets = starSystemAPI.getFleets();
		for(int i = 0; i < fleets.size(); i++)
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);
            if(fleet.getFaction().getId().equalsIgnoreCase("pirate"))
                pirateFleetCount++;
		}

        if(pirateFleetCount > fleets.size() / 3)
            return;

        String[] factions = SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getFactionsInSystem();

        if(factions.length == 0)
        {
            if(ExerelinUtils.isToreUpPlentyInstalled())
                factions = new String[] {"scavengers"};
            else
                return;
        }

        if(ExerelinUtils.isToreUpPlentyInstalled() && ExerelinUtils.getRandomInRange(1, 3) == 1)
            factions = new String[] {"scavengers"};

        CampaignFleetAPI newPirateFleet = ExerelinUtilsFleet.createPirateFleet(factions, ExerelinUtils.getRandomInRange(1, 3));
        ExerelinUtilsFleet.sortByHullSize(newPirateFleet);
        ExerelinUtilsFleet.resetFleetCargoToDefaults(newPirateFleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);
        newPirateFleet.setName("Raiders");
        newPirateFleet.getCommander().setPersonality("fearless");

        SectorEntityToken spawnToken = null;
        if(ExerelinUtils.getRandomInRange(1, 4) == 1)
        {
            for(PlanetAPI planet : starSystemAPI.getPlanets())
            {
                if(planet.isGasGiant() || planet.getFullName().contains("Gaseous"))
                {
                    spawnToken = planet;
                    break;
                }
            }
        }

        if(spawnToken == null)
        {
            spawnToken = (SectorEntityToken)starSystemAPI.getAsteroids().get(ExerelinUtils.getRandomInRange(0, starSystemAPI.getAsteroids().size() - 1));
        }

        newPirateFleet.setPreferredResupplyLocation(spawnToken);

        newPirateFleet.addAssignment(FleetAssignment.DEFEND_LOCATION, spawnToken, 90);
        newPirateFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, spawnToken, 60);

        SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(spawnToken, 0, 0, newPirateFleet));
        //System.out.println("EVENT: Spawned pirate fleet in " + starSystemAPI.getName());
	}
}






