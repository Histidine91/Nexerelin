package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SpawnPointPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;

/* This class functions as a data transfer for the various Exerelin modules

   It should also function as a cache, but due to no hookable event on load/save
   some variables need to be reset each game advance.
 */

public final class ExerelinData
{
	private static ExerelinData instance = null;
	private static SectorAPI sector = null;

	public boolean confirmedFaction = false;
	private String playerFaction = "independent";

	private String[] possibleFactions = new String[] {"hegemony", "tritachyon", "pirates", "independent"};
	//private String[] possibleFactions = new String[] {"hegemony", "tritachyon", "pirates", "independent"};
	private String[] availableFactions = null;
	public boolean onlyVanillaFactions = false;
	public boolean confirmedAvailableFactions = false;

	public int numSystems = 1;
	public int numPlanets = 10;
	public int maxMoonsPerPlanet = 3;
	public int numStations = 3;
	public int numAsteroidBelts = 6;

	public Boolean playerOwnedStationFreeTransfer = false;
	public Boolean confirmedFreeTransfer = false;
	public boolean respawnFactions = true;
	public boolean onlyRespawnStartingFactions = true;
	public int respawnDelay = 1000000;
	public int numStartFactions = 3;
	public boolean omniFacPresent = false;
	public int maxFactionsInExerelinAtOnce = 3;
	public int maxSystemSize = 15000;

	public Vector2f playerOffMapFleetSpawnLocation = null;

	private SectorManager sectorManager;

	private ExerelinData()
	{
		// Empty constructor
	}

	public static ExerelinData getInstance()
	{
		if(instance == null)
			instance = new ExerelinData();

		updateSectorManager();

		return instance;
	}

	private static void updateSectorManager()
	{
		if (Global.getSector() != sector)
		{
			sector = Global.getSector();

			System.out.println("Sector change detected, retrieving saved time manager...");

			//TODO - Will the time manager always be in Exerelin?
			StarSystemAPI system = (StarSystemAPI)sector.getStarSystems().get(0); //TODO - change

			ArrayList spawnPoints = ExerelinHacks.getSpawnPoints(system);

			if (spawnPoints != null)
			{
				System.out.println("Spawnpoints retrieved.");

				for(Iterator it = spawnPoints.iterator(); it.hasNext(); )
				{
					SpawnPointPlugin plugin = (SpawnPointPlugin)it.next();

					if (plugin instanceof TimeManager)
					{
						System.out.println("TimeManager found, settign reference");

						instance.sectorManager = ((TimeManager) plugin).sectorManagerRef;
						break;
					}
				}
			} else
			{
				System.out.println("Failed to retrieve spawnpoints.");
			}
		}
	}

	public String getPlayerFaction()
	{
		return playerFaction;
	}

	public void setPlayerFaction(String factionId)
	{
		confirmedFaction = true;
		playerFaction = factionId;
	}

	public void resetPlayerFaction()
	{
		playerFaction = "independent"; // Set to default
	}

	public String[] getPossibleFactions()
	{
		if(onlyVanillaFactions)
			return new String[] {"hegemony", "tritachyon", "pirates", "independent",};
		else
		{
			ArrayList possibleFactionsList = new ArrayList();

			// Add built in factions
			for(int i = 0; i < possibleFactions.length; i++)
				possibleFactionsList.add(possibleFactions[i]);

			// Add modded factions
			addModdedFactionsToList(possibleFactionsList);
			return (String[])possibleFactionsList.toArray(new String[possibleFactionsList.size()]);
		}
	}

	public void setAvailableFactions(String[] array)
	{
		availableFactions = array;
	}

	public void resetAvailableFactions()
	{
		availableFactions = null;
	}

	public String[] getAvailableFactions(SectorAPI sector)
	{
		if (availableFactions == null)
		{
			String[] locPossibleFaction = this.getPossibleFactions();
			ArrayList confirmedFactions = new ArrayList(locPossibleFaction.length);

			if(!onlyRespawnStartingFactions)
			{
				for(int i = 0; i < locPossibleFaction.length; i = i + 1)
				{
					FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
					if(fac != null)
						confirmedFactions.add(fac.getId());
					else
						System.out.println("Couldn't determine faction for:" + locPossibleFaction[i]);
				}
				availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
			}
			else
			{
				ExerelinUtils.shuffleStringArray(locPossibleFaction);

				int i = 0;
				while(confirmedFactions.size() < Math.min(this.numStartFactions, locPossibleFaction.length))
				{
					if(locPossibleFaction[i].equalsIgnoreCase(this.playerFaction))
					{
						i = i + 1;
						continue;
					}

					FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
					if(fac != null)
					{
						confirmedFactions.add(fac.getId());
					}
					else
						System.out.println("Couldn't determine faction for:" + locPossibleFaction[i]);

					i = i + 1;
				}
				confirmedFactions.add(this.getPlayerFaction());

				availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
			}
		}
		return availableFactions;
	}

	public void addModdedFactionsToList(ArrayList possibleFactionList)
	{
		System.out.println("EXERELIN: Getting modded factions");

		// Test for antediluvian
		if(isFactionInstalled("antediluvian", "data.scripts.world.AntediluvianGen"))
			possibleFactionList.add("antediluvian");

		// Test for blackrock
		if(isFactionInstalled("blackrock", "data.scripts.world.BRGen"))
			possibleFactionList.add("blackrock");

		// Test for interstellarFederation
		if(isFactionInstalled("interstellarFederation", "data.scripts.world.InterstellarFederationSectorGen"))
			possibleFactionList.add("interstellarFederation");

		// Test for junkpirate
		if(isFactionInstalled("junkpirate", "data.scripts.world.JPSectorGen"))
			possibleFactionList.add("junkpirate");

		// Test for council
		if(isFactionInstalled("council", "data.scripts.world.HegemonyCoreGen"))
			possibleFactionList.add("council");

		// Test for neutrino
		if(isFactionInstalled("neutrino", "data.scripts.world.neutrinoGen"))
			possibleFactionList.add("neutrino");

		// Test for gedune
		if(isFactionInstalled("gedune", "data.scripts.world.SectorGenWithGedune"))
			possibleFactionList.add("gedune");

		// Test for nihil
		if(isFactionInstalled("nihil", "data.scripts.nihil.world.NihilSectorGen"))
			possibleFactionList.add("nihil");

		// Test for nomads
		if(isFactionInstalled("nomad", "data.scripts.nom.world.SectorGenWithNomads"))
			possibleFactionList.add("nomad");

		// Test for relics
		//if(isFactionInstalled("relics", "data.scripts.pur.world.PurSectorGen"))
			//possibleFactionList.add("relics");

		// Test for shadowyards
		if(isFactionInstalled("shadowyards", "data.scripts.world.SHIGen"))
			possibleFactionList.add("shadowyards");

		// Test for thulelegacy
		if(isFactionInstalled("thulelegacy", "data.scripts.world.TLGen"))
			possibleFactionList.add("thulelegacy");

		// Test for valkyrian
		if(isFactionInstalled("valkyrian", "data.scripts.world.valkyrianGen"))
			possibleFactionList.add("valkyrian");

		// Test for syndicateasp
		if(isFactionInstalled("syndicateasp", "data.scripts.world.ASPSectorGen"))
			possibleFactionList.add("syndicateasp");

		// Test for lotusconglomerate
		if(isFactionInstalled("lotusconglomerate", "data.scripts.world.LotusSectorGen"))
			possibleFactionList.add("lotusconglomerate");

		// Test for Bushi
		if(isFactionInstalled("bushi", "data.scripts.world.BushiGen"))
			possibleFactionList.add("bushi");

		// Test for Hiigaran Descendents
		if(isFactionInstalled("hiigaran_descendants", "data.scripts.world.HiigaraGen"))
			possibleFactionList.add("hiigaran_descendants");

		System.out.println("- - - - - - - - - -");
	}

	private boolean isFactionInstalled(String factionId, String factionSpecficClassName)
	{
		try
		{
			Global.getSettings().getScriptClassLoader().loadClass(factionSpecficClassName);
			System.out.println(factionId + " installed");
			return true;
		}
		catch (ClassNotFoundException ex)
		{
			System.out.println(factionId + " not installed");
			return false;
		}
	}

	public SectorManager getSectorManager()
	{
		return sectorManager;
	}

	public void setSectorManager(SectorManager inSectorManager)
	{
		sectorManager = inSectorManager;
	}
}
