package exerelin.campaign;

import com.fs.starfarer.api.Global;
import exerelin.utilities.ExerelinConfig;

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
	public int numSystems = 16;
	public int numPlanets = 32;
	public int numStations = 16;
	public int maxPlanetsPerSystem = 4;
	public int maxMarketsPerSystem = 6;	// includes stations

	// Game defaults
	public boolean corvusMode = true;
	public boolean respawnFactions = false;
	public boolean onlyRespawnStartingFactions = false;
	public int numStartFactions = -1;
	public boolean randomStartRelationships = false;
	public boolean randomStartRelationshipsPirate = false;
	public boolean easyMode = false;
	public boolean hardMode = false;
	@Deprecated public boolean omnifactoryPresent = false;
	@Deprecated public boolean randomOmnifactoryLocation = false;
	public boolean prismMarketPresent = false;
	public boolean freeStart = false;
	public boolean useMarketFactionWeights = true;	// FIXME: use me!
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
}
