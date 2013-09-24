package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.StarSystemAPI;

public class SystemEventManager
{
	private EventRebelInsurrection eventRebelInsurrection;
	private EventRebelFleetSpawn eventRebelFleetSpawn;
	private EventOutSystemReinforcements eventOutSystemReinforcements;
	private EventStationExplosion eventStationExplosion;
	private EventStationSeccession eventStationSeccession;
	private EventAddObjectToStorage eventAddObjectToStorage;

	private int waitTime = 30; // Wait 1 month before running first events
	private String lastEventType = "";
	private int betweenEventWait = 10;

	private StarSystemAPI starSystemAPI;

	public SystemEventManager(StarSystemAPI inSystem)
	{
		starSystemAPI = inSystem;
		eventRebelInsurrection = new EventRebelInsurrection();
		eventRebelFleetSpawn = new EventRebelFleetSpawn();
		eventOutSystemReinforcements = new EventOutSystemReinforcements();
		eventStationExplosion = new EventStationExplosion();
		eventStationSeccession = new EventStationSeccession();
		eventAddObjectToStorage = new EventAddObjectToStorage();
	}

	public void runEvents()
	{
		if(waitTime > 0)
		{
			waitTime--;
			return;
		}

		if(ExerelinUtils.getRandomInRange(0, 10) == 0
				&& !eventRebelFleetSpawn.getType().equalsIgnoreCase(lastEventType))
		{
			eventRebelFleetSpawn.spawnRebelFleet(starSystemAPI);
			//waitTime = betweenEventWait;
			//lastEventType = eventRebelFleetSpawn.getType();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,45) == 0
				&& !eventRebelInsurrection.getType().equalsIgnoreCase(lastEventType))
		{
			eventRebelInsurrection.causeRebellionAgainstLeadingFaction(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventRebelInsurrection.getType();
			return;
		}

		/*if(ExerelinUtils.getRandomInRange(0, 45) == 0
				&& !eventOutSystemReinforcements.getType().equalsIgnoreCase(lastEventType))
		{
			eventOutSystemReinforcements.callReinforcementFleets(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventOutSystemReinforcements.getType();
			return;
		}*/

		if(ExerelinUtils.getRandomInRange(0,40) == 0
				&& !eventStationExplosion.getType().equalsIgnoreCase(lastEventType))
		{
			eventStationExplosion.causeExplosion(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventStationExplosion.getType();
			return;
		}

		/*if(ExerelinData.getInstance().getSectorManager().getRespawnFactions()
				&& ExerelinUtils.getRandomInRange(0,55) == 0
				&& !eventStationSeccession.getType().equalsIgnoreCase(lastEventType)
                && Global.getSector().getClock().getElapsedDaysSince(SectorManager.getCurrentSectorManager().getLastFactionSpawnTime()) > SectorManager.getCurrentSectorManager().getRespawnWaitDays())
		{
			eventStationSeccession.makeStationSecedeToOutSystemFaction(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventStationSeccession.getType();
			return;
		}*/

        float extraChance = ExerelinUtilsPlayer.getPlayerDiplomacyObjectCreationBonus();

		if(ExerelinUtils.getRandomInRange(0,(45*(int)(1.0f - extraChance))) == 0
				&& !eventAddObjectToStorage.getType().equalsIgnoreCase(lastEventType))
		{
			eventAddObjectToStorage.addAgentToStorageFacility(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventAddObjectToStorage.getType();
		}

		if(ExerelinUtils.getRandomInRange(0,(60*(int)(1.0f - extraChance))) == 0
				&& !eventAddObjectToStorage.getType().equalsIgnoreCase(lastEventType))
		{
			eventAddObjectToStorage.addPrisonerToStorageFacility(starSystemAPI);
			waitTime = betweenEventWait;
			lastEventType = eventAddObjectToStorage.getType();
		}

        if(ExerelinUtils.getRandomInRange(0,(60*(int)(1.0f - extraChance))) == 0
                && !eventAddObjectToStorage.getType().equalsIgnoreCase(lastEventType)
                && ExerelinUtilsPlayer.getPlayerSabateurAvailability())
        {
            eventAddObjectToStorage.addSabateurToStorageFacility(starSystemAPI);
            waitTime = betweenEventWait;
            lastEventType = eventAddObjectToStorage.getType();
        }
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
