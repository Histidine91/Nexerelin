package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

public class ExerelinConfig
{
    // List of ships that can be produced at any station regardless of owner
    public static String[] commonShipList = new String[]{"ox_Hull", "crig_Hull", "shuttle_Attack", "hermes_Standard"};

    // Factions classed as neutral for relationship calculations
    public static String[] neutralFactions = new String[]{"neutral", "independent"};
    public static List<ExerelinFactionConfig> exerelinFactionConfigs;

    // Threading support for improving/smoothing performance
    public static boolean enableThreading = true;
    // Use multiple larger backgrounds
    public static boolean useMultipleBackgroundsAndStars = true;
    // Use custom faction configs
    public static boolean useCustomFactionConfigs = true;

    // Randomise the location of the omnifactory
    public static boolean randomOmnifactoryLocation = false;

    // Trading control
    public static boolean allowTradeAtAlliedStations = true;
    public static boolean allowTradeAtNeutralStations = false;
    public static boolean allowTradeAtHostileStations = false;

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

    // Special Ships
    public static String[] validBoardingFlagships = new String[]{};
    public static String[] validTroopTransportShips = new String[]{};
    public static String[] validMiningShips = new String[]{};

    public static String[] builtInFactions = new String[]{};
    public static String[] supportedModFactions = new String[]{};

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelin_config.json");

            enableThreading = settings.getBoolean("enableThreading");
            useMultipleBackgroundsAndStars = settings.getBoolean("useMultipleBackgroundsAndStars");
            useCustomFactionConfigs = settings.getBoolean("useCustomFactionConfigs");

            randomOmnifactoryLocation = settings.getBoolean("randomOmnifactoryLocation");

            allowTradeAtAlliedStations = settings.getBoolean("allowTradeAtAlliedStations");
            allowTradeAtNeutralStations = settings.getBoolean("allowTradeAtNeutralStations");
            allowTradeAtHostileStations = settings.getBoolean("allowTradeAtHostileStations");

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

            validBoardingFlagships = JSONArrayToStringArray(settings.getJSONArray("validBoardingFlagships"));
            validTroopTransportShips = JSONArrayToStringArray(settings.getJSONArray("validTroopTransportShips"));
            validMiningShips = JSONArrayToStringArray(settings.getJSONArray("validMiningShips"));

            builtInFactions = JSONArrayToStringArray(settings.getJSONArray("builtInFactions"));
            supportedModFactions = JSONArrayToStringArray(settings.getJSONArray("supportedModFactions"));
        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR: Unable to load settings: " + e.getMessage());
        }

        // Reset and load faction configuration data
        if(ExerelinConfig.exerelinFactionConfigs != null)
            ExerelinConfig.exerelinFactionConfigs.clear();
        ExerelinConfig.exerelinFactionConfigs = new ArrayList<ExerelinFactionConfig>();

        for(String factionId : builtInFactions)
            ExerelinConfig.exerelinFactionConfigs.add(new ExerelinFactionConfig(factionId));

        for(String factionId : supportedModFactions)
            ExerelinConfig.exerelinFactionConfigs.add(new ExerelinFactionConfig(factionId));
    }

    public static ExerelinFactionConfig getExerelinFactionConfig(String factionId)
    {
        for(ExerelinFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(exerelinFactionConfig.factionId.equalsIgnoreCase(factionId))
                return exerelinFactionConfig;
        }

        return null;
    }

    public static List<String> getAllCustomFactionRebels()
    {
        List<String> customRebels = new ArrayList<String>();

        for(ExerelinFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(!exerelinFactionConfig.customRebelFaction.equalsIgnoreCase(""))
                customRebels.add(exerelinFactionConfig.customRebelFaction);
        }

        return  customRebels;
    }

    private static String[] JSONArrayToStringArray(JSONArray jsonArray)
    {
        try
        {
            return jsonArray.toString().substring(1, jsonArray.toString().length() - 1).replaceAll("\"","").split(",");
        }
        catch(Exception e)
        {
            return new String[]{};
        }
    }
}
