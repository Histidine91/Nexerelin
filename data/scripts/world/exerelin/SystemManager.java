package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;

public class SystemManager
{
	private StationManager stationManager;
	private EventManager eventManager;

	private StarSystemAPI starSystemAPI;

	public SystemManager(SectorAPI sectorAPI, StarSystemAPI starSystemAPI)
	{
		this.starSystemAPI = starSystemAPI;

		stationManager = new StationManager(sectorAPI, this.starSystemAPI);
		eventManager = new EventManager(this.starSystemAPI);
	}

	public String getStarSystemName()
	{
		return starSystemAPI.getName();
	}

	public StarSystemAPI getStarSystemAPI()
	{
		return this.starSystemAPI;
	}

	public StationManager getStationManager()
	{
		return stationManager;
	}

	public EventManager getEventManager()
	{
		return eventManager;
	}

	public String getLeadingFactionId()
	{
		return stationManager.getFactionLeader();
	}

	public String getLosingFactionId()
	{
		return stationManager.getFactionLoser();
	}

	public Boolean isFactionInSystem(String factionId)
	{
		return stationManager.doesFactionOwnStation(factionId);
	}

	public Boolean isOneOfFactionInSystem(String[] factionIds)
	{
		for(int i = 0; i < factionIds.length; i++)
		{
			if(this.isFactionInSystem(factionIds[i]))
				return true;
		}

		return false;
	}

	public float getSystemOwnership(String factionId)
	{
		return stationManager.getStationOwnershipPercent(factionId);
	}

	public String[] getFactionsInSystem()
	{
		return stationManager.getFactionsOwningStations();
	}

	public static SystemManager getSystemManagerForSystem(String systemName)
	{
		return SectorManager.getCurrentSectorManager().getSystemManager(systemName);
	}

	public static SystemManager getSystemManagerForAPI(StarSystemAPI starSystemAPI)
	{
		return SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI);
	}
}