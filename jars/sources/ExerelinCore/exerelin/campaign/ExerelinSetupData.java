package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.utilities.ExerelinConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/* This class functions as a data structure for Exerelin setup
 */

@SuppressWarnings("unchecked")
public class ExerelinSetupData
{
	public static Logger log = Global.getLogger(ExerelinSetupData.class);
	private static ExerelinSetupData instance = null;

	// Sector Generation Defaults
	public int numSystems = 16;
	public int numPlanets = 36;
	public int numStations = 12;
	public int maxPlanetsPerSystem = 3;
	public int maxMarketsPerSystem = 5;	// includes stations
	public Map<String, Boolean> factions = new HashMap<>();

	// Game defaults
	public boolean corvusMode = true;
	public boolean respawnFactions = false;
	public boolean onlyRespawnStartingFactions = false;
	public boolean randomStartRelationships = false;
	public boolean randomStartRelationshipsPirate = false;
	public boolean easyMode = false;
	public boolean hardMode = false;
	public boolean prismMarketPresent = false;
	public boolean freeStart = false;
	public boolean useFactionWeights = true;
	public boolean randomFactionWeights = false;
	public int numStartingOfficers = 0;
	public boolean randomStartShips = false;

	private ExerelinSetupData()
	{
		List<String> factionIds = ExerelinConfig.getFactions(true, false);
		factionIds.add(Factions.INDEPENDENT);
		factionIds.remove(Factions.PLAYER);
		for (String factionId : factionIds)
			factions.put(factionId, true);
	}

	public static ExerelinSetupData getInstance()
	{
		if(instance == null)
		{
			instance = new ExerelinSetupData();
		}

		return instance;
	}

	public static void resetInstance()
	{
		instance = new ExerelinSetupData();
	}
}
