package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.ExerelinConstants;
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
	public int numPlanets = 32;
	public int numStations = 16;
	public int maxPlanetsPerSystem = 4;
	public int maxMarketsPerSystem = 6;	// includes stations
	public Map<String, Boolean> factions = new HashMap<>();

	// Game defaults
	public boolean corvusMode = true;
	public boolean respawnFactions = false;
	public boolean onlyRespawnStartingFactions = false;
	@Deprecated
	public int numStartFactions = -1;
	public boolean randomStartRelationships = false;
	public boolean randomStartRelationshipsPirate = false;
	public boolean easyMode = false;
	public boolean hardMode = false;
	public boolean prismMarketPresent = false;
	public boolean freeStart = false;
	public boolean useFactionWeights = true;
	public int numStartingOfficers = 0;
	public boolean randomStartShips = false;

	private ExerelinSetupData()
	{
		List<String> factionIds = ExerelinConfig.getFactions(true, false);
		factionIds.add(Factions.INDEPENDENT);
		factionIds.remove(ExerelinConstants.PLAYER_NPC_ID);
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
