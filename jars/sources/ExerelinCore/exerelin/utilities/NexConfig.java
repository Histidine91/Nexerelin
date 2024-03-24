package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.ExerelinConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static exerelin.utilities.NexUtils.JSONArrayToStringArray;

public class NexConfig
{
    public static final String CONFIG_PATH = "exerelin_config.json";
    public static final String MOD_FACTION_LIST_PATH = "data/config/exerelinFactionConfig/mod_factions.csv";
    
    public static Logger log = Global.getLogger(NexConfig.class);
    public static List<NexFactionConfig> exerelinFactionConfigs;
    public static NexFactionConfig defaultConfig;
   
    // System Generation settings
    public static int minimumPlanets = 3;
    public static float forcePiratesInSystemChance = 0.3f;
    
    // Player settings
    public static float playerInsuranceMult = 0.8f;
    public static boolean legacyInsurance = false;
    
    public static float fleetBonusFpPerPlayerLevel = 1f;
    
    // Prisoners
    public static float prisonerRepatriateRepValue = 0.04f;
    public static float prisonerBaseRansomValue = 10000f;
    public static float prisonerRansomValueIncrementPerLevel = 2000f;
    public static float prisonerLootChancePer10Fp = 0.025f;
    public static float crewLootMult = 0.02f;
    
    // Agents
    public static int agentBaseSalary = 2000;
    public static int agentSalaryPerLevel = 1000;
    public static int maxAgents = 2;
    public static boolean agentStealAllShips = true;
    public static boolean useAgentSpecializations = true;

    public static String[] builtInFactions = new String[]{};
    public static String[] supportedModFactions = new String[]{};
    
    // Invasion stuff
    public static boolean enableHostileFleetEvents = true;
    public static boolean enableInvasions = true;
    public static boolean legacyInvasions = false;
    public static boolean invasionsOnlyAfterPlayerColony = false;
    public static boolean allowInvadeStoryCritical = false;
    public static boolean allowInvadeStartingMarkets = true;
    public static boolean allowPirateInvasions = false;
    public static boolean retakePirateMarkets = true;
    public static float fleetRequestCostPerFP = 400f;
    public static float fleetRequestCapMult = 1;
    public static float fleetRequestIncrementMult = 1;
    public static float invasionFleetSizeMult = 1;
    public static float responseFleetSizeMult = 1;
    public static float invasionGracePeriod = 90;
    public static float pointsRequiredForInvasionFleet = 27000f;
    public static float baseInvasionPointsPerFaction = 30f;
    public static float invasionPointsPerPlayerLevel = 1;
    public static float invasionPointEconomyMult = 0.5f;
    public static float creditLossOnColonyLossMult = 0.5f;
    public static boolean allowNPCSatBomb = true;
    public static float permaHateFromPlayerSatBomb = 0.2f;

    // ground battles
    public static float groundBattleDamageMult = 1;
    public static float groundBattleGarrisonSizeMult = 1;
    public static float groundBattleGarrisonXP = 0.25f;
    public static float groundBattleInvasionTroopSizeMult = 1;
    public static float groundBattleInvasionTroopXP = 0.5f;

    // Diplomacy
    public static boolean enableDiplomacy = true;
    public static boolean allowRandomDiplomacyTraits = true;
    
    // Alliances
    public static boolean enableAlliances = true;
    public static float allianceGracePeriod = 120;
    public static float allianceFormationInterval = 30f;
    public static boolean ignoreAlignmentForAlliances = false;
    public static float predefinedAllianceNameChance = 1;
    public static boolean npcAllianceOffers = true;
    
    // Prism Freeport
    public static boolean prismInHyperspace = false;
    public static int prismMaxWeapons = 27;
    public static int prismNumShips = 14;
    public static int prismNumWings = 6;
    public static int prismNumBossShips = 3;
    public static boolean prismRenewBossShips = false;
    public static boolean prismUseIBBProgressForBossShips = true;
    public static float prismTariff = 2f;
    public static float prismBlueprintPriceMult = 1.5f;
    
    // War weariness
    public static float warWearinessDivisor = 10000f;
    public static float warWearinessDivisorModPerLevel = 75f;
    public static float minWarWearinessForPeace = 5000f;
    public static float warWearinessCeasefireReduction = 3000f;
    public static float warWearinessPeaceTreatyReduction = 6000f;
    public static boolean acceptCeasefiresOnTimeout = false;
    
    // Followers faction
    public static boolean followersAgents = false;
    public static boolean followersDiplomacy = true;
    public static boolean followersInvasions = false;
    
    // Faction special stuff
    public static boolean enableAvesta = true;    // Association
    //public static boolean enableShanghai = true;    // Tiandong
    public static boolean enableUnos = true;    // ApproLight
    public static boolean factionRuler = false;
    
    // Revengeance fleets
    public static int enableRevengeFleets = 2;
    public static float revengePointsPerEnemyFP = 0.05f;
    public static float revengePointsForMarketCaptureMult = 2f;
    public static float vengeanceFleetSizeMult = 0.8f;
    public static boolean useNewVengeanceEncounters = false;
    
    // Combat
    @Deprecated public static boolean useCustomBattleCreationPlugin = true;
    public static boolean officerDeaths = false;
    public static boolean officerDaredevilBonus = false;
    
    // Colonies
    public static int maxNPCNewColonySize = 6;
    public static float hardModeColonyGrowthMult = 0.75f;
    public static float hardModeColonyIncomeMult = 0.9f;
    public static boolean enableColonyExpeditions = true;
    public static float colonyExpeditionInterval = 270;
    public static boolean colonyExpeditionsOnlyAfterPlayerColony = false;
    
    public static float specialForcesPointMult = 1;
    public static float specialForcesSizeMult = 1;
    
    // Misc
    public static int directoryDialogKey = 45;  // X
    public static boolean ceasefireNotificationPopup = true;
    public static int diplomacyEventFilterLevel = 0;
    public static int agentEventFilterLevel = 0;
    public static int nexIntelQueued = 0;
    public static boolean queuedNexMissions = false;
    public static boolean enableStrategicAI = true;
    public static boolean showStrategicAI = true;
	public static boolean enableVictory = true;
    
    public static float baseTariffMult = 0.6f;
    public static float freeMarketTariffMult = 0.5f;
    public static boolean doubleSubmarketWeapons = false;
    public static int warmongerPenalty = 0;
    public static float factionRespawnInterval = 120;
    public static int maxFactionRespawns = 3;
    public static boolean countPiratesForVictory = false;
    public static boolean leaveEliminatedFaction = true;
    public static boolean useRelationshipBounds = true;
    public static boolean useConfigRelationshipsInNonRandomSector = false;
    public static boolean useEnhancedStartRelations = true;
    public static boolean useEnhancedCoreWorlds = true;
    public static boolean useEnhancedAdmins = true;
    public static boolean corvusModeLandmarks = false;
    public static int stabilizePackageEffect = 3;
    public static float rebellionMult = 1;
    public static boolean enablePunitiveExpeditions = true;
    public static boolean autoResistAIInspections = true;
    public static boolean allyVictories = true;
    public static boolean updateMarketDescOnCapture = true;

    public static void loadSettings()
    {
        try
        {
            System.out.println("Loading exerelinSettings");

            JSONObject settings = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, ExerelinConstants.MOD_ID);
            
            directoryDialogKey = settings.optInt("directoryDialogKey", directoryDialogKey);

            minimumPlanets = settings.optInt("minimumPlanets");
            forcePiratesInSystemChance = (float)settings.optDouble("piratesNotInSystemChance", forcePiratesInSystemChance);
            
            playerInsuranceMult = (float)settings.optDouble("playerInsuranceMult", playerInsuranceMult);
            legacyInsurance = settings.optBoolean("legacyInsurance", legacyInsurance);
            fleetBonusFpPerPlayerLevel = (float)settings.optDouble("fleetBonusFpPerPlayerLevel", fleetBonusFpPerPlayerLevel);
            
            prisonerRepatriateRepValue = (float)settings.optDouble("prisonerRepatriateRepValue", prisonerRepatriateRepValue);
            prisonerBaseRansomValue = (float)settings.optDouble("prisonerBaseRansomValue", prisonerBaseRansomValue);
            prisonerRansomValueIncrementPerLevel = (float)settings.optDouble("prisonerRansomValueIncrementPerLevel", prisonerRansomValueIncrementPerLevel);
            prisonerLootChancePer10Fp  = (float)settings.optDouble("prisonerLootChancePer10Fp", prisonerLootChancePer10Fp);
            crewLootMult = (float)settings.optDouble("crewLootMult", crewLootMult);
            
            agentBaseSalary = settings.optInt("agentBaseSalary", agentBaseSalary);
            agentSalaryPerLevel = settings.optInt("agentSalaryPerLevel", agentSalaryPerLevel);
            maxAgents = settings.optInt("maxAgents", maxAgents);
            agentStealAllShips = settings.optBoolean("agentStealAllShips", agentStealAllShips);
            useAgentSpecializations = settings.optBoolean("useAgentSpecializations", useAgentSpecializations);

            enableHostileFleetEvents = settings.optBoolean("enableHostileFleetEvents", enableHostileFleetEvents);
            enableInvasions = settings.optBoolean("enableInvasions", enableInvasions);
            legacyInvasions = settings.optBoolean("legacyInvasions", legacyInvasions);
			invasionsOnlyAfterPlayerColony = settings.optBoolean("invasionsOnlyAfterPlayerColony", invasionsOnlyAfterPlayerColony);
            allowInvadeStoryCritical = settings.optBoolean("allowInvadeStoryCritical", allowInvadeStoryCritical);
            allowInvadeStartingMarkets = settings.optBoolean("allowInvadeStartingMarkets", allowInvadeStartingMarkets);
            allowPirateInvasions = settings.optBoolean("allowPirateInvasions", allowPirateInvasions);
            retakePirateMarkets = settings.optBoolean("retakePirateMarkets", retakePirateMarkets);
            fleetRequestCostPerFP = (float)settings.optDouble("fleetRequestCostPerFP", fleetRequestCostPerFP);
            fleetRequestCapMult = (float)settings.optDouble("fleetRequestCapMult", fleetRequestCapMult);
            fleetRequestIncrementMult = (float)settings.optDouble("fleetRequestIncrementMult", fleetRequestIncrementMult);
            invasionFleetSizeMult = (float)settings.optDouble("invasionFleetSizeMult", invasionFleetSizeMult);
            responseFleetSizeMult = (float)settings.optDouble("responseFleetSizeMult", responseFleetSizeMult);
            invasionGracePeriod = (float)settings.optDouble("invasionGracePeriod", invasionGracePeriod);
            pointsRequiredForInvasionFleet = (float)settings.optDouble("pointsRequiredForInvasionFleet", pointsRequiredForInvasionFleet);
            baseInvasionPointsPerFaction = (float)settings.optDouble("baseInvasionPointsPerFaction", baseInvasionPointsPerFaction);
            invasionPointsPerPlayerLevel = (float)settings.optDouble("invasionPointsPerPlayerLevel", invasionPointsPerPlayerLevel );
            invasionPointEconomyMult = (float)settings.optDouble("invasionPointEconomyMult", invasionPointEconomyMult);
            creditLossOnColonyLossMult = (float)settings.optDouble("creditLossOnColonyLossMult", creditLossOnColonyLossMult);
            allowNPCSatBomb = settings.optBoolean("allowNPCSatBomb", allowNPCSatBomb);
            permaHateFromPlayerSatBomb = (float)settings.optDouble("permaHateFromPlayerSatBomb", permaHateFromPlayerSatBomb);

            groundBattleDamageMult = (float)settings.optDouble("groundBattleDamageMult", groundBattleDamageMult);
            groundBattleGarrisonSizeMult = (float)settings.optDouble("groundBattleGarrisonSizeMult", groundBattleGarrisonSizeMult);
            groundBattleGarrisonXP = (float)settings.optDouble("groundBattleGarrisonXP", groundBattleGarrisonXP);
            groundBattleInvasionTroopSizeMult = (float)settings.optDouble("groundBattleInvasionTroopSizeMult", groundBattleInvasionTroopSizeMult);
            groundBattleInvasionTroopXP = (float)settings.optDouble("groundBattleInvasionTroopXP", groundBattleInvasionTroopXP);

            enableDiplomacy = settings.optBoolean("enableDiplomacy", enableDiplomacy);
            allowRandomDiplomacyTraits = settings.optBoolean("allowRandomDiplomacyTraits", allowRandomDiplomacyTraits);
            useRelationshipBounds = settings.optBoolean("useRelationshipBounds", useRelationshipBounds);

            enableAlliances = settings.optBoolean("enableAlliances", enableAlliances);
            allianceGracePeriod = (float)settings.optDouble("allianceGracePeriod", allianceGracePeriod);
            allianceFormationInterval = (float)settings.optDouble("allianceFormationInterval", allianceFormationInterval);
            ignoreAlignmentForAlliances = settings.optBoolean("ignoreAlignmentForAlliances", ignoreAlignmentForAlliances);
            predefinedAllianceNameChance = (float)settings.optDouble("predefinedAllianceNameChance", predefinedAllianceNameChance);
            npcAllianceOffers = settings.optBoolean("npcAllianceOffers", npcAllianceOffers);
            
            prismInHyperspace = settings.optBoolean("prismInHyperspace", prismInHyperspace);
            prismMaxWeapons = settings.optInt("prismMaxWeapons", prismMaxWeapons);
            prismNumShips = settings.optInt("prismNumShips", prismNumShips);
            prismNumWings = settings.optInt("prismNumWings", prismNumWings);
            prismNumBossShips = settings.optInt("prismNumBossShips", prismNumBossShips);
            prismRenewBossShips = settings.optBoolean("prismRenewBossShips", prismRenewBossShips);
            prismUseIBBProgressForBossShips = settings.optBoolean("prismUseIBBProgressForBossShips", prismUseIBBProgressForBossShips);
            prismTariff = (float)settings.optDouble("prismTariff", prismTariff);
            prismBlueprintPriceMult = (float)settings.optDouble("prismBlueprintPriceMult", prismBlueprintPriceMult);
            
            warWearinessDivisor = (float)settings.optDouble("warWearinessDivisor", warWearinessDivisor);
            warWearinessDivisorModPerLevel = (float)settings.optDouble("warWearinessDivisorModPerLevel", warWearinessDivisorModPerLevel);
            minWarWearinessForPeace = (float)settings.optDouble("minWarWearinessForPeace", minWarWearinessForPeace);
            warWearinessCeasefireReduction = (float)settings.optDouble("warWearinessCeasefireReduction", warWearinessCeasefireReduction);
            warWearinessPeaceTreatyReduction = (float)settings.optDouble("warWearinessCeasefireReduction", warWearinessCeasefireReduction);
            acceptCeasefiresOnTimeout = settings.optBoolean("acceptCeasefiresOnTimeout", acceptCeasefiresOnTimeout);
            
            followersAgents = settings.optBoolean("followersAgents", followersAgents);
            followersDiplomacy = settings.optBoolean("followersDiplomacy", followersDiplomacy);
            followersInvasions = settings.optBoolean("followersInvasions", followersInvasions);
            
            enableAvesta = settings.optBoolean("enableAvesta", enableAvesta);
            //enableShanghai = settings.optBoolean("enableShanghai", enableShanghai);
            enableUnos = settings.optBoolean("enableUnos", enableUnos);
            factionRuler = settings.optBoolean("factionRuler", factionRuler);
            
            enableRevengeFleets = settings.optInt("enableRevengeFleets", enableRevengeFleets);
            revengePointsPerEnemyFP = (float)settings.optDouble("revengeFleetPointsPerEnemyFP", revengePointsPerEnemyFP);
            revengePointsForMarketCaptureMult = (float)settings.optDouble("revengeFleetPointsForMarketCaptureMult", revengePointsForMarketCaptureMult);
            vengeanceFleetSizeMult = (float)settings.optDouble("vengeanceFleetSizeMult", vengeanceFleetSizeMult);
            useNewVengeanceEncounters = settings.optBoolean("useNewVengeanceEncounters", useNewVengeanceEncounters);
            
            maxNPCNewColonySize = settings.optInt("maxNPCNewColonySize", maxNPCNewColonySize);
            hardModeColonyGrowthMult = (float)settings.optDouble("hardModeColonyGrowthMult", hardModeColonyGrowthMult);
            hardModeColonyIncomeMult = (float)settings.optDouble("hardModeColonyIncomeMult", hardModeColonyIncomeMult);
            enableColonyExpeditions = settings.optBoolean("enableColonyExpeditions", enableColonyExpeditions);
            colonyExpeditionInterval = (float)settings.optDouble("colonyExpeditionInterval", colonyExpeditionInterval);
            colonyExpeditionsOnlyAfterPlayerColony = settings.optBoolean("colonyExpeditionsOnlyAfterPlayerColony", colonyExpeditionsOnlyAfterPlayerColony);
            
            specialForcesPointMult = (float)settings.optDouble("specialForcesPointMult", specialForcesPointMult);
            specialForcesSizeMult = (float)settings.optDouble("specialForcesPointMult", specialForcesSizeMult);
            
            baseTariffMult = (float)settings.optDouble("baseTariffMult", baseTariffMult);
            freeMarketTariffMult = (float)settings.optDouble("freeMarketTariffMult", freeMarketTariffMult);
            doubleSubmarketWeapons = settings.optBoolean("doubleSubmarketWeapons", doubleSubmarketWeapons);
            warmongerPenalty = settings.optInt("warmongerPenalty", warmongerPenalty);
            factionRespawnInterval = (float)settings.optDouble("factionRespawnInterval", factionRespawnInterval);
            maxFactionRespawns = settings.optInt("maxFactionRespawns", maxFactionRespawns);
            countPiratesForVictory = settings.optBoolean("countPiratesForVictory", countPiratesForVictory);
            leaveEliminatedFaction = settings.optBoolean("leaveEliminatedFaction", leaveEliminatedFaction);
            stabilizePackageEffect = settings.optInt("stabilizePackageEffect", stabilizePackageEffect);
            rebellionMult = (float)settings.optDouble("rebellionMult", rebellionMult);
            enablePunitiveExpeditions = settings.optBoolean("enablePunitiveExpeditions", enablePunitiveExpeditions);
            autoResistAIInspections = settings.optBoolean("autoResistAIInspections", autoResistAIInspections);
            allyVictories = settings.optBoolean("allyVictories", allyVictories);
            updateMarketDescOnCapture = settings.optBoolean("updateMarketDescOnCapture", updateMarketDescOnCapture);

            useEnhancedStartRelations = settings.optBoolean("useEnhancedStartRelations", useEnhancedStartRelations);
            useConfigRelationshipsInNonRandomSector = settings.optBoolean("useConfigRelationshipsInNonRandomSector", useConfigRelationshipsInNonRandomSector);
            useEnhancedCoreWorlds = settings.optBoolean("useEnhancedCoreWorlds", useEnhancedCoreWorlds);
            useEnhancedAdmins = settings.optBoolean("useEnhancedAdmins", useEnhancedAdmins);
            
            useCustomBattleCreationPlugin = settings.optBoolean("useCustomBattleCreationPlugin", useCustomBattleCreationPlugin);
            officerDeaths = settings.optBoolean("officerDeaths", officerDeaths);
            officerDaredevilBonus = settings.optBoolean("officerDaredevilBonus", officerDaredevilBonus);
            
            corvusModeLandmarks = settings.optBoolean("corvusModeLandmarks", corvusModeLandmarks);
            
            ceasefireNotificationPopup = settings.optBoolean("ceasefireNotificationPopup", ceasefireNotificationPopup);
            diplomacyEventFilterLevel = settings.optInt("diplomacyEventFilterLevel", diplomacyEventFilterLevel);
            agentEventFilterLevel = settings.optInt("agentEventFilterLevel", agentEventFilterLevel);
            nexIntelQueued = settings.optInt("nexIntelQueued", nexIntelQueued);
            queuedNexMissions = settings.optBoolean("queuedNexMissions", queuedNexMissions);
            enableStrategicAI = settings.optBoolean("enableStrategicAI", enableStrategicAI);
            showStrategicAI = settings.optBoolean("showStrategicAI", showStrategicAI);
            enableVictory = settings.optBoolean("enableVictory", enableVictory);
            
            builtInFactions = JSONArrayToStringArray(settings.getJSONArray("builtInFactions"));
            
            loadModFactionList();
        }
        catch(Exception e)
        {
            throw new RuntimeException("Failed to load config: " + e.getMessage(), e);
        }

        // Reset and load faction configuration data
        if(NexConfig.exerelinFactionConfigs != null)
            NexConfig.exerelinFactionConfigs.clear();
        NexConfig.exerelinFactionConfigs = new ArrayList<>();

        for(String factionId : builtInFactions) {
            NexFactionConfig conf = new NexFactionConfig(factionId);
            conf.isBuiltIn = true;
            NexConfig.exerelinFactionConfigs.add(conf);
            if (factionId.equals(Factions.NEUTRAL))
                defaultConfig = conf;
        }

        for(String factionId : supportedModFactions)
        {
            if (NexUtilsFaction.doesFactionExist(factionId))
                NexConfig.exerelinFactionConfigs.add(new NexFactionConfig(factionId));
        }
    }
	
	static {
		loadSettings();
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
            log.error("Failed to load mod faction file", ex);
        }
    }

    public static NexFactionConfig getFactionConfig(String factionId)
    {
        return getFactionConfig(factionId, true);
    }
	
	public static NexFactionConfig getFactionConfig(String factionId, boolean useDefault)
    {
        for(NexFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if (exerelinFactionConfig.factionId.equalsIgnoreCase(factionId))
                return exerelinFactionConfig;
        }
		if (useDefault)
		{
			//Global.getLogger(NexConfig.class).warn("Faction config " + factionId + " not found, using default");
			return defaultConfig;
		}
        else
		{
			//Global.getLogger(NexConfig.class).warn("Faction config " + factionId + " not found");
			return null;
		}
    }
	
	public static List<NexFactionConfig> getAllFactionConfigsCopy() {
		return new ArrayList<>(exerelinFactionConfigs);
	}

    @Deprecated
    public static List<String> getAllCustomFactionRebels()
    {
        List<String> customRebels = new ArrayList<>();

        for(NexFactionConfig exerelinFactionConfig : exerelinFactionConfigs)
        {
            if(!exerelinFactionConfig.customRebelFaction.isEmpty())
                customRebels.add(exerelinFactionConfig.customRebelFaction);
        }

        return customRebels;
    }
    
    public static List<String> getFactions(boolean onlyPlayable, boolean onlyStartable) {
        return getFactions(onlyPlayable, onlyStartable, false);
    }
    
    public static List<String> getFactions(boolean onlyPlayable, boolean onlyStartable, boolean excludeFreeStart)
    {
        List<String> factions = new ArrayList<>();

        for (NexFactionConfig config : exerelinFactionConfigs) {
            if (onlyPlayable && !config.playableFaction)
                continue;
            if (onlyStartable && !config.startingFaction)
                continue;
            if (excludeFreeStart && config.freeStart) {
                continue;
            }
            
            if (NexUtilsFaction.doesFactionExist(config.factionId))
            {
                factions.add(config.factionId);
            }
        }
        return factions;
    }
}
