package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import exerelin.events.EventPirateFleetSpawn;
import exerelin.utilities.ExerelinConfig;

public class SystemEventManager
{
	private EventRebelInsurrection eventRebelInsurrection;
	private EventRebelFleetSpawn eventRebelFleetSpawn;
	private EventStationExplosion eventStationExplosion;
	private EventStationSeccession eventStationSeccession;
    private EventPirateFleetSpawn eventPirateFleetSpawn;

	private int waitTime = 30; // Wait 1 month before running first events
	private String lastEventType = "";
	private int betweenEventWait = 10;

	private StarSystemAPI starSystemAPI;

	public SystemEventManager(StarSystemAPI inSystem)
	{
		starSystemAPI = inSystem;
		eventRebelInsurrection = new EventRebelInsurrection();
		eventRebelFleetSpawn = new EventRebelFleetSpawn();
		eventStationExplosion = new EventStationExplosion();
		eventStationSeccession = new EventStationSeccession();
        eventPirateFleetSpawn = new EventPirateFleetSpawn();
	}

	public void runEvents()
	{
        // Spawn rebel and pirate in system
        if(ExerelinUtils.getRandomInRange(0, 5) == 0)
        {
            if(ExerelinConfig.enableThreading)
                {
                Thread spawnEventFleetThread = new Thread("spawnEventFleetThread"){
                    public void run()
                    {
                        eventPirateFleetSpawn.spawnPirateFleet(starSystemAPI, false);

                        if(ExerelinUtils.getRandomInRange(0, 1) == 0)
                            eventRebelFleetSpawn.spawnRebelFleet(starSystemAPI);
                    }
                };

                spawnEventFleetThread.start();
            }
            else
            {
                eventPirateFleetSpawn.spawnPirateFleet(starSystemAPI, false);

                if(ExerelinUtils.getRandomInRange(0, 1) == 0)
                    eventRebelFleetSpawn.spawnRebelFleet(starSystemAPI);
            }
            //waitTime = betweenEventWait;
            //lastEventType = eventRebelFleetSpawn.getType();
            return;
        }

		if(waitTime > 0)
		{
			waitTime--;
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,40) == 0
				&& !eventRebelInsurrection.getType().equalsIgnoreCase(lastEventType))
		{
			eventRebelInsurrection.causeRebellionAgainstLeadingFaction(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventRebelInsurrection.getType();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,40) == 0
				&& !eventStationExplosion.getType().equalsIgnoreCase(lastEventType))
		{
			//eventStationExplosion.causeExplosion(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventStationExplosion.getType();
		}

		/*if(SectorManager.getCurrentSectorManager().getRespawnFactions()
				&& ExerelinUtils.getRandomInRange(0,55) == 0
				&& !eventStationSeccession.getType().equalsIgnoreCase(lastEventType)
                && Global.getSector().getClock().getElapsedDaysSince(SectorManager.getCurrentSectorManager().getLastFactionSpawnTime()) > SectorManager.getCurrentSectorManager().getRespawnWaitDays())
		{
			eventStationSeccession.makeStationSecedeToOutSystemFaction(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventStationSeccession.getType();
			return;
		}*/
	}

	public void setBetweenEventWait(int value)
	{
		betweenEventWait = value;
	}

	public void setWaitTime(int value)
	{
		waitTime = value;
	}

	// Set the starSystem to run events in
	public void setStarSystemAPI(StarSystemAPI inSystem)
	{
		starSystemAPI = inSystem;
	}
}
