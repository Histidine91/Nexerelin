package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import data.scripts.world.exerelin.utilities.ExerelinConfig;

import java.util.ArrayList;
import java.util.Collections;

/* This class functions as a data transfer for the various Exerelin modules
 */

@SuppressWarnings("unchecked")
public final class ExerelinData
{
    private static ExerelinData instance = null;

    // Player setup defaults
    private String playerFaction = "sindrian_diktat";
    private String playerStartingShipVariant = "shuttle_Attack";

    // Valid ships for special fleets
    private final String[] ValidBoardingFlagships = new String[] { "atlas", "mazerk", "neerin", "thule_hansa", "qua_cesall", "zorg_auxiliary", "neutrino_nausicaa", "neutrino_nausicaa2"};
    private final String[] ValidTroopTransportShips = new String[] { "valkyrie", "hadd_stonehead", "bushi_sangu", "hii_saari", "zorg_auxiliary", "qua_yidato" };
    private final String[] ValidMiningShips = new String[] {"mining_drone", "zorg_sphere"};

	//private String[] possibleFactions = new String[] {};
	private String[] possibleFactions = new String[] {"hegemony", "tritachyon", "pirates", "sindrian_diktat"};
	private String[] availableFactions = null;
	public boolean onlyVanillaFactions = false;

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

		return instance;
	}

    public static void resetInstance()
    {
        instance = new ExerelinData();
        ExerelinConfig.loadSettings();
    }

	public String getPlayerFaction()
	{
        if(sectorManager != null)
            return sectorManager.getPlayerFactionId();
        else
		    return playerFaction;
	}

	public void setPlayerFaction(String factionId)
	{
		playerFaction = factionId;
	}

	public String[] getPossibleFactions()
	{
		if(onlyVanillaFactions)
			return new String[] {"hegemony", "tritachyon", "pirates", "sindrian_diktat",};
		else
		{
			ArrayList possibleFactionsList = new ArrayList();

			// Add built in factions
            Collections.addAll(possibleFactionsList, this.possibleFactions);

			// Add modded factions
            Collections.addAll(possibleFactionsList, this.getModdedFactionsList());
			return (String[])possibleFactionsList.toArray(new String[possibleFactionsList.size()]);
		}
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

	public String[] getModdedFactionsList()
	{
		System.out.println("EXERELIN: Getting modded factions");

        ArrayList possibleModdedFactionList = new ArrayList();

		// Test for antediluvian
		if(isFactionInstalled("antediluvian", "data.scripts.world.AntediluvianGen"))
			possibleModdedFactionList.add("antediluvian");

		// Test for blackrock
		if(isFactionInstalled("blackrock", "data.scripts.BRModPlugin"))
			possibleModdedFactionList.add("blackrock");

		// Test for interstellarFederation
		if(isFactionInstalled("interstellarFederation", "data.scripts.world.InterstellarFederationSectorGen"))
			possibleModdedFactionList.add("interstellarFederation");

		// Test for junkpirate
		if(isFactionInstalled("junkpirate", "data.scripts.world.JPSectorGen"))
			possibleModdedFactionList.add("junkpirate");

		// Test for council_loyalists
		if(isFactionInstalled("council_loyalists", "data.scripts.world.HegemonyCoreGen"))
			possibleModdedFactionList.add("council_loyalists");

		// Test for neutrino
		if(isFactionInstalled("neutrino", "data.scripts.world.neutrinoGen"))
			possibleModdedFactionList.add("neutrino");

		// Test for gedune
		if(isFactionInstalled("gedune", "data.scripts.world.GeduneGen"))
			possibleModdedFactionList.add("gedune");

		// Test for nihil
		if(isFactionInstalled("nihil", "data.scripts.nihil.world.NihilSectorGen"))
			possibleModdedFactionList.add("nihil");

		// Test for nomads
		if(isFactionInstalled("nomads", "data.scripts.TheNomadsModPlugin"))
			possibleModdedFactionList.add("nomads");

		// Test for relics
		if(isFactionInstalled("relics", "data.scripts.pur.world.PurSectorGen"))
			possibleModdedFactionList.add("relics");

		// Test for shadowyards
		if(isFactionInstalled("shadowyards_hi", "data.scripts.world.SHIGen"))
			possibleModdedFactionList.add("shadowyards_hi");

		// Test for thulelegacy
		if(isFactionInstalled("thulelegacy", "data.scripts.world.TLGen"))
			possibleModdedFactionList.add("thulelegacy");

		// Test for valkyrian
		if(isFactionInstalled("valkyrian", "data.scripts.world.valkyrianGen"))
			possibleModdedFactionList.add("valkyrian");

		// Test for syndicateasp
		if(isFactionInstalled("syndicateasp", "data.scripts.world.ASPSectorGen"))
			possibleModdedFactionList.add("syndicateasp");

		// Test for lotusconglomerate
		if(isFactionInstalled("lotusconglomerate", "data.scripts.world.LotusSectorGen"))
			possibleModdedFactionList.add("lotusconglomerate");

		// Test for Bushi
		if(isFactionInstalled("bushi", "data.scripts.world.BushiModPlugin"))
			possibleModdedFactionList.add("bushi");

		// Test for Hiigaran Descendents
		if(isFactionInstalled("hiigaran_descendants", "data.scripts.world.HiiModPlugin"))
			possibleModdedFactionList.add("hiigaran_descendants");

        // Test for Ceredia
        if(isFactionInstalled("ceredia", "data.scripts.world.AvanMod"))
            possibleModdedFactionList.add("ceredia");

        // Test for Directorate
        if(isFactionInstalled("directorate", "data.scripts.world.AvanMod"))
            possibleModdedFactionList.add("directorate");

        // Test for Isora
        if(isFactionInstalled("isora", "data.scripts.world.AvanMod"))
            possibleModdedFactionList.add("isora");

        // Test for Independant Miners
        if(isFactionInstalled("independantMiners", "data.scripts.world.MineFactionModGen"))
            possibleModdedFactionList.add("independantMiners");

        // Test for Scrappers
        if(isFactionInstalled("scrappers", "data.scripts.world.hadd_ModGen"))
            possibleModdedFactionList.add("scrappers");

        // Test for Shadow Order
        if(isFactionInstalled("shadoworder", "data.scripts.world.tadd_ModGen"))
            possibleModdedFactionList.add("shadoworder");

        // Test for Zorg
        if(isFactionInstalled("zorg_hive", "data.scripts.ZorgModPlugin"))
            possibleModdedFactionList.add("zorg_hive");

        // Test for Qualljom Society
        if(isFactionInstalled("qualljom_society", "data.scripts.world.QSGen"))
            possibleModdedFactionList.add("qualljom_society");

        // Test for Kadur Theocracy
        if(isFactionInstalled("regime", "data.scripts.world.vayraKadurGen"))
            possibleModdedFactionList.add("regime");

        // Test for Qamar Insurgency
        if(isFactionInstalled("insurgency", "data.scripts.world.vayraKadurGen"))
            possibleModdedFactionList.add("insurgency");

        // Test for Citadel Defenders
        if(isFactionInstalled("citadeldefenders", "data.scripts.world.defendersGen"))
            possibleModdedFactionList.add("citadeldefenders");

		System.out.println("- - - - - - - - - -");
        return (String[])possibleModdedFactionList.toArray(new String[possibleModdedFactionList.size()]);
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
