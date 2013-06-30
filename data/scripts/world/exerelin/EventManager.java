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

	private int waitTime = 60;

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
			return; // Wait two months before running events
		}

		if(ExerelinUtils.getRandomInRange(0, 30) == 0)
		{
			eventTradeGuildConversion.callTradersForLastFaction();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,45) == 0)
		{
			eventRebelInsurrection.causeRebellionAgainstLeadingFaction();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0, 45) == 0)
		{
			eventOutSystemReinforcements.callReinforcementFleets();
			return;
		}

		if(ExerelinUtils.getRandomInRange(0,40) == 0)
		{
			eventStationExplosion.causeExplosion();
			return;
		}

		if(ExerelinData.getInstance().respawnFactions && ExerelinUtils.getRandomInRange(0,55) == 0)
		{
			eventStationSeccession.makeStationSecedeToOutSystemFaction();
			return;
		}
	}
}
