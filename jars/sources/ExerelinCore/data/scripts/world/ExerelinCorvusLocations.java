package data.scripts.world;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

// should probably be a singleton instead of static but meh
public class ExerelinCorvusLocations {
    
    protected static final String CONFIG_FILE = "data/config/exerelin/corvus_location_config.json";
    
    protected static final Map<String, SpawnPointEntry> SPAWN_POINTS = new HashMap<>();
    protected static final Map<String, String> SYSTEM_CAPITALS = new HashMap<>();
    
    static {
        try {
            JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
            JSONObject spawnPoints = config.getJSONObject("spawnPoints");
            JSONObject systemCapitals = config.getJSONObject("systemCapitals");
            Iterator<?> keys = spawnPoints.keys();

            while( keys.hasNext() ) {
                String factionId = (String)keys.next();
                JSONObject spawnPointJson = spawnPoints.getJSONObject(factionId);
                
                SpawnPointEntry spawnPoint = new SpawnPointEntry();
                String system = spawnPointJson.optString("system");
                String entity = spawnPointJson.optString("entity");
                
                if (!system.isEmpty()) spawnPoint.systemName = system;
                if (!entity.isEmpty()) spawnPoint.entityName = entity;
                SPAWN_POINTS.put(factionId, spawnPoint);
                
                /*
                if (!system.isEmpty() && !system.equalsIgnoreCase("hyperspace") && !entity.isEmpty() && isCapital )
                {
                    SYSTEM_CAPITALS.put(system, entity);
                }
                */
            }
            
            keys = systemCapitals.keys();
            while (keys.hasNext())
            {
                String systemName = (String)keys.next();
                SYSTEM_CAPITALS.put(systemName, systemCapitals.getString(systemName));
            }
            
        } catch (IOException | JSONException ex) {
            Global.getLogger(ExerelinCorvusLocations.class).error(ex);
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
