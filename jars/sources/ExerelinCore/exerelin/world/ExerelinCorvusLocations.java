package exerelin.world;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// should probably be a singleton instead of static but meh
public class ExerelinCorvusLocations {
    
    protected static final String SYSTEM_CAPITAL_CONFIG = "data/config/exerelin/corvus_capitals.csv";
    protected static final String SPAWN_POINT_CONFIG = "data/config/exerelin/corvus_spawnpoints.csv";
    
    protected static final Map<String, SpawnPointEntry> SPAWN_POINTS = new HashMap<>();
    protected static final Map<String, String> SYSTEM_CAPITALS = new HashMap<>();
    
    static {
        try {
            JSONArray systemCapitalsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("system", SYSTEM_CAPITAL_CONFIG, "nexerelin");
            for(int x = 0; x < systemCapitalsCsv.length(); x++)
            {
                JSONObject row = systemCapitalsCsv.getJSONObject(x);
                String systemName = row.getString("system");
                String entityId = row.getString("entityID");
                SYSTEM_CAPITALS.put(systemName, entityId);
            }
            
            JSONArray spawnPointsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("faction", SPAWN_POINT_CONFIG, "nexerelin");
            for(int x = 0; x < spawnPointsCsv.length(); x++)
            {
                JSONObject row = spawnPointsCsv.getJSONObject(x);
                SpawnPointEntry spawnPoint = new SpawnPointEntry();
                String systemName = row.getString("system");
                String entityId = row.getString("entityID");
                if (!systemName.isEmpty()) spawnPoint.systemName = systemName;
                if (!entityId.isEmpty()) spawnPoint.entityId = entityId;
                SPAWN_POINTS.put(row.getString("faction"), spawnPoint);
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
        public String entityId;
    }
}
