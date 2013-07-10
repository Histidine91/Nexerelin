package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class EventManager
{
	private EventRebelInsurrection eventRebelInsurrection;
	private EventTradeGuildConversion eventTradeGuildConversion;
	private EventOutSystemReinforcements eventOutSystemReinforcements;
	private EventStationExplosion eventStationExplosion;
	private EventStationSeccession eventStationSeccession;

	private int waitTime = 60; // Wait two months before running first events
	private String lastEventType = "";
	private int betweenEventWait = 5;

	public EventManager(SectorAPI sector, StarSystemAPI system)
	{
		eventRebelInsurrection = new EventRebelInsurrection(sector, system);
		eventTradeGuildConversion = new EventTradeGuildConversion(sector, system);
		eventOutSystemReinforcements = new EventOutSystemReinforcements(sector, system);
		eventStationExplosion = new EventStationExplosion();
		eventStationSeccession = new EventStationSeccession(sector,  system);
	}

	public void runEvents()
	{
		if(waitTime > 0)
		{
			waitTime--;
			return;
		}

		if(ExerelinUtils.getRandomInRange(0, 30) == 0
				&& !eventTradeGuildConversion.getType().equalsIgnoreCase(lastEventType))
		{
			eventTradeGuildConversion.callTradersForLastFaction();
			waitTime = betweenEventWait;
			lastEventType = eventTradeGuildConversion.getType();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,45) == 0
				&& !eventRebelInsurrection.getType().equalsIgnoreCase(lastEventType))
		{
			eventRebelInsurrection.causeRebellionAgainstLeadingFaction();
			waitTime = betweenEventWait;
			lastEventType = eventRebelInsurrection.getType();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0, 45) == 0
				&& !eventOutSystemReinforcements.getType().equalsIgnoreCase(lastEventType))
		{
			eventOutSystemReinforcements.callReinforcementFleets();
			waitTime = betweenEventWait;
			lastEventType = eventOutSystemReinforcements.getType();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,40) == 0
				&& !eventStationExplosion.getType().equalsIgnoreCase(lastEventType))
		{
			eventStationExplosion.causeExplosion();
			waitTime = betweenEventWait;
			lastEventType = eventStationExplosion.getType();
			return;
		}

		if(ExerelinData.getInstance().systemManager.respawnFactions
				&& ExerelinUtils.getRandomInRange(0,55) == 0
				&& !eventStationSeccession.getType().equalsIgnoreCase(lastEventType))
		{
			eventStationSeccession.makeStationSecedeToOutSystemFaction();
			waitTime = betweenEventWait;
			lastEventType = eventStationSeccession.getType();
			return;
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
}
