package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

public class ExerelinConfig
{
    // List of ships that can be produced at any station regardless of owner
    public static String[] commonShipList = new String[]{"ox_Hull", "crig_Hull", "shuttle_Attack"};

    // Factions classed as neutral for relationship calculations
    public static String[] neutralFactions = new String[]{"neutral", "independent"};

    // Threading support for improving/smoothing performance WARNING: BROKEN
    public static boolean enableThreading = false;

    // Randomise the location of the omnifactory
    public static boolean randomOmnifactoryLocation = false;

    // Trading control
    public static boolean allowTradeAtAlliedStations = true;
    public static boolean allowTradeAtNeutralStations = false;
    public static boolean allowTradeAtHostileStations = false;

    // Supply reduction
    public static boolean reduceSupplies = true;
    public static int maxSuppliesDropped = 200;
    
    // System Generation Minimums
    public static int minimumPlanets = 3;
    public static int minimumStations = 5;
    public static int minimumAsteroidBelts = 0;

    // Resourcing
    public static String asteroidMiningResource = "supplies";
    public static String gasgiantMiningResource = "fuel";
    public static String fleetCostResource = "supplies";
    public static int miningAmountPerDayPerMiner = 50;

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelin_config.json");

            //enableThreading = settings.getBoolean("enableThreading");

            randomOmnifactoryLocation = settings.getBoolean("randomOmnifactoryLocation");

            allowTradeAtAlliedStations = settings.getBoolean("allowTradeAtAlliedStations");
            allowTradeAtNeutralStations = settings.getBoolean("allowTradeAtNeutralStations");
            allowTradeAtHostileStations = settings.getBoolean("allowTradeAtHostileStations");

            reduceSupplies = settings.getBoolean("reduceSupplies");
            maxSuppliesDropped = settings.getInt("maxSuppliesDropped");

            minimumPlanets = settings.getInt("minimumPlanets");
            minimumStations = settings.getInt("minimumStations");
            minimumAsteroidBelts = settings.getInt("minimumAsteroidBelts");

            asteroidMiningResource = settings.getString("asteroidMiningResource");
            gasgiantMiningResource = settings.getString("gasgiantMiningResource");
            fleetCostResource = settings.getString("fleetCostResource");
            miningAmountPerDayPerMiner = settings.getInt("miningAmountPerDayPerMiner");
        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR: Unable to load settings: " + e.getMessage());
        }
    }
}
