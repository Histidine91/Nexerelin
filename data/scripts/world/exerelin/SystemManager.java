package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.*;

public class SystemManager
{
	private SystemStationManager systemStationManager;
	private SystemEventManager systemEventManager;

	private StarSystemAPI starSystemAPI;

	public SystemManager(SectorAPI sectorAPI, StarSystemAPI starSystemAPI)
	{
		this.starSystemAPI = starSystemAPI;

		systemStationManager = new SystemStationManager(sectorAPI, this.starSystemAPI);
		systemEventManager = new SystemEventManager(this.starSystemAPI);
	}

	public String getStarSystemName()
	{
		return starSystemAPI.getName();
	}

	public StarSystemAPI getStarSystemAPI()
	{
		return this.starSystemAPI;
	}

	public SystemStationManager getSystemStationManager()
	{
		return systemStationManager;
	}

	public SystemEventManager getSystemEventManager()
	{
		return systemEventManager;
	}

	public String getLeadingFactionId()
	{
		return systemStationManager.getFactionLeader();
	}

	public String getLosingFactionId()
	{
		return systemStationManager.getFactionLoser();
	}

	public Boolean isFactionInSystem(String factionId)
	{
		return systemStationManager.doesFactionOwnStation(factionId);
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
		return systemStationManager.getStationOwnershipPercent(factionId);
	}

	public String[] getFactionsInSystem()
	{
		return systemStationManager.getFactionsOwningStations();
	}

    public SectorEntityToken getStationTokenForXY(float x, float y, float maxOffset)
    {
        return systemStationManager.getStationTokenForXY(x, y, maxOffset);
    }

    public void setStationOwner(SectorEntityToken station, String newOwnerFactionId, Boolean displayMessage, Boolean updateRelationship)
    {
        systemStationManager.setStationOwner(station, newOwnerFactionId, displayMessage, updateRelationship);
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