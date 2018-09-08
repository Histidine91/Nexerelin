package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.ExerelinConstants;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

import static exerelin.utilities.ExerelinUtils.JSONArrayToStringArray;
import java.io.IOException;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;

public class ExerelinConfig
{
    public static final String CONFIG_PATH = "exerelin_config.json";
    public static final String MOD_FACTION_LIST_PATH = "data/config/exerelinFactionConfig/mod_factions.csv";
    
    public static Logger log = Global.getLogger(ExerelinConfig.class);
    public static List<ExerelinFactionConfig> exerelinFactionConfigs;
    public static ExerelinFactionConfig defaultConfig;
   
    // System Generation settings
    public static int minimumPlanets = 3;
    public static float forcePiratesInSystemChance = 0.3f;

    // Player settings
    public static float playerBaseSalary = 5000f;
    public static float playerSalaryIncrementPerLevel = 1000f;
    public static float playerInsuranceMult = 0.5f;
    public static float followersBaseTax = 100f;
    
    public static float fleetBonusFpPerPlayerLevel = 0.25f;
    
    // Prisoners
    public static float prisonerRepatriateRepValue = 0.05f;
    public static float prisonerBaseRansomValue = 2000f;
    public static float prisonerRansomValueIncrementPerLevel = 100f;
    public static float prisonerBaseSlaveValue = 4000f;
    public static float prisonerSlaveValueIncrementPerLevel = 400f;
    public static float prisonerSlaveRepValue = -0.02f;
    public static float prisonerLootChancePer10Fp = 0.025f;
    
    public static float crewLootMult = 0.02f;

    public static String[] builtInFactions = new String[]{};
    public static String[] supportedModFactions = new String[]{};
    
    // Invasion stuff
    public static boolean allowPirateInvasions = false;
	public static boolean retakePirateMarkets = true;
    public static float fleetRequestCostPerMarine = 125f;
    public static float fleetRequestCostPerFP = 2000f;
    public static float invasionGracePeriod = 0;
    public static float pointsRequiredForInvasionFleet = 4000f;
    public static float baseInvasionPointsPerFaction = 45f;
    public static float invasionPointsPerPlayerLevel = 1f;
    public static float invasionPointEconomyMult = 1f;
    public static float conquestMissionRewardMult = 1f;
    public static float invasionLootMult = 0.05f;
    
    // Alliances
    public static float allianceGracePeriod = 30;
    public static float allianceFormationInterval = 30f;
    public static boolean ignoreAlignmentForAlliances = false;
    
    // Prism Freeport
    public static int prismMaxWeapons = 27;
    public static int prismNumShips = 14;
    public static int prismNumWings = 6;
    public static int prismNumBossShips = 3;
    public static boolean prismRenewBossShips = false;
    public static boolean prismUseIBBProgressForBossShips = true;
    public static float prismTariff = 2f;
    
    // War weariness
    public static float warWearinessDivisor = 10000f;
    public static float warWearinessDivisorModPerLevel = 75f;
    public static float minWarWearinessForPeace = 5000f;
    public static float warWearinessCeasefireReduction = 3000f;
    public static float warWearinessPeaceTreatyReduction = 6000f;
    
    // Followers faction
    public static boolean followersAgents = false;
    public static boolean followersDiplomacy = true;
    
    // Faction special stuff
    public static boolean enableAvesta = true;    // Association
    public static boolean enableShanghai = true;    // Tiandong
    public static boolean enableUnos = true;    // ApproLight
    
    // Revengeance fleets
    public static int enableRevengeFleets = 1;
    public static float revengePointsPerEnemyFP = 0.05f;
    public static float revengePointsForMarketCaptureMult = 2f;
    
    // Misc
    public static int directoryDialogKey = 32;  // D
    
    public static float baseTariffMult = 1;
    public static float freeMarketTariffMult = 0.5f;
    public static int warmongerPenalty = 0;
    public static float factionRespawnInterval = 30f;
    public static int maxFactionRespawns = 1;
    public static boolean countPiratesForVictory = false;
    public static boolean leaveEliminatedFaction = true;
    @Deprecated
    public static boolean ownFactionCustomsInspections = false;
    public static boolean useRelationshipBounds = true;
    public static boolean officerDeaths = true;
    public static boolean officerDaredevilBonus = true;
    public static boolean corvusModeLandmarks = false;  

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().loadJSON(CONFIG_PATH);
            
            directoryDialogKey = settings.optInt("directoryDialogKey", directoryDialogKey);

            minimumPlanets = settings.optInt("minimumPlanets");
            forcePiratesInSystemChance = (float)settings.optDouble("piratesNotInSystemChance", forcePiratesInSystemChance);

            playerBaseSalary = (float)settings.optDouble("playerBaseSalary",  playerBaseSalary);
            playerSalaryIncrementPerLevel = (float)settings.optDouble("playerSalaryIncrementPerLevel", playerSalaryIncrementPerLevel);
            playerInsuranceMult = (float)settings.optDouble("playerInsuranceMult", playerInsuranceMult);
            followersBaseTax = (float)settings.optDouble("followersBaseTax", followersBaseTax);
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
			retakePirateMarkets = settings.optBoolean("retakePirateMarkets", retakePirateMarkets);
            fleetRequestCostPerMarine = (float)settings.optDouble("fleetRequestCostPerMarine", fleetRequestCostPerMarine);
            fleetRequestCostPerFP = (float)settings.optDouble("fleetRequestCostPerFP", fleetRequestCostPerFP);
            invasionGracePeriod = (float)settings.optDouble("invasionGracePeriod", invasionGracePeriod);
            pointsRequiredForInvasionFleet = (float)settings.optDouble("pointsRequiredForInvasionFleet", pointsRequiredForInvasionFleet);
            baseInvasionPointsPerFaction = (float)settings.optDouble("baseInvasionPointsPerFaction", baseInvasionPointsPerFaction);
            invasionPointsPerPlayerLevel = (float)settings.optDouble("invasionPointsPerPlayerLevel ", invasionPointsPerPlayerLevel );
            invasionPointEconomyMult = (float)settings.optDouble("invasionPointEconomyMult", invasionPointEconomyMult);
            conquestMissionRewardMult = (float)settings.optDouble("conquestMissionRewardMult", conquestMissionRewardMult);
            invasionLootMult = (float)settings.optDouble("invasionLootMult", invasionLootMult);
            
            allianceGracePeriod = (float)settings.optDouble("allianceGracePeriod", allianceGracePeriod);
            allianceFormationInterval = (float)settings.optDouble("allianceFormationInterval", allianceFormationInterval);
            ignoreAlignmentForAlliances = settings.optBoolean("ignoreAlignmentForAlliances", ignoreAlignmentForAlliances);
            
            prismMaxWeapons = settings.optInt("prismMaxWeapons", prismMaxWeapons);
            prismNumShips = settings.optInt("prismNumShips", prismNumShips);
            prismNumWings = settings.optInt("prismNumWings", prismNumWings);
            //prismSellBossShips = settings.optBoolean("prismSellBossShips", prismSellBossShips);
            prismNumBossShips = settings.optInt("prismNumBossShips", prismNumBossShips);
            prismRenewBossShips = settings.optBoolean("prismRenewBossShips", prismRenewBossShips);
            prismUseIBBProgressForBossShips = settings.optBoolean("prismUseIBBProgressForBossShips", prismUseIBBProgressForBossShips);
            prismTariff = (float)settings.optDouble("prismTariff", prismTariff);
            
            warWearinessDivisor = (float)settings.optDouble("warWearinessDivisor", warWearinessDivisor);
            warWearinessDivisorModPerLevel = (float)settings.optDouble("warWearinessDivisorModPerLevel", warWearinessDivisorModPerLevel);
            minWarWearinessForPeace = (float)settings.optDouble("minWarWearinessForPeace", minWarWearinessForPeace);
            warWearinessCeasefireReduction = (float)settings.optDouble("warWearinessCeasefireReduction", warWearinessCeasefireReduction);
            warWearinessPeaceTreatyReduction = (float)settings.optDouble("warWearinessCeasefireReduction", warWearinessCeasefireReduction);
            
            followersAgents = settings.optBoolean("followersAgents", followersAgents);
            followersDiplomacy = settings.optBoolean("followersDiplomacy", followersDiplomacy);
            
            enableAvesta = settings.optBoolean("enableAvesta", enableAvesta);
            enableShanghai = settings.optBoolean("enableShanghai", enableShanghai);
            enableUnos = settings.optBoolean("enableUnos", enableUnos);
            
            enableRevengeFleets = settings.optInt("enableRevengeFleets", enableRevengeFleets);
            revengePointsPerEnemyFP = (float)settings.optDouble("revengeFleetPointsPerEnemyFP", revengePointsPerEnemyFP);
            revengePointsForMarketCaptureMult = (float)settings.optDouble("revengeFleetPointsForMarketCaptureMult", revengePointsForMarketCaptureMult);
            
            baseTariffMult = (float)settings.optDouble("baseTariffMult", baseTariffMult);
            freeMarketTariffMult = (float)settings.optDouble("freeMarketTariffMult", freeMarketTariffMult);
            warmongerPenalty = settings.optInt("warmongerPenalty", warmongerPenalty);
            factionRespawnInterval = (float)settings.optDouble("factionRespawnInterval", factionRespawnInterval);
            maxFactionRespawns = settings.optInt("maxFactionRespawns", maxFactionRespawns);
            countPiratesForVictory = settings.optBoolean("countPiratesForVictory", countPiratesForVictory);
            leaveEliminatedFaction = settings.optBoolean("leaveEliminatedFaction", leaveEliminatedFaction);
            
            useRelationshipBounds = settings.optBoolean("useRelationshipBounds", useRelationshipBounds);
            
            officerDeaths = settings.optBoolean("officerDeaths", officerDeaths);
            officerDaredevilBonus = settings.optBoolean("officerDaredevilBonus", officerDaredevilBonus);
            
            corvusModeLandmarks = settings.optBoolean("corvusModeLandmarks", corvusModeLandmarks);
            
            builtInFactions = JSONArrayToStringArray(settings.getJSONArray("builtInFactions"));
            
            loadModFactionList();
        }
        catch(Exception e)
        {
            log.error("Unable to load settings: " + e.getMessage());
        }

        // Reset and load faction configuration data
        if(ExerelinConfig.exerelinFactionConfigs != null)
            ExerelinConfig.exerelinFactionConfigs.clear();
        ExerelinConfig.exerelinFactionConfigs = new ArrayList<>();

        for(String factionId : builtInFactions) {
            ExerelinFactionConfig conf = new ExerelinFactionConfig(factionId);
            conf.isBuiltIn = true;
            ExerelinConfig.exerelinFactionConfigs.add(conf);
            if (factionId.equals(Factions.NEUTRAL))
                defaultConfig = conf;
        }

        for(String factionId : supportedModFactions)
        {
            if (ExerelinUtilsFaction.doesFactionExist(factionId))
                ExerelinConfig.exerelinFactionConfigs.add(new ExerelinFactionConfig(factionId));
        }
    }
    
    protected static void loadModFactionList()
    {
        try {
            List<String> modFactions = new ArrayList<>();
            JSONArray modFactionsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("faction", MOD_FACTION_LIST_PATH, ExerelinConstants.MOD_ID);
            for(int x = 0; x < modFactionsCsv.length(); x++)
            {
                JSONObject row = modFactionsCsv.getJSONObject(x);
                String factionName = row.getString("faction");
                modFactions.add(factionName);
            }
            supportedModFactions = modFactions.toArray(new String[]{});
        } catch (IOException | JSONException ex) {
            log.error("Failed to load mod faction file: " + ex);
        }
    }

    public static ExerelinFactionConfig getExerelinFactionConfig(String factionId)
    {
        for(ExerelinFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(exerelinFactionConfig.factionId.equalsIgnoreCase(factionId))
                return exerelinFactionConfig;
        }

        Global.getLogger(ExerelinConfig.class).warn("Faction config " + factionId + " not found, using default");
        return defaultConfig;
    }

    @Deprecated
    public static List<String> getAllCustomFactionRebels()
    {
        List<String> customRebels = new ArrayList<>();

        for(ExerelinFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(!exerelinFactionConfig.customRebelFaction.equalsIgnoreCase(""))
                customRebels.add(exerelinFactionConfig.customRebelFaction);
        }

        return customRebels;
    }
    
    public static List<String> getFactions(boolean onlyPlayable, boolean onlyStartable)
    {
        List<String> factions = new ArrayList<>();

        for (ExerelinFactionConfig config : exerelinFactionConfigs) {
            if (onlyPlayable && !config.playableFaction)
                continue;
            if (onlyStartable && !config.startingFaction)
                continue;
            if (ExerelinUtilsFaction.doesFactionExist(config.factionId))
            {
                factions.add(config.factionId);
            }
        }
        return factions;
    }
}
