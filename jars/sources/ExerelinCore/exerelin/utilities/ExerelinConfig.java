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
    
    // Special Ships
    public static String[] validBoardingFlagships = new String[]{};
    public static String[] validTroopTransportShips = new String[]{};
    public static String[] validMiningShips = new String[]{};

    public static String[] builtInFactions = new String[]{};
    public static String[] supportedModFactions = new String[]{};
    
    // Invasion stuff
    public static boolean allowPirateInvasions = false;
    public static float fleetRequestCostPerMarine = 250f;
    public static float fleetRequestCostPerFP = 1000f;
    public static float invasionGracePeriod = 30f;
    public static float pointsRequiredForInvasionFleet = 5000f;
    public static float baseInvasionPointsPerFaction = 25f;
    
    // Alliances
    public static float allianceGracePeriod = 90f;
    public static float allianceFormationInterval = 30f;
    public static boolean ignoreAlignmentForAlliances = false;
    
    // Prism Freeport
    public static int prismMaxWeaponsPerFaction = 3;
    public static float prismNumShipsPerFaction = 0.5f;
    
    // War weariness
    public static float warWearinessDivisor = 10000f;
    public static float minWarWearinessForPeace = 4000f;
    public static float warWearinessCeasefireReduction = 3000f;
    public static float warWearinessPeaceTreatyReduction = 4500f;
    
    // Misc
    public static float factionRespawnInterval = 30f;
    public static boolean countPiratesForVictory = true;
    public static boolean ownFactionCustomsInspections = false;

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelin_config.json");

            enableThreading = settings.optBoolean("enableThreading", true);
            useMultipleBackgroundsAndStars = settings.optBoolean("useMultipleBackgroundsAndStars", true);
            useCustomFactionConfigs = settings.optBoolean("useCustomFactionConfigs", true);

            minimumPlanets = settings.optInt("minimumPlanets");
            minimumStations = settings.optInt("minimumStations");
            minimumAsteroidBelts = settings.optInt("minimumAsteroidBelts");

            asteroidMiningResource = settings.optString("asteroidMiningResource");
            gasgiantMiningResource = settings.optString("gasgiantMiningResource");
            fleetCostResource = settings.optString("fleetCostResource");
            miningAmountPerDayPerMiner = settings.optInt("miningAmountPerDayPerMiner");

            playerBaseSalary = (float)settings.optDouble("playerBaseSalary",  playerBaseSalary);
            playerSalaryIncrementPerLevel = (float)settings.optDouble("playerSalaryIncrementPerLevel", playerSalaryIncrementPerLevel);
            playerInsuranceMult = (float)settings.optDouble("playerInsuranceMult", playerInsuranceMult);
            fleetBonusFpPerPlayerLevel = (float)settings.optDouble("fleetBonusFpPerPlayerLevel", fleetBonusFpPerPlayerLevel);
            
            prisonerRepatriateRepValue = (float)settings.optDouble("prisonerRepatriateRepValue", prisonerRepatriateRepValue);
            prisonerBaseRansomValue = (float)settings.optDouble("prisonerBaseRansomValue", prisonerBaseRansomValue);
            prisonerRansomValueIncrementPerLevel = (float)settings.optDouble("prisonerRansomValueIncrementPerLevel", prisonerRansomValueIncrementPerLevel);
            prisonerBaseSlaveValue = (float)settings.optDouble("prisonerBaseSlaveValue", prisonerBaseSlaveValue);
            prisonerSlaveValueIncrementPerLevel = (float)settings.optDouble("prisonerSlaveValueIncrementPerLevel", prisonerSlaveValueIncrementPerLevel);
            prisonerLootChancePer10Fp  = (float)settings.optDouble("prisonerLootChancePer10Fp", prisonerLootChancePer10Fp);
            prisonerSlaveRepValue = (float)settings.optDouble("prisonerSlaveRepValue", prisonerSlaveRepValue);
            crewLootMult = (float)settings.optDouble("crewLootMult", crewLootMult);
            
            allowPirateInvasions = settings.optBoolean("allowPirateInvasions", allowPirateInvasions);
            fleetRequestCostPerMarine = (float)settings.optDouble("fleetRequestCostPerMarine", fleetRequestCostPerMarine);
            fleetRequestCostPerFP = (float)settings.optDouble("fleetRequestCostPerFP", fleetRequestCostPerFP);
            invasionGracePeriod = (float)settings.optDouble("invasionGracePeriod", invasionGracePeriod);
            pointsRequiredForInvasionFleet = (float)settings.optDouble("pointsRequiredForInvasionFleet", pointsRequiredForInvasionFleet);
            baseInvasionPointsPerFaction = (float)settings.optDouble("baseInvasionPointsPerFaction", baseInvasionPointsPerFaction);
            
            allianceGracePeriod = (float)settings.optDouble("allianceGracePeriod", allianceGracePeriod);
            allianceFormationInterval = (float)settings.optDouble("allianceFormationInterval", allianceFormationInterval);
            ignoreAlignmentForAlliances = settings.optBoolean("ignoreAlignmentForAlliances", ignoreAlignmentForAlliances);
            
            prismMaxWeaponsPerFaction = settings.optInt("prismMaxWeaponsPerFaction", prismMaxWeaponsPerFaction);
            prismNumShipsPerFaction = (float)settings.optDouble("prismNumShipsPerFaction", prismNumShipsPerFaction);
            
            warWearinessDivisor = (float)settings.optDouble("warWearinessDivisor", warWearinessDivisor);
            minWarWearinessForPeace = (float)settings.optDouble("minWarWearinessForPeace", minWarWearinessForPeace);
            warWearinessCeasefireReduction = (float)settings.optDouble("warWearinessCeasefireReduction", warWearinessCeasefireReduction);
            warWearinessPeaceTreatyReduction = (float)settings.optDouble("warWearinessCeasefireReduction", warWearinessCeasefireReduction);
            
            factionRespawnInterval = (float)settings.optDouble("factionRespawnInterval", factionRespawnInterval);
            countPiratesForVictory = settings.optBoolean("countPiratesForVictory", countPiratesForVictory);
            ownFactionCustomsInspections = settings.optBoolean("ownFactionCustomsInspections", ownFactionCustomsInspections);
            
            builtInFactions = JSONArrayToStringArray(settings.getJSONArray("builtInFactions"));
            supportedModFactions = JSONArrayToStringArray(settings.getJSONArray("supportedModFactions"));
        }
        catch(Exception e)
        {
            Global.getLogger(ExerelinConfig.class).error("Unable to load settings: " + e.getMessage());
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

        Global.getLogger(ExerelinConfig.class).warn("Faction config not found: " + factionId);
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
