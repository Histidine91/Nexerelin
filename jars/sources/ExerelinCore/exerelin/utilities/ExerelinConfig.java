package exerelin.utilities;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

import static exerelin.utilities.ExerelinUtils.JSONArrayToStringArray;

public class ExerelinConfig
{
    public static List<ExerelinFactionConfig> exerelinFactionConfigs;

    // Threading support for improving/smoothing performance
    public static boolean enableThreading = true;
    // Use multiple larger backgrounds
    public static boolean useMultipleBackgroundsAndStars = true;
    // Use custom faction configs
    public static boolean useCustomFactionConfigs = true;
   
    // System Generation settings
    public static int minimumPlanets = 2;
    public static int minimumStations = 0;
    public static int minimumAsteroidBelts = 0;

    // Resourcing
    public static String asteroidMiningResource = "supplies";
    public static String gasgiantMiningResource = "fuel";
    public static String fleetCostResource = "supplies";
    public static int miningAmountPerDayPerMiner = 50;

    // Player settings
    public static float playerBaseSalary = 5000f;
    public static float playerSalaryIncrementPerLevel = 1000f;
    public static float playerInsuranceMult = 0.5f;
    
    public static float fleetBonusFpPerPlayerLevel = 1f;
    
    // Prisoners
    public static float prisonerRepatriateRepValue = 0.05f;
    public static float prisonerBaseRansomValue = 2000f;
    public static float prisonerRansomValueIncrementPerLevel = 200f;
    public static float prisonerBaseSlaveValue = 4000f;
    public static float prisonerSlaveValueIncrementPerLevel = 400f;
    public static float prisonerSlaveRepValue = -0.02f;
    public static float prisonerLootChancePer10Fp = 0.05f;
    
    public static float crewLootMult = 0.02f;
    
    public static float invasionFleetCostPerMarine = 500f;
    
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

            enableThreading = settings.optBoolean("enableThreading");
            useMultipleBackgroundsAndStars = settings.optBoolean("useMultipleBackgroundsAndStars");
            useCustomFactionConfigs = settings.optBoolean("useCustomFactionConfigs");

            minimumPlanets = settings.optInt("minimumPlanets");
            minimumStations = settings.optInt("minimumStations");
            minimumAsteroidBelts = settings.optInt("minimumAsteroidBelts");

            asteroidMiningResource = settings.optString("asteroidMiningResource");
            gasgiantMiningResource = settings.optString("gasgiantMiningResource");
            fleetCostResource = settings.optString("fleetCostResource");
            miningAmountPerDayPerMiner = settings.optInt("miningAmountPerDayPerMiner");

            playerBaseSalary = (float)settings.optDouble("playerBaseSalary");
            playerSalaryIncrementPerLevel = (float)settings.optDouble("playerSalaryIncrementPerLevel");
            playerInsuranceMult = (float)settings.optDouble("playerInsuranceMult");
            fleetBonusFpPerPlayerLevel = (float)settings.optDouble("fleetBonusFpPerPlayerLevel");
            
            prisonerRepatriateRepValue = (float)settings.optDouble("prisonerRepatriateRepValue");
            prisonerBaseRansomValue = (float)settings.optDouble("prisonerBaseRansomValue");
            prisonerRansomValueIncrementPerLevel = (float)settings.optDouble("prisonerRansomValueIncrementPerLevel");
            prisonerBaseSlaveValue = (float)settings.optDouble("prisonerBaseSlaveValue");
            prisonerSlaveValueIncrementPerLevel = (float)settings.optDouble("prisonerSlaveValueIncrementPerLevel");
            prisonerLootChancePer10Fp  = (float)settings.optDouble("prisonerLootChancePer10Fp");
            prisonerSlaveRepValue = (float)settings.optDouble("prisonerSlaveRepValue");

            crewLootMult = (float)settings.optDouble("crewLootMult");
            invasionFleetCostPerMarine = (float)settings.optDouble("invasionFleetCostPerMarine");
            
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
        ExerelinConfig.exerelinFactionConfigs = new ArrayList<>();

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

        System.out.println("EXERELIN ERROR: Faction config not found: " + factionId);
        return null;
    }

    public static ExerelinFactionConfig getExerelinFactionConfigForNiceName(String factionNiceName)
    {
        for(ExerelinFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(exerelinFactionConfig.factionNiceName.equalsIgnoreCase(factionNiceName))
                return exerelinFactionConfig;
        }

        System.out.println("EXERELIN ERROR: Faction config not found: " + factionNiceName);
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
}
