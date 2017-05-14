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

	// Sector Generation Defaults
	public int numSystems = 12;
	public int numPlanets = 24;
	public int numStations = 12;
	public int maxPlanetsPerSystem = 4;
	public int maxMarketsPerSystem = 6;	// includes stations

	// Game defaults
	public boolean corvusMode = true;
	public boolean respawnFactions = false;
	public boolean onlyRespawnStartingFactions = false;
	public int numStartFactions = -1;
	public boolean randomStartRelationships = false;
	public boolean easyMode = false;
	public boolean hardMode = false;
	@Deprecated public boolean omnifactoryPresent = false;
	@Deprecated public boolean randomOmnifactoryLocation = false;
	public boolean prismMarketPresent = false;
	public boolean freeStart = false;
	public boolean useMarketFactionWeights = true;
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
		return new ArrayList<>(getFactions(true));
	}
	
	public List<String> getAllFactions()
	{
		return new ArrayList<>(getFactions(false));
	}
	
	protected List<String> getFactions(boolean playableOnly)
	{
		List<String> factionsList = new ArrayList<>();
		factionsList.addAll(ExerelinConfig.getBuiltInFactionsList(playableOnly));
		factionsList.addAll(ExerelinConfig.getModdedFactionsList(playableOnly));
		return new ArrayList<>(factionsList);
	}
}
