package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/* This class functions as a data structure for Exerelin setup
 */

@SuppressWarnings("unchecked")
public final class ExerelinSetupData
{
	public static Logger log = Global.getLogger(ExerelinSetupData.class);
	private static ExerelinSetupData instance = null;

	// Player setup defaults
	private List<String> factions = null;
	private List<String> playableFactions = null;

	// Sector Generation Defaults
	public int numSystems = 6;
	public int numSystemsEmpty = 2;
	public int maxMoonsPerPlanet = 2;

	public int maxPlanets = 5;
	public int maxStations = 3;
	public int maxAsteroidBelts = 3;
	public int baseSystemSize = 12800;
	public int maxSectorSize = 12000;

	// Game defaults
	public boolean corvusMode = false;
	public boolean respawnFactions = false;
	public boolean onlyRespawnStartingFactions = false;
	public int respawnDelay = 60;
	public int numStartFactions = -1;
	public boolean randomStartRelationships = false;
	public boolean easyMode = false;
	public boolean hardMode = false;
	public boolean omnifactoryPresent = false;
	public boolean randomOmnifactoryLocation = false;
	public boolean prismMarketPresent = false;
	public boolean freeStart = false;
	public int numStartingOfficers = 0;
	public boolean randomStartShips = false;

	private ExerelinSetupData()
	{
		// Empty constructor
	}

	public static ExerelinSetupData getInstance()
	{
		if(instance == null)
		{
			instance = new ExerelinSetupData();
			ExerelinConfig.loadSettings();
		}

		return instance;
	}

	public static void resetInstance()
	{
		instance = new ExerelinSetupData();
	}
	
	public List<String> getPlayableFactions()
	{
		if (playableFactions != null) return new ArrayList<>(playableFactions);	
		playableFactions = getFactions(true);
		return new ArrayList<>(playableFactions);
	}
	
	public List<String> getAllFactions()
	{
		if (factions != null) return new ArrayList<>(factions);	
		factions = getFactions(false);
		return new ArrayList<>(factions);
	}
	
	protected List<String> getFactions(boolean playableOnly)
	{
		List<String> factionsList = new ArrayList<>();
		factionsList.addAll(ExerelinConfig.getBuiltInFactionsList(playableOnly));
		factionsList.addAll(ExerelinConfig.getModdedFactionsList(playableOnly));
		return new ArrayList<>(factionsList);
	}

	public void resetAvailableFactions()
	{
		factions = null;
		playableFactions = null;
	}
}
