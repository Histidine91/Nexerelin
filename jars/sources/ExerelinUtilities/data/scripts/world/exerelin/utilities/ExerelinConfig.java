package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

public class ExerelinConfig
{
    // List of ships that can be produced at any station regardless of owner
    public static String[] commonShipList = new String[]{"ox_Hull", "crig_Hull", "shuttle_Attack"};

    // Factions classed as neutral for relationship calculations
    public static String[] neutralFactions = new String[]{"neutral", "independent"};

    // Threading support for improving/smoothing performance
    public static boolean enableThreading = true;

    // Randomise the location of the omnifactory
    public static boolean randomOmnifactoryLocation = false;

    // Trading control
    public static boolean allowTradeAtAlliedStations = true;
    public static boolean allowTradeAtNeutralStations = false;
    public static boolean allowTradeAtHostileStations = false;
    public static boolean reduceCapitalShipSaleChance = true;

    // Supply reduction
    public static boolean reduceSupplies = true;
    public static boolean capSupplyDropToCargo = true;
    public static double reduceSuppliesFactor = 1.0;
    
    // System Generation Minimums
    public static int minimumPlanets = 3;
    public static int minimumStations = 5;
    public static int minimumAsteroidBelts = 0;

    // Resourcing
    public static String asteroidMiningResource = "supplies";
    public static String gasgiantMiningResource = "fuel";
    public static String fleetCostResource = "supplies";
    public static int miningAmountPerDayPerMiner = 50;

    // Player settings
    public static int playerBaseWage = 1000;
    public static boolean playerFactionFreeTransfer = false;

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelin_config.json");

            enableThreading = settings.getBoolean("enableThreading");

            randomOmnifactoryLocation = settings.getBoolean("randomOmnifactoryLocation");

            allowTradeAtAlliedStations = settings.getBoolean("allowTradeAtAlliedStations");
            allowTradeAtNeutralStations = settings.getBoolean("allowTradeAtNeutralStations");
            allowTradeAtHostileStations = settings.getBoolean("allowTradeAtHostileStations");
            reduceCapitalShipSaleChance = settings.getBoolean("reduceCapitalShipSaleChance");

            reduceSupplies = settings.getBoolean("reduceSupplies");
            capSupplyDropToCargo = settings.getBoolean("capSupplyDropToCargo");
            reduceSuppliesFactor = settings.getDouble("reduceSuppliesFactor");

            minimumPlanets = settings.getInt("minimumPlanets");
            minimumStations = settings.getInt("minimumStations");
            minimumAsteroidBelts = settings.getInt("minimumAsteroidBelts");

            asteroidMiningResource = settings.getString("asteroidMiningResource");
            gasgiantMiningResource = settings.getString("gasgiantMiningResource");
            fleetCostResource = settings.getString("fleetCostResource");
            miningAmountPerDayPerMiner = settings.getInt("miningAmountPerDayPerMiner");

            playerBaseWage = settings.getInt("playerBaseWage");
            playerFactionFreeTransfer = settings.getBoolean("playerFactionFreeTransfer");
        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR: Unable to load settings: " + e.getMessage());
        }
    }
}
