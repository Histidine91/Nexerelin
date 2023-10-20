package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.utilities.NexUtils;
import lombok.extern.log4j.Log4j;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

@Log4j
public class StrategicDefManager {

    public static final String DEF_FILE_PATH = "data/config/exerelin/strategicAIConfig.json";

    public static final Map<String, StrategicConcernDef> CONCERN_DEFS_BY_ID = new HashMap<>();
    public static final Map<String, StrategicActionDef> ACTION_DEFS_BY_ID = new HashMap<>();

    static {
        loadConfig();
    }

    public static void loadConfig() {
        String id = "<unknown>";
        try {
            JSONObject config = Global.getSettings().getMergedJSONForMod(DEF_FILE_PATH, ExerelinConstants.MOD_ID);
            JSONObject concernsJSON = config.getJSONObject("concerns");
            Iterator<String> keys = concernsJSON.sortedKeys();
            while (keys.hasNext()) {
                id = keys.next();
                JSONObject defJSON = concernsJSON.getJSONObject(id);
                StrategicConcernDef def = new StrategicConcernDef(id);
                def.name = defJSON.getString("name");
                def.desc = defJSON.getString("desc");
                def.icon = defJSON.optString("icon", null);
                def.classPath = defJSON.getString("classPath");
                def.module = ModuleType.valueOf(defJSON.getString("module"));
                def.noAutoGenerate = defJSON.optBoolean("noAutoGenerate", false);
                def.enabled = defJSON.optBoolean("enabled", true);
                def.cooldownMult = (float)defJSON.optDouble("cooldownMult", 1);
                def.antiRepetitionMult = (float)defJSON.optDouble("antiRepetitionMult", 1);
                List<String> tags = NexUtils.JSONArrayToArrayList(defJSON.getJSONArray("tags"));
                def.tags.addAll(tags);

                CONCERN_DEFS_BY_ID.put(id, def);
            }

            JSONObject actionsJSON = config.getJSONObject("actions");
            keys = actionsJSON.sortedKeys();
            while (keys.hasNext()) {
                id = keys.next();
                JSONObject defJSON = actionsJSON.getJSONObject(id);
                StrategicActionDef def = new StrategicActionDef(id);
                def.name = defJSON.getString("name");
                def.classPath = defJSON.getString("classPath");
                def.chance = (float)defJSON.optDouble("chance", 1);
                def.cooldown = (float)defJSON.optDouble("cooldown", SAIConstants.DEFAULT_ACTION_COOLDOWN);
                def.antiRepetition = (float)defJSON.optDouble("antiRepetition", SAIConstants.DEFAULT_ANTI_REPETITION_VALUE);
                def.idForAntiRepetition = defJSON.optString("idForAntiRepetition", null);
                def.shim = defJSON.optBoolean("shim", false);
                def.enabled = defJSON.optBoolean("enabled", true);
                List<String> tags = NexUtils.JSONArrayToArrayList(defJSON.getJSONArray("tags"));
                def.tags.addAll(tags);

                ACTION_DEFS_BY_ID.put(id, def);
            }
        } catch (IOException | JSONException ex) {
            throw new RuntimeException("Failed to load strategic AI config, id " + id, ex);
        }
    }

    public static StrategicConcernDef getConcernDef(String id) {
        return CONCERN_DEFS_BY_ID.get(id);
    }
    public static StrategicActionDef getActionDef(String id) {
        return ACTION_DEFS_BY_ID.get(id);
    }

    /**
     * Creates an instance of the strategic concern with the specified def. Make sure to call its {@code setAI} method before using.
     * @param def
     * @return
     */
    public static StrategicConcern instantiateConcern(StrategicConcernDef def) {
        StrategicConcern concern = (StrategicConcern)NexUtils.instantiateClassByName(def.classPath);
        concern.setId(def.id);
        return concern;
    }

    /**
     * Creates an instance of the strategic action with the specified def. Make sure to call its {@code setAI} and {@code setConcern} methods before using.
     * @param def
     * @return
     */
    public static StrategicAction instantiateAction(StrategicActionDef def) {
        StrategicAction action = (StrategicAction)NexUtils.instantiateClassByName(def.classPath);
        action.setId(def.id);
        return action;
    }

    public static class StrategicConcernDef {
        public final String id;
        public String name;
        public String desc;
        public String icon;
        public String classPath;
        public ModuleType module;
        public boolean noAutoGenerate;
        public boolean enabled;
        public float cooldownMult;
        public float antiRepetitionMult;
        public Set<String> tags = new HashSet<>();

        public StrategicConcernDef(String id) {
            this.id = id;
        }

        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }
    }

    public static class StrategicActionDef {
        public final String id;
        public String name;
        public String classPath;
        public float chance;
        public float cooldown;
        public float antiRepetition;
        public String idForAntiRepetition;
        /**
         * Intended for actions that exist purely to replace themselves with another action.
         */
        public boolean shim;
        public boolean enabled;
        public Set<String> tags = new HashSet<>();

        public StrategicActionDef(String id) {
            this.id = id;
        }

        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }
    }

    public enum ModuleType {
        ECONOMIC, DIPLOMATIC, MILITARY, EXECUTIVE
    }
}
