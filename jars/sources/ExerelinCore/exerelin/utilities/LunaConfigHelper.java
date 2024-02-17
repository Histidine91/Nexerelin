package exerelin.utilities;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.plugins.ExerelinModPlugin;
import lombok.extern.log4j.Log4j;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Log4j
public class LunaConfigHelper implements LunaSettingsListener {

    public static final String PREFIX = "nex_";

    @Deprecated public static final List<String> DEFAULT_TAGS = new ArrayList<>();  // we don't use tags no more
    static {
        DEFAULT_TAGS.add("spacing:0.5");
    }

    // runcode exerelin.utilities.LunaConfigHelper.initLunaConfig()
    public static void initLunaConfig() {
        String mid = ExerelinConstants.MOD_ID;
        //List<String> tags = DEFAULT_TAGS;

        String tabFleets = getString("tabFleets");
        String tabDiplomacy = getString("tabDiplomacy");

        addHeader("ui", null);
        addSetting("directoryDialogKey", "key", NexConfig.directoryDialogKey);
        addSetting("ceasefireNotificationPopup", "boolean", NexConfig.ceasefireNotificationPopup);
        addSetting("diplomacyEventFilterLevel", "int", NexConfig.diplomacyEventFilterLevel, 0, 2);
        addSetting("agentEventFilterLevel", "int", NexConfig.agentEventFilterLevel, 0, 2);

        addHeader("invasions", tabFleets);
        addSetting("enableHostileFleetEvents", "boolean", tabFleets, NexConfig.enableHostileFleetEvents);
        addSetting("enableInvasions", "boolean", tabFleets, NexConfig.enableInvasions);
        addSetting("legacyInvasions", "boolean", tabFleets, NexConfig.legacyInvasions);
        addSetting("invasionsOnlyAfterPlayerColony", "boolean", tabFleets, NexConfig.invasionsOnlyAfterPlayerColony);
        addSetting("allowInvadeStoryCritical", "boolean", tabFleets, NexConfig.allowInvadeStoryCritical);
        addSetting("allowInvadeStartingMarkets", "boolean", tabFleets, NexConfig.allowInvadeStartingMarkets);
        addSetting("followersInvasions", "boolean", tabFleets, NexConfig.followersInvasions);
        addSetting("allowPirateInvasions", "boolean", tabFleets, NexConfig.allowPirateInvasions);
        addSetting("retakePirateMarkets", "boolean", tabFleets, NexConfig.retakePirateMarkets);

        addSetting("invasionGracePeriod", "int", tabFleets, Math.round(NexConfig.invasionGracePeriod), 0, 365*5);
        addSetting("pointsRequiredForInvasionFleet", "int", tabFleets, Math.round(NexConfig.pointsRequiredForInvasionFleet), 2000, 100000);
        addSetting("baseInvasionPointsPerFaction", "int", tabFleets, Math.round(NexConfig.baseInvasionPointsPerFaction), 0, 1000);
        addSetting("invasionPointsPerPlayerLevel", "int", tabFleets, Math.round(NexConfig.invasionPointsPerPlayerLevel), 0, 100);
        addSetting("invasionPointEconomyMult", "float", tabFleets, NexConfig.invasionPointEconomyMult, 0, 10);
        addSetting("invasionFleetSizeMult", "float", tabFleets, NexConfig.invasionFleetSizeMult, 0.1, 10);
        addSetting("fleetRequestCostPerFP", "int", tabFleets, Math.round(NexConfig.fleetRequestCostPerFP), 1, 10000);
        addSetting("creditLossOnColonyLossMult", "float", tabFleets, NexConfig.creditLossOnColonyLossMult, 0, 1);
        addSetting("groundBattleDamageMult", "float", tabFleets, NexConfig.groundBattleDamageMult, 0, 5);

        addHeader("diplomacy", tabDiplomacy);
        addSetting("enableDiplomacy", "boolean", tabDiplomacy, NexConfig.enableDiplomacy);
        addSetting("followersDiplomacy", "boolean", tabDiplomacy, NexConfig.followersDiplomacy);
        addSetting("diplomacyInterval", "float", tabDiplomacy, DiplomacyManager.getBaseInterval(), 5, 60);
        addSetting("allowRandomDiplomacyTraits", "boolean", tabDiplomacy, NexConfig.allowRandomDiplomacyTraits);
        addSetting("useRelationshipBounds", "boolean", tabDiplomacy, NexConfig.useRelationshipBounds);

        addHeader("alliances", tabDiplomacy);
        addSetting("enableAlliances", "boolean", tabDiplomacy, NexConfig.enableAlliances);
        addSetting("allianceGracePeriod", "int", tabDiplomacy, NexConfig.allianceGracePeriod, 0, 1825);
        //addSetting("allianceFormationInterval", "int", tabDiplomacy, NexConfig.allianceFormationInterval);
        addSetting("ignoreAlignmentForAlliances", "boolean", tabDiplomacy, NexConfig.ignoreAlignmentForAlliances);

        addHeader("insurance", null);
        addSetting("legacyInsurance", "boolean", NexConfig.legacyInsurance);
        addSetting("playerInsuranceMult", "float", NexConfig.playerInsuranceMult, 0, 10);

        addHeader("agents", null);
        addSetting("agentBaseSalary", "int", NexConfig.agentBaseSalary, 0, 100000);
        addSetting("agentSalaryPerLevel", "int", NexConfig.agentSalaryPerLevel, 0, 100000);
        addSetting("maxAgents", "int", NexConfig.maxAgents, 0, 100);
        addSetting("agentStealMarketShipsOnly", "boolean", !NexConfig.agentStealAllShips);
        addSetting("useAgentSpecializations", "boolean", NexConfig.useAgentSpecializations);
        addSetting("followersAgents", "boolean", NexConfig.followersAgents);

        addHeader("prisoners", null);
        addSetting("prisonerRepatriateRepValue", "float", NexConfig.prisonerRepatriateRepValue, 0, 1);
        addSetting("prisonerBaseRansomValue", "int", (int)NexConfig.prisonerBaseRansomValue, 0, 200000);
        addSetting("prisonerRansomValueIncrementPerLevel", "int", (int)NexConfig.prisonerRansomValueIncrementPerLevel, 0, 100000);
        addSetting("crewLootMult", "float", NexConfig.crewLootMult, 0, 10);

        addHeader("satbomb", tabFleets);
        addSetting("allowNPCSatBomb", "boolean", tabFleets, NexConfig.allowNPCSatBomb);
        addSetting("permaHateFromPlayerSatBomb", "float", tabFleets, NexConfig.permaHateFromPlayerSatBomb, 0, 1);

        addHeader("vengeance", tabFleets);
        addSetting("enableRevengeFleets", "int", tabFleets, NexConfig.enableRevengeFleets, 0, 2);
        addSetting("useNewVengeanceEncounters", "boolean", tabFleets, NexConfig.useNewVengeanceEncounters);
        addSetting("vengeanceFleetSizeMult", "float", tabFleets, NexConfig.vengeanceFleetSizeMult, 0.2, 5);

        addHeader("otherFleets", tabFleets);
        addSetting("enableColonyExpeditions", "boolean", tabFleets, NexConfig.enableColonyExpeditions);
        addSetting("colonyExpeditionInterval", "int", tabFleets, NexConfig.colonyExpeditionInterval, 15, 10000);
        addSetting("colonyExpeditionsOnlyAfterPlayerColony", "boolean", tabFleets, NexConfig.colonyExpeditionsOnlyAfterPlayerColony);
        addSetting("specialForcesPointMult", "float", tabFleets, NexConfig.specialForcesPointMult, 0, 10);
        addSetting("specialForcesSizeMult", "float", tabFleets, NexConfig.specialForcesSizeMult, 0.2, 5);

        addHeader("misc", null);
        addSetting("enableStrategicAI", "boolean", NexConfig.enableStrategicAI);
        addSetting("showStrategicAI", "boolean", NexConfig.showStrategicAI);
        addSetting("enableVictory", "boolean", NexConfig.enableVictory);
        addSetting("hardModeColonyGrowthMult", "float", NexConfig.hardModeColonyGrowthMult, 0.5f, 1f);
        addSetting("hardModeColonyIncomeMult", "float", NexConfig.hardModeColonyIncomeMult, 0.5f, 1f);
        addSetting("enablePunitiveExpeditions", "boolean", NexConfig.enablePunitiveExpeditions);
        //addSetting("prismNumBossShips", "int", NexConfig.prismNumBossShips, 0, 10);
        addSetting("officerDeaths", "boolean", NexConfig.officerDeaths);
        addSetting("rebellionMult", "float", NexConfig.rebellionMult, 0f, 10f);
        addSetting("skipStoryDefault", "boolean", Global.getSettings().getBoolean("nex_skipStoryDefault"));

        addHeader("debug", null);
        addSetting("nexDevMode", "boolean", ExerelinModPlugin.isNexDev);

        LunaSettings.SettingsCreator.refresh(mid);

        tryLoadLunaConfig();

        createListener();
    }

    public static void tryLoadLunaConfig() {
        try {
            loadConfigFromLuna();
        } catch (NullPointerException npe) {
            // config not created yet I guess, do nothing
        }
    }

    /**
     * Loads only the settings that are save-specific
     */
    public static void tryLoadLunaConfigOnGameLoad() {
        try {
            loadConfigFromLunaOnGameLoad();
        } catch (NullPointerException npe) {
            // config not created yet I guess, do nothing
        }
    }

    protected static void loadConfigFromLuna() {
        NexConfig.ceasefireNotificationPopup = (boolean)loadSetting("ceasefireNotificationPopup", "boolean");
        NexConfig.directoryDialogKey = (int)loadSetting("directoryDialogKey", "key");
        NexConfig.diplomacyEventFilterLevel = (int)loadSetting("diplomacyEventFilterLevel", "int");
        NexConfig.agentEventFilterLevel = (int)loadSetting("agentEventFilterLevel", "int");

        NexConfig.crewLootMult = (float)loadSetting("crewLootMult", "float");

        NexConfig.enableHostileFleetEvents = (boolean)loadSetting("enableHostileFleetEvents", "boolean");
        NexConfig.enableInvasions = (boolean)loadSetting("enableInvasions", "boolean");
        NexConfig.legacyInvasions = (boolean)loadSetting("legacyInvasions", "boolean");
        NexConfig.invasionsOnlyAfterPlayerColony = (boolean)loadSetting("invasionsOnlyAfterPlayerColony", "boolean");
        NexConfig.allowInvadeStoryCritical = (boolean)loadSetting("allowInvadeStoryCritical", "boolean");
        NexConfig.allowInvadeStartingMarkets = (boolean)loadSetting("allowInvadeStartingMarkets", "boolean");
        NexConfig.followersInvasions = (boolean)loadSetting("followersInvasions", "boolean");
        NexConfig.allowPirateInvasions = (boolean)loadSetting("allowPirateInvasions", "boolean");
        NexConfig.retakePirateMarkets = (boolean)loadSetting("retakePirateMarkets", "boolean");
        NexConfig.invasionGracePeriod = (int)loadSetting("invasionGracePeriod", "int");
        NexConfig.pointsRequiredForInvasionFleet = (int)loadSetting("pointsRequiredForInvasionFleet", "int");
        NexConfig.baseInvasionPointsPerFaction = (int)loadSetting("baseInvasionPointsPerFaction", "int");
        NexConfig.invasionPointsPerPlayerLevel = (int)loadSetting("invasionPointsPerPlayerLevel", "int");
        NexConfig.invasionPointEconomyMult = (float)loadSetting("invasionPointEconomyMult", "float");
        NexConfig.invasionFleetSizeMult = (float)loadSetting("invasionFleetSizeMult", "float");
        NexConfig.fleetRequestCostPerFP = (int)loadSetting("fleetRequestCostPerFP", "int");
        NexConfig.creditLossOnColonyLossMult = (float)loadSetting("creditLossOnColonyLossMult", "float");
        NexConfig.groundBattleDamageMult = (float)loadSetting("groundBattleDamageMult", "float");

        NexConfig.enableDiplomacy = (boolean)loadSetting("enableDiplomacy", "boolean");
        NexConfig.followersDiplomacy = (boolean)loadSetting("followersDiplomacy", "boolean");
        DiplomacyManager.setBaseInterval((float)loadSetting("diplomacyInterval", "float"));
        NexConfig.allowRandomDiplomacyTraits = (boolean)loadSetting("allowRandomDiplomacyTraits", "boolean");
        NexConfig.useRelationshipBounds = (boolean)loadSetting("useRelationshipBounds", "boolean");

        NexConfig.enableAlliances = (boolean)loadSetting("enableAlliances", "boolean");
        NexConfig.allianceGracePeriod = (int)loadSetting("allianceGracePeriod", "int");
        //NexConfig.allianceFormationInterval = (float)loadSetting("allianceFormationInterval", "int");
        NexConfig.ignoreAlignmentForAlliances = (boolean)loadSetting("ignoreAlignmentForAlliances", "boolean");

        NexConfig.legacyInsurance = (boolean)loadSetting("legacyInsurance", "boolean");
        NexConfig.playerInsuranceMult = (float)loadSetting("playerInsuranceMult", "float");

        NexConfig.agentBaseSalary = (int)loadSetting("agentBaseSalary", "int");
        NexConfig.agentSalaryPerLevel = (int)loadSetting("agentSalaryPerLevel", "int");
        NexConfig.maxAgents = (int)loadSetting("maxAgents", "int");
        NexConfig.agentStealAllShips = !(boolean)loadSetting("agentStealMarketShipsOnly", "boolean");
        NexConfig.useAgentSpecializations = (boolean)loadSetting("useAgentSpecializations", "boolean");
        NexConfig.followersAgents = (boolean)loadSetting("followersAgents", "boolean");

        NexConfig.prisonerRepatriateRepValue = (float)loadSetting("prisonerRepatriateRepValue", "float");
        NexConfig.prisonerBaseRansomValue = (float)loadSetting("prisonerBaseRansomValue", "float");
        NexConfig.prisonerRansomValueIncrementPerLevel = (float)loadSetting("prisonerRansomValueIncrementPerLevel", "float");

        NexConfig.allowNPCSatBomb = (boolean)loadSetting("allowNPCSatBomb", "boolean");
        NexConfig.permaHateFromPlayerSatBomb = (float)loadSetting("permaHateFromPlayerSatBomb", "float");

        NexConfig.enableRevengeFleets = (int)loadSetting("enableRevengeFleets", "int");
        NexConfig.useNewVengeanceEncounters = (boolean)loadSetting("useNewVengeanceEncounters", "boolean");
        NexConfig.vengeanceFleetSizeMult = (float)loadSetting("vengeanceFleetSizeMult", "float");

        NexConfig.enableColonyExpeditions = (boolean)loadSetting("enableColonyExpeditions", "boolean");
        NexConfig.colonyExpeditionInterval = (int)loadSetting("colonyExpeditionInterval", "int");
        NexConfig.colonyExpeditionsOnlyAfterPlayerColony = (boolean)loadSetting("colonyExpeditionsOnlyAfterPlayerColony", "boolean");
        NexConfig.specialForcesPointMult = (float)loadSetting("specialForcesPointMult", "float");
        NexConfig.specialForcesSizeMult = (float)loadSetting("specialForcesSizeMult", "float");

        NexConfig.enableStrategicAI = (boolean)loadSetting("enableStrategicAI", "boolean");
        NexConfig.showStrategicAI = (boolean)loadSetting("showStrategicAI", "boolean");
        NexConfig.enableVictory = (boolean)loadSetting("enableVictory", "boolean");
        NexConfig.hardModeColonyGrowthMult = (float)loadSetting("hardModeColonyGrowthMult", "float");
        NexConfig.hardModeColonyIncomeMult = (float)loadSetting("hardModeColonyIncomeMult", "float");
        NexConfig.enablePunitiveExpeditions = (boolean)loadSetting("enablePunitiveExpeditions", "boolean");
        NexConfig.officerDeaths = (boolean)loadSetting("officerDeaths", "boolean");
        NexConfig.rebellionMult = (float)loadSetting("rebellionMult", "float");
        Global.getSettings().setBoolean("nex_skipStoryDefault", (boolean)loadSetting("skipStoryDefault", "boolean"));
        //NexConfig.prismNumBossShips = (int)loadSetting("prismNumBossShips", "int");

        ExerelinModPlugin.isNexDev = (boolean)loadSetting("nexDevMode", "boolean");
    }

    protected static void loadConfigFromLunaOnGameLoad() {
        //DiplomacyManager.setBaseInterval((float)loadSetting("diplomacyInterval", "float"));
    }

    public static Object loadSetting(String var, String type) {
        String mid = ExerelinConstants.MOD_ID;
        var = PREFIX + var;
        switch (type) {
            case "bool":
            case "boolean":
                return LunaSettings.getBoolean(mid, var);
            case "int":
            case "integer":
            case "key":
                return LunaSettings.getInt(mid, var);
            case "float":
                return (float)(double)LunaSettings.getDouble(mid, var);
            case "double":
                return LunaSettings.getDouble(mid, var);
            default:
                log.error(String.format("Setting %s has invalid type %s", var, type));
        }
        return null;
    }

    public static void addSetting(String var, String type, Object defaultVal) {
        addSetting(var, type, null, defaultVal, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static void addSetting(String var, String type, @Nullable String tab, Object defaultVal) {
        addSetting(var, type, tab, defaultVal, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static void addSetting(String var, String type, Object defaultVal, double min, double max) {
        addSetting(var, type, null, defaultVal, min, max);
    }

    public static void addSetting(String var, String type, @Nullable String tab, Object defaultVal, double min, double max) {
        String tooltip = getString("tooltip_" + var);
        if (tooltip.startsWith("Missing string:")) {
            tooltip = "";
        }
        String mid = ExerelinConstants.MOD_ID;
        String name = getString("name_" + var);

        if (tab == null) tab = "";

        Double defaultAsDouble = objectToDouble(defaultVal);
        if (defaultAsDouble != null) {
            log.info("Trying default " + defaultAsDouble + " for setting " + var);
            if (defaultAsDouble < min) min = defaultAsDouble;
            else if (defaultAsDouble > max) max = defaultAsDouble;
        }

        var = PREFIX + var;

        switch (type) {
            case "boolean":
                LunaSettings.SettingsCreator.addBoolean(mid, var, name, tooltip, (boolean)defaultVal, tab);
                break;
            case "int":
            case "integer":
                if (defaultVal instanceof Float) {
                    defaultVal = Math.round((float)defaultVal);
                }
                LunaSettings.SettingsCreator.addInt(mid, var, name, tooltip,
                        (int)defaultVal, (int)Math.round(min), (int)Math.round(max), tab);
                break;
            case "float":
                // fix float -> double conversion causing an unround number
                String floatStr = ((Float)defaultVal).toString();
                LunaSettings.SettingsCreator.addDouble(mid, var, name, tooltip,
                        Double.parseDouble(floatStr), min, max, tab);
                break;
            case "double":
                LunaSettings.SettingsCreator.addDouble(mid, var, name, tooltip,
                        (double)defaultVal, min, max, tab);
                break;
            case "key":
                LunaSettings.SettingsCreator.addKeybind(mid, var, name, tooltip, (int)defaultVal, tab);
            default:
                log.error(String.format("Setting %s has invalid type %s", var, type));
        }
    }

    public static void addHeader(String id, String tab) {
        addHeader(id, getString("header_" + id), tab);
    }

    public static void addHeader(String id, String title, String tab) {
        if (tab == null) tab = "";
        LunaSettings.SettingsCreator.addHeader(ExerelinConstants.MOD_ID, id, title, tab);
    }

    public static Double objectToDouble(Object obj) {
        if (obj instanceof Double) return (double)obj;
        if (obj instanceof Float) return (double)(float)obj;
        if (obj instanceof Integer) return (double)(int)obj;
        if (obj instanceof Long) return (double)(long)obj;
        return null;
    }

    public static LunaConfigHelper createListener() {
        LunaConfigHelper helper = new LunaConfigHelper();
        LunaSettings.INSTANCE.addSettingsListener(helper);
        return helper;
    }

    @Override
    public void settingsChanged(String modId) {
        if (ExerelinConstants.MOD_ID.equals(modId)) {
            loadConfigFromLuna();
        }
    }

    public static String getString(String id) {
        return StringHelper.getString("nex_lunaSettings", id);
    }
}
