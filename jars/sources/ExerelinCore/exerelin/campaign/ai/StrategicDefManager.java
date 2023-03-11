package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
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
                List<String> tags = NexUtils.JSONArrayToArrayList(defJSON.getJSONArray("tags"));
                def.tags.addAll(tags);

                CONCERN_DEFS_BY_ID.put(id, def);
            }
        } catch (IOException | JSONException ex) {
            throw new RuntimeException("Failed to load strategic AI config, id " + id, ex);
        }
    }

    public static StrategicConcernDef getConcernDef(String id) {
        return CONCERN_DEFS_BY_ID.get(id);
    }

    public static StrategicConcern instantiateConcern(StrategicConcernDef def) {
        StrategicConcern concern = null;
        try {
            ClassLoader loader = Global.getSettings().getScriptClassLoader();
            Class<?> clazz = loader.loadClass(def.classPath);
            concern = (StrategicConcern)clazz.newInstance();
            concern.setId(def.id);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
            log.error("Failed to load concern " + def.id, ex);
        }
        return concern;
    }

    public static class StrategicConcernDef {
        public final String id;
        public String name;
        public String desc;
        public String icon;
        public String classPath;
        public ModuleType module;
        public Set<String> tags = new HashSet<>();

        public StrategicConcernDef(String id) {
            this.id = id;
        }

        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }
    }

    public enum ModuleType {
        ECONOMIC, DIPLOMATIC, MILITARY
    }
}
