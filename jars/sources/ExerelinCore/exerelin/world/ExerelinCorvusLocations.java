package exerelin.world;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// should probably be a singleton instead of static but meh
public class ExerelinCorvusLocations {
    
    protected static final String SPAWN_POINT_CONFIG = "data/config/exerelin/corvus_spawnpoints.csv";
    
    protected static final Map<String, SpawnPointEntry> SPAWN_POINTS = new HashMap<>();
    
    static {
        try {            
            JSONArray spawnPointsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("faction", SPAWN_POINT_CONFIG, ExerelinConstants.MOD_ID);
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
            //MagicSettings.getStringMap("nexerelin", "faction_spawn_points");
        } catch (IOException | JSONException ex) {
            Global.getLogger(ExerelinCorvusLocations.class).error(ex);
        }
    }
    
    public static Map<String, SpawnPointEntry> getFactionSpawnPointsCopy() {
        return new HashMap<>(SPAWN_POINTS);
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
