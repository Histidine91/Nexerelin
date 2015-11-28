package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
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
	private List<String> availableFactions = null;

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
	public boolean hardMode = false;
	public boolean omnifactoryPresent = false;
	public boolean randomOmnifactoryLocation = false;
	public boolean prismMarketPresent = false;
	public boolean freeStart = false;

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

	public List<String> getAvailableFactions()
	{
		if (availableFactions != null) return availableFactions;
		List<String> factionsList = new ArrayList<>();
	
		// Add built in factions
		ExerelinConfig.loadSettings();
		factionsList.addAll(this.getBuiltInFactionsList());
	
		// Add modded factions
		factionsList.addAll(this.getModdedFactionsList());
		
		availableFactions = factionsList;
		return factionsList;
	}

	public void resetAvailableFactions()
	{
		availableFactions = null;
	}

	public List<String> getModdedFactionsList()
	{
		//log.info("Getting modded factions");
		List<String> possibleModdedFactions = new ArrayList<String>();

		for (ExerelinFactionConfig config : ExerelinConfig.exerelinFactionConfigs) {
			if (!config.playableFaction) continue;
			if (config.uniqueModClassName.equalsIgnoreCase("")) continue;	// FIXME replace with isBuiltIn boolean
			if (isFactionInstalled(config.factionId))
			{
				possibleModdedFactions.add(config.factionId);
			}
		}
		return possibleModdedFactions;
	}

	public List<String> getBuiltInFactionsList()
	{
		List<String> possibleBuiltInFactions = new ArrayList<String>();

		for (ExerelinFactionConfig config : ExerelinConfig.exerelinFactionConfigs) {
			if (!config.playableFaction) continue;
			if (!config.uniqueModClassName.equalsIgnoreCase("")) continue;
			possibleBuiltInFactions.add(config.factionId);
		}

		return possibleBuiltInFactions;
	}

	public boolean isFactionInstalled(String factionId)
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
		if (faction == null)
		{
			log.warn("Couldn't find faction " + factionId);
			return false;
		}
		return true;
	}
}
