package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;

import java.util.ArrayList;
import java.util.Collections;
import org.apache.log4j.Logger;

/* This class functions as a data structure for Exerelin setup
 */

@SuppressWarnings("unchecked")
public final class ExerelinSetupData
{
	public static Logger log = Global.getLogger(ExerelinSetupData.class);
	private static ExerelinSetupData instance = null;

	// Player setup defaults
	private String[] availableFactions = null;

	// Sector Generation Defaults
	public int numSystems = 6;
	public int maxMoonsPerPlanet = 2;

	public int maxPlanets = 5;
	public int maxStations = 3;
	public int maxAsteroidBelts = 3;
	public int maxSystemSize = 14000;
	public int maxSectorSize = 12000;

	// Game defaults
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

	public String[] getPossibleFactions()
	{
		ArrayList possibleFactionsList = new ArrayList();
	
		// Add built in factions
		ExerelinConfig.loadSettings();
		Collections.addAll(possibleFactionsList, this.getBuiltInFactionsList());
	
		// Add modded factions
		Collections.addAll(possibleFactionsList, this.getModdedFactionsList());
		return (String[])possibleFactionsList.toArray(new String[possibleFactionsList.size()]);
	}

	public void resetAvailableFactions()
	{
		availableFactions = null;
	}

	public String[] getAvailableFactions()
	{
		SectorAPI sector = Global.getSector();
		if (availableFactions == null)
		{
			String[] locPossibleFaction = this.getPossibleFactions();
			ArrayList confirmedFactions = new ArrayList(locPossibleFaction.length);
			
			for(int i = 0; i < locPossibleFaction.length; i = i + 1)
			{
				FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
				if(fac != null)
					confirmedFactions.add(fac.getId());
				else
					log.warn("EXERELIN ERROR: Couldn't determine faction for:" + locPossibleFaction[i]);
			}
			availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
		}
		return availableFactions;
	}

	public String[] getModdedFactionsList()
	{
	log.info("Getting modded factions");
	ArrayList possibleModdedFactionList = new ArrayList();

	for(int i = 0; i < ExerelinConfig.exerelinFactionConfigs.size(); i++)
	{
		ExerelinFactionConfig exerelinFactionConfig = (ExerelinFactionConfig)ExerelinConfig.exerelinFactionConfigs.get(i);
		if(exerelinFactionConfig.playableFaction
			&& isFactionInstalled(exerelinFactionConfig.factionId, exerelinFactionConfig.uniqueModClassName))
		{
			possibleModdedFactionList.add(exerelinFactionConfig.factionId);
		}
	}
		return (String[])possibleModdedFactionList.toArray(new String[possibleModdedFactionList.size()]);
	}

	public String[] getBuiltInFactionsList()
	{
		ArrayList possibleBuiltInFactionList = new ArrayList();

		for(int i = 0; i < ExerelinConfig.exerelinFactionConfigs.size(); i++)
		{
			ExerelinFactionConfig exerelinFactionConfig = (ExerelinFactionConfig)ExerelinConfig.exerelinFactionConfigs.get(i);
			if(exerelinFactionConfig.playableFaction && exerelinFactionConfig.uniqueModClassName.equalsIgnoreCase(""))
			{
				possibleBuiltInFactionList.add(exerelinFactionConfig.factionId);
			}
		}

		return (String[])possibleBuiltInFactionList.toArray(new String[possibleBuiltInFactionList.size()]);
	}

	public boolean isFactionInstalled(String factionId, String factionSpecficClassName)
	{
		try
		{
			Global.getSettings().getScriptClassLoader().loadClass(factionSpecficClassName);
			log.info("Loaded " + factionId);
			return true;
		}
		catch (ClassNotFoundException ex)
		{
			log.info("Skipped " + factionId);
			return false;
		}
	}
}
