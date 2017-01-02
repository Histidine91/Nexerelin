package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.ExerelinSetupData;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

import static exerelin.utilities.ExerelinUtils.JSONArrayToStringArray;
import org.json.JSONArray;

public class ExerelinConfig
{
    public static final String CONFIG_PATH = "exerelin_config.json";
    public static final String MOD_FACTION_LIST_PATH = "data/config/exerelinFactionConfig/mod_factions.csv";
    
    public static List<ExerelinFactionConfig> exerelinFactionConfigs;
	public static ExerelinFactionConfig defaultConfig;

    // Threading support for improving/smoothing performance
    @Deprecated
    public static boolean enableThreading = true;
   
    // System Generation settings
    public static int minimumPlanets = 2;
    public static int minimumStations = 0;
    public static int minimumAsteroidBelts = 0;
    public static float binarySystemChance = 0.2f;
    public static float forcePiratesInSystemChance = 0.7f;
    public static boolean realisticStars = false;
	public static boolean enableIndependents = true;
	public static boolean enablePirates = true;

    // Player settings
    public static float playerBaseSalary = 5000f;
    public static float playerSalaryIncrementPerLevel = 1000f;
    public static float playerInsuranceMult = 0.5f;
    
    public static float fleetBonusFpPerPlayerLevel = 0.25f;
    
    // Prisoners
    public static float prisonerRepatriateRepValue = 0.05f;
    public static float prisonerBaseRansomValue = 2000f;
    public static float prisonerRansomValueIncrementPerLevel = 100f;
    public static float prisonerBaseSlaveValue = 4000f;
    public static float prisonerSlaveValueIncrementPerLevel = 400f;
    public static float prisonerSlaveRepValue = -0.02f;
    public static float prisonerLootChancePer10Fp = 0.04f;
    
    public static float crewLootMult = 0.02f;

    public static String[] builtInFactions = new String[]{};
    public static String[] supportedModFactions = new String[]{};
    
    // Invasion stuff
    public static boolean allowPirateInvasions = false;
    public static float fleetRequestCostPerMarine = 125f;
    public static float fleetRequestCostPerFP = 2000f;
    public static float invasionGracePeriod = 0;
    public static float pointsRequiredForInvasionFleet = 4000f;
    public static float baseInvasionPointsPerFaction = 45f;
    public static float invasionPointsPerPlayerLevel = 1f;
    public static float invasionPointEconomyMult = 1f;
	public static float conquestMissionRewardMult = 1f;
    
    // Alliances
    public static float allianceGracePeriod = 30;
    public static float allianceFormationInterval = 30f;
    public static boolean ignoreAlignmentForAlliances = false;
    
    // Prism Freeport
    public static int prismMaxWeapons = 27;
    public static int prismNumShips = 16;
    public static int prismNumBossShips = 3;
    public static boolean prismRenewBossShips = false;
    public static boolean prismUseIBBProgressForBossShips = true;
    public static float prismTariff = 2f;
    
    // War weariness
    public static float warWearinessDivisor = 20000f;
    public static float warWearinessDivisorModPerLevel = 200f;
    public static float minWarWearinessForPeace = 8000f;
    public static float warWearinessCeasefireReduction = 5000f;
    public static float warWearinessPeaceTreatyReduction = 8000f;
    
    // Followers faction
    public static boolean followersAgents = false;
    public static boolean followersDiplomacy = true;
    public static boolean followersAlliances = true;
    
    // Faction special stuff
    public static boolean enableAvesta = true;    // Association
    public static boolean enableShanghai = true;    // Tiandong
    public static boolean enableUnos = true;    // ApproLight
    
    // Misc
    public static float baseTariff = 0.2f;
    public static float freeMarketTariffMult = 0.5f;
    public static int warmongerPenalty = 0;
    public static float factionRespawnInterval = 30f;
    public static int maxFactionRespawns = 1;
    public static boolean countPiratesForVictory = false;
    public static boolean ownFactionCustomsInspections = false;
    public static int directoryDialogKey = 32;  // D
	public static boolean useRelationshipBounds = true;
    

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().loadJSON(CONFIG_PATH);

            minimumPlanets = settings.optInt("minimumPlanets");
            minimumStations = settings.optInt("minimumStations");
            minimumAsteroidBelts = settings.optInt("minimumAsteroidBelts");
            binarySystemChance = (float)settings.optDouble("binarySystemChance", binarySystemChance);
            forcePiratesInSystemChance = (float)settings.optDouble("piratesNotInSystemChance", forcePiratesInSystemChance);
            realisticStars = settings.optBoolean("realisticStars", realisticStars);
			enableIndependents = settings.optBoolean("enableIndependents", enableIndependents);
			enablePirates = settings.optBoolean("enablePirates", enablePirates);

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
            invasionPointsPerPlayerLevel = (float)settings.optDouble("invasionPointsPerPlayerLevel ", invasionPointsPerPlayerLevel );
            invasionPointEconomyMult = (float)settings.optDouble("invasionPointEconomyMult", invasionPointEconomyMult);
            conquestMissionRewardMult = (float)settings.optDouble("conquestMissionRewardMult", conquestMissionRewardMult);
            
            allianceGracePeriod = (float)settings.optDouble("allianceGracePeriod", allianceGracePeriod);
            allianceFormationInterval = (float)settings.optDouble("allianceFormationInterval", allianceFormationInterval);
            ignoreAlignmentForAlliances = settings.optBoolean("ignoreAlignmentForAlliances", ignoreAlignmentForAlliances);
            
            prismMaxWeapons = settings.optInt("prismMaxWeapons", prismMaxWeapons);
            prismNumShips = settings.optInt("prismNumShips", prismNumShips);
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
            followersAlliances = settings.optBoolean("followersAlliances", followersAlliances);
            
            enableAvesta = settings.optBoolean("enableAvesta", enableAvesta);
            enableShanghai = settings.optBoolean("enableShanghai", enableShanghai);
            enableUnos = settings.optBoolean("enableUnos", enableUnos);
            
            baseTariff = (float)settings.optDouble("baseTariff", baseTariff);
            freeMarketTariffMult = (float)settings.optDouble("freeMarketTariffMult", freeMarketTariffMult);
            warmongerPenalty = settings.optInt("warmongerPenalty", warmongerPenalty);
            factionRespawnInterval = (float)settings.optDouble("factionRespawnInterval", factionRespawnInterval);
            maxFactionRespawns = settings.optInt("maxFactionRespawns", maxFactionRespawns);
            countPiratesForVictory = settings.optBoolean("countPiratesForVictory", countPiratesForVictory);
            ownFactionCustomsInspections = settings.optBoolean("ownFactionCustomsInspections", ownFactionCustomsInspections);
            directoryDialogKey = settings.optInt("directoryDialogKey", directoryDialogKey);
			useRelationshipBounds = settings.optBoolean("useRelationshipBounds", useRelationshipBounds);
            
            builtInFactions = JSONArrayToStringArray(settings.getJSONArray("builtInFactions"));
            
            List<String> modFactions = new ArrayList<>();
            JSONArray modFactionsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("faction", MOD_FACTION_LIST_PATH, "nexerelin");
            for(int x = 0; x < modFactionsCsv.length(); x++)
            {
                JSONObject row = modFactionsCsv.getJSONObject(x);
                String factionName = row.getString("faction");
                modFactions.add(factionName);
            }
            supportedModFactions = modFactions.toArray(new String[]{});
        }
        catch(Exception e)
        {
            Global.getLogger(ExerelinConfig.class).error("Unable to load settings: " + e.getMessage());
        }

        // Reset and load faction configuration data
        if(ExerelinConfig.exerelinFactionConfigs != null)
            ExerelinConfig.exerelinFactionConfigs.clear();
        ExerelinConfig.exerelinFactionConfigs = new ArrayList<>();

        for(String factionId : builtInFactions) {
			ExerelinFactionConfig conf = new ExerelinFactionConfig(factionId);
			ExerelinConfig.exerelinFactionConfigs.add(conf);
			if (factionId.equals(Factions.NEUTRAL))
				defaultConfig = conf;
		}
            

        for(String factionId : supportedModFactions)
        {
            if (ExerelinSetupData.isFactionInstalled(factionId))
                ExerelinConfig.exerelinFactionConfigs.add(new ExerelinFactionConfig(factionId));
        }
    }

    public static ExerelinFactionConfig getExerelinFactionConfig(String factionId)
    {
        for(ExerelinFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(exerelinFactionConfig.factionId.equalsIgnoreCase(factionId))
                return exerelinFactionConfig;
        }

        Global.getLogger(ExerelinConfig.class).warn("Faction config " + factionId + "  not found, using default");
		return defaultConfig;
    }

	@Deprecated
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
