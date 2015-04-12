package exerelin;

import com.fs.starfarer.api.campaign.*;

import java.awt.*;
import java.util.*;

@Deprecated
public class SystemManager
{
	private SystemStationManager systemStationManager;
	private SystemEventManager systemEventManager;

	private StarSystemAPI starSystemAPI;

    private String originalBackgroundImage;
    private String originalStarSpec;
    private Color originalLightColor;

	public SystemManager(StarSystemAPI starSystemAPI)
	{
		this.starSystemAPI = starSystemAPI;

		systemStationManager = new SystemStationManager(this.starSystemAPI);
		systemEventManager = new SystemEventManager(this.starSystemAPI);

        this.originalBackgroundImage = starSystemAPI.getBackgroundTextureFilename();
        this.originalStarSpec = starSystemAPI.getStar().getTypeId();
        this.originalLightColor = starSystemAPI.getLightColor();
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

    public ArrayList<String> getFactionInSystemAsList()
    {
        return new ArrayList<String>(Arrays.asList(this.getFactionsInSystem()));
    }

    public SectorEntityToken getStationTokenForXY(float x, float y, float maxOffset)
    {
        return systemStationManager.getStationTokenForXY(x, y, maxOffset);
    }

    public void setStationOwner(SectorEntityToken station, String newOwnerFactionId, Boolean displayMessage, Boolean updateRelationship)
    {
        systemStationManager.setStationOwner(station, newOwnerFactionId, displayMessage, updateRelationship);
    }

    public String getOriginalBackgroundImage()
    {
        return  originalBackgroundImage;
    }

    public String getOriginalStarSpec()
    {
        return  originalStarSpec;
    }

    public Color getOriginalLightColor()
    {
        return originalLightColor;
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