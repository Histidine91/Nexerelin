package exerelin.utilities;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import lombok.extern.log4j.Log4j;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

import java.util.ArrayList;
import java.util.List;

@Log4j
public class LunaConfigHelper implements LunaSettingsListener {

    public static final List<String> DEFAULT_TAGS = new ArrayList<>();
    static {
        DEFAULT_TAGS.add("spacing:0.5");
    }

    public static void initLunaConfig() {
        String mid = ExerelinConstants.MOD_ID;
        List<String> tags = DEFAULT_TAGS;

        addSetting("ceasefireNotificationPopup", "boolean", NexConfig.ceasefireNotificationPopup, tags);

        addHeader("invasions");
        addSetting("enableInvasions", "boolean", NexConfig.enableInvasions, tags);
        addSetting("legacyInvasions", "boolean", NexConfig.legacyInvasions, tags);
        addSetting("invasionsOnlyAfterPlayerColony", "boolean", NexConfig.invasionsOnlyAfterPlayerColony, tags);
        addSetting("allowInvadeStoryCritical", "boolean", NexConfig.allowInvadeStoryCritical, tags);
        addSetting("followersInvasions", "boolean", NexConfig.followersInvasions, tags);
        addSetting("allowPirateInvasions", "boolean", NexConfig.allowPirateInvasions, tags);
        addSetting("retakePirateMarkets", "boolean", NexConfig.retakePirateMarkets, tags);
        addSetting("invasionGracePeriod", "int", Math.round(NexConfig.invasionGracePeriod), 0, 365*5, tags);
        addSetting("pointsRequiredForInvasionFleet", "float", NexConfig.pointsRequiredForInvasionFleet, 2000, 100000, tags);
        addSetting("invasionFleetSizeMult", "float", NexConfig.invasionFleetSizeMult, 0.1, 10, tags);
        addSetting("fleetRequestCostPerFP", "int", Math.round(NexConfig.fleetRequestCostPerFP), 1, 10000, tags);
        addSetting("creditLossOnColonyLossMult", "float", NexConfig.creditLossOnColonyLossMult, 0, 1, tags);

        addHeader("insurance");
        addSetting("legacyInsurance", "boolean", NexConfig.legacyInsurance, tags);
        addSetting("playerInsuranceMult", "float", NexConfig.playerInsuranceMult, 0, 10, tags);

        addHeader("agents");
        addSetting("agentBaseSalary", "int", NexConfig.agentBaseSalary, 0, 100000, tags);
        addSetting("agentSalaryPerLevel", "int", NexConfig.agentSalaryPerLevel, 0, 100000, tags);
        addSetting("maxAgents", "int", NexConfig.maxAgents, 0, 100, tags);
        addSetting("agentStealMarketShipsOnly", "boolean", !NexConfig.agentStealAllShips, tags);
        addSetting("useAgentSpecializations", "boolean", NexConfig.useAgentSpecializations, tags);
        addSetting("followersAgents", "boolean", NexConfig.followersAgents, tags);

        addHeader("prisoners");
        addSetting("prisonerRepatriateRepValue", "float", NexConfig.prisonerRepatriateRepValue, 0, 1, tags);
        addSetting("prisonerBaseRansomValue", "int", (int)NexConfig.prisonerBaseRansomValue, 0, 1000000, tags);
        addSetting("prisonerRansomValueIncrementPerLevel", "int", (int)NexConfig.prisonerRansomValueIncrementPerLevel, 0, 1000000, tags);
        addSetting("crewLootMult", "float", NexConfig.crewLootMult, 0, 100, tags);

        addHeader("satbomb");
        addSetting("allowNPCSatBomb", "boolean", NexConfig.allowNPCSatBomb, tags);
        addSetting("permaHateFromPlayerSatBomb", "float", NexConfig.permaHateFromPlayerSatBomb, 0, 1, tags);

        try {
            loadConfigFromLuna();
        } catch (NullPointerException npe) {
            // config not created yet I guess, do nothing
        }
    }

    public static void loadConfigFromLuna() {
        NexConfig.ceasefireNotificationPopup = (boolean)loadSetting("ceasefireNotificationPopup", "boolean");
        NexConfig.crewLootMult = (float)loadSetting("crewLootMult", "float");

        NexConfig.enableInvasions = (boolean)loadSetting("enableInvasions", "boolean");
        NexConfig.legacyInvasions = (boolean)loadSetting("legacyInvasions", "boolean");
        NexConfig.invasionsOnlyAfterPlayerColony = (boolean)loadSetting("invasionsOnlyAfterPlayerColony", "boolean");
        NexConfig.allowInvadeStoryCritical = (boolean)loadSetting("allowInvadeStoryCritical", "boolean");
        NexConfig.followersInvasions = (boolean)loadSetting("followersInvasions", "boolean");
        NexConfig.allowPirateInvasions = (boolean)loadSetting("allowPirateInvasions", "boolean");
        NexConfig.retakePirateMarkets = (boolean)loadSetting("retakePirateMarkets", "boolean");
        NexConfig.invasionGracePeriod = (int)loadSetting("invasionGracePeriod", "int");
        NexConfig.pointsRequiredForInvasionFleet = (int)loadSetting("pointsRequiredForInvasionFleet", "int");
        NexConfig.invasionFleetSizeMult = (float)loadSetting("invasionFleetSizeMult", "float");
        NexConfig.fleetRequestCostPerFP = (int)loadSetting("invasionFleetSizeMult", "int");
        NexConfig.creditLossOnColonyLossMult = (float)loadSetting("creditLossOnColonyLossMult", "float");

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
    }

    public static Object loadSetting(String var, String type) {
        String mid = ExerelinConstants.MOD_ID;
        switch (type) {
            case "bool":
            case "boolean":
                return LunaSettings.getBoolean(mid, var);
            case "int":
            case "integer":
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

    public static void addSetting(String var, String type, Object defaultVal, List<String> tags) {
        addSetting(var, type, defaultVal, Integer.MIN_VALUE, Integer.MAX_VALUE, tags);
    }

    public static void addSetting(String var, String type, Object defaultVal, double min, double max, List<String> tags) {
        String tooltip = Global.getSettings().getString("nex_lunaSettings", "tooltip_" + var);
        if (tooltip.startsWith("Missing string:")) {
            tooltip = "";
        }
        String mid = ExerelinConstants.MOD_ID;

        switch (type) {
            case "boolean":
                LunaSettings.SettingsCreator.addBoolean(mid, var, getString("name_" + var), tooltip, (boolean)defaultVal, tags);
                break;
            case "int":
            case "integer":
                LunaSettings.SettingsCreator.addInt(mid, var, getString("name_" + var), tooltip,
                        (int)defaultVal, (int)Math.round(min), (int)Math.round(max), tags);
                break;
            case "float":
                // fix float -> double conversion causing an unround number
                String floatStr = ((Float)defaultVal).toString();
                LunaSettings.SettingsCreator.addDouble(mid, var, getString("name_" + var), tooltip,
                        Double.parseDouble(floatStr), min, max, tags);
                break;
            case "double":
                LunaSettings.SettingsCreator.addDouble(mid, var, getString("name_" + var), tooltip,
                        (double)defaultVal, min, max, tags);
                break;
            default:
                log.error(String.format("Setting %s has invalid type %s", var, type));
        }
    }

    public static void addHeader(String id) {
        LunaSettings.SettingsCreator.addHeader(ExerelinConstants.MOD_ID, id, getString("header_" + id));
    }

    public static void addHeader(String id, String title) {
        LunaSettings.SettingsCreator.addHeader(ExerelinConstants.MOD_ID, id, title);
    }

    public static LunaConfigHelper createListener() {
        LunaConfigHelper helper = new LunaConfigHelper();
        Global.getSector().getListenerManager().addListener(helper, true);
        return helper;
    }

    @Override
    public void settingsChanged() {
        loadConfigFromLuna();
    }

    public static String getString(String id) {
        return StringHelper.getString("nex_lunaSettings", id);
    }
}
