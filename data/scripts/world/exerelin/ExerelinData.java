package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SpawnPointPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import data.scripts.world.exerelin.utilities.ExerelinConfig;

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

    // Player setup defaults
    private String playerFaction = "sindrian_diktat";
    private String playerStartingShipVariant = "shuttle_Attack";

    // Valid ships for special fleets
    private final String[] ValidBoardingFlagships = new String[] { "atlas", "mazerk", "neerin", "thule_hansa", "qua_cesall", "zorg_allocator", "neutrino_nausicaa", "neutrino_nausicaa2"};
    private final String[] ValidTroopTransportShips = new String[] { "valkyrie", "hadd_stonehead", "bushi_sangu", "hii_saari", "zorg_allocator", "qua_yidato" };
    private final String[] ValidMiningShips = new String[] {"mining_drone", "zorg_worker"};

	//private String[] possibleFactions = new String[] {};
	private String[] possibleFactions = new String[] {"hegemony", "tritachyon", "pirates", "sindrian_diktat"};
	private String[] availableFactions = null;
	public boolean onlyVanillaFactions = false;
	public boolean confirmedAvailableFactions = false;

    // Sector Generation Defaults
	public int numSystems = 4;
    public int maxMoonsPerPlanet = 3;

	public int maxPlanets = 6;
	public int maxStations = 10;
	public int maxAsteroidBelts = 0;
    public int maxSystemSize = 16000;
    public int maxSectorSize = 16000;

    // Game defaults
	public Boolean playerOwnedStationFreeTransfer = false;
	public Boolean confirmedFreeTransfer = false;
	public boolean respawnFactions = true;
	public boolean onlyRespawnStartingFactions = false;
	public int respawnDelay = 60;
	public int numStartFactions = 3;
	public boolean omniFacPresent = true;
	public int maxFactionsInExerelinAtOnce = 3;

	private SectorManager sectorManager;

	private ExerelinData()
	{
		// Empty constructor
	}

	public static ExerelinData getInstance()
	{
		if(instance == null)
        {
			instance = new ExerelinData();
            ExerelinConfig.loadSettings();
        }

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
            StarSystemAPI system;
            try
            {
			    system = (StarSystemAPI)sector.getStarSystems().get(0); //TODO - change
            }
            catch(Exception e)
            {
                System.out.println("No systems built yet." + e.getMessage());
                return;
            }

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
				while(confirmedFactions.size() < Math.min(this.numStartFactions, locPossibleFaction.length - 1))
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
		if(isFactionInstalled("blackrock", "data.scripts.BRModPlugin"))
			possibleFactionList.add("blackrock");

		// Test for interstellarFederation
		if(isFactionInstalled("interstellarFederation", "data.scripts.world.InterstellarFederationSectorGen"))
			possibleFactionList.add("interstellarFederation");

		// Test for junkpirate
		if(isFactionInstalled("junkpirate", "data.scripts.world.JPSectorGen"))
			possibleFactionList.add("junkpirate");

		// Test for council_loyalists
		if(isFactionInstalled("council_loyalists", "data.scripts.world.HegemonyCoreGen"))
			possibleFactionList.add("council_loyalists");

		// Test for neutrino
		if(isFactionInstalled("neutrino", "data.scripts.world.neutrinoGen"))
			possibleFactionList.add("neutrino");

		// Test for gedune
		if(isFactionInstalled("gedune", "data.scripts.world.GeduneGen"))
			possibleFactionList.add("gedune");

		// Test for nihil
		if(isFactionInstalled("nihil", "data.scripts.nihil.world.NihilSectorGen"))
			possibleFactionList.add("nihil");

		// Test for nomads
		if(isFactionInstalled("nomads", "data.scripts.TheNomadsModPlugin"))
			possibleFactionList.add("nomads");

		// Test for relics
		if(isFactionInstalled("relics", "data.scripts.pur.world.PurSectorGen"))
			possibleFactionList.add("relics");

		// Test for shadowyards
		if(isFactionInstalled("shadowyards_hi", "data.scripts.world.SHIGen"))
			possibleFactionList.add("shadowyards_hi");

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
		if(isFactionInstalled("bushi", "data.scripts.world.BushiModPlugin"))
			possibleFactionList.add("bushi");

		// Test for Hiigaran Descendents
		if(isFactionInstalled("hiigaran_descendants", "data.scripts.world.HiiModPlugin"))
			possibleFactionList.add("hiigaran_descendants");

        // Test for Ceredia
        if(isFactionInstalled("ceredia", "data.scripts.world.AvanMod"))
            possibleFactionList.add("ceredia");

        // Test for Directorate
        if(isFactionInstalled("directorate", "data.scripts.world.AvanMod"))
            possibleFactionList.add("directorate");

        // Test for Isora
        if(isFactionInstalled("isora", "data.scripts.world.AvanMod"))
            possibleFactionList.add("isora");

        // Test for Independant Miners
        if(isFactionInstalled("independantMiners", "data.scripts.world.MineFactionModGen"))
            possibleFactionList.add("independantMiners");

        // Test for Scrappers
        if(isFactionInstalled("scrappers", "data.scripts.world.hadd_ModGen"))
            possibleFactionList.add("scrappers");

        // Test for Shadow Order
        if(isFactionInstalled("shadoworder", "data.scripts.world.tadd_ModGen"))
            possibleFactionList.add("shadoworder");

        // Test for Zorg
        if(isFactionInstalled("zorg_hive", "data.scripts.ZorgModPlugin"))
            possibleFactionList.add("zorg_hive");

        // Test for Qualljom Society
        if(isFactionInstalled("qualljom_society", "data.scripts.world.QSGen"))
            possibleFactionList.add("qualljom_society");

        // Test for Kadur Theocracy
        if(isFactionInstalled("regime", "data.scripts.world.vayraKadurGen"))
            possibleFactionList.add("regime");

        // Test for Qamar Insurgency
        if(isFactionInstalled("insurgency", "data.scripts.world.vayraKadurGen"))
            possibleFactionList.add("insurgency");

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

    public String[] getValidBoardingFlagships()
    {
        return ValidBoardingFlagships;
    }

    public String[] getValidTroopTransportShips()
    {
        return ValidTroopTransportShips;
    }

    public String[] getValidMiningShips()
    {
        return ValidMiningShips;
    }

    public String getPlayerStartingShipVariant()
    {
        return this.playerStartingShipVariant;
    }

    public void setPlayerStartingShipVariant(String variant)
    {
        this.playerStartingShipVariant = variant;
    }
}
