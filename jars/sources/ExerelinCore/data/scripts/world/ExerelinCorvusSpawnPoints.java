package data.scripts.world;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

// should probably be a singleton instead of static but meh
public class ExerelinCorvusSpawnPoints {
    
    protected static final String CONFIG_FILE = "data/config/corvus_spawn_points.json";
    
    protected static final Map<String, SpawnPointEntry> SPAWN_POINTS = new HashMap<>();
    protected static final Map<String, String> SYSTEM_CAPITALS = new HashMap<>();
    
    static {
        try {
            JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
            Iterator<?> keys = config.keys();

            while( keys.hasNext() ) {
                String factionId = (String)keys.next();
                JSONObject spawnPointJson = config.getJSONObject(factionId);
                
                SpawnPointEntry spawnPoint = new SpawnPointEntry();
                String system = spawnPointJson.optString("system");
                String entity = spawnPointJson.optString("entity");
                boolean isCapital = spawnPointJson.optBoolean("isCapital", false);
                
                if (!system.isEmpty()) spawnPoint.systemName = system;
                if (!entity.isEmpty()) spawnPoint.entityName = entity;
                SPAWN_POINTS.put(factionId, spawnPoint);
                
                if (!system.isEmpty() && !system.equalsIgnoreCase("hyperspace") && !entity.isEmpty() && isCapital )
                {
                    SYSTEM_CAPITALS.put(system, entity);
                }
            }
        } catch (IOException | JSONException ex) {
            Global.getLogger(ExerelinCorvusSpawnPoints.class).error(ex);
        }
    }
    
    public static Map<String, String> getSystemCapitalsCopy()
    {
        return new HashMap<>(SYSTEM_CAPITALS);
    }
    
    public static SpawnPointEntry getFactionSpawnPoint(String factionId)
    {
        if (SPAWN_POINTS.containsKey(factionId))
            return SPAWN_POINTS.get(factionId);
        return null;
    }
    
    public static class SpawnPointEntry {
        public String systemName = "hyperspace";
        public String entityName;
    }
}
