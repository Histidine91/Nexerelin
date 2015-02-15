package exerelin;

import com.fs.starfarer.api.Global;
import java.util.Map;

public class PlayerFactionStore {
    private static final String PLAYER_FACTION_ID_KEY = "exerelin_playerFactionId";
    
    private static String factionId = "independent";
    
    public static void setPlayerFactionId(String newFactionId)
    {
        factionId = newFactionId;
        Map<String, Object> data = Global.getSector().getPersistentData();
        data.put(PLAYER_FACTION_ID_KEY, factionId);
    }
    
    public static String getPlayerFactionId()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object storedId = data.get(PLAYER_FACTION_ID_KEY);
        if (storedId != null) 
        {
            factionId = (String)storedId;
            return (String)storedId;
        }
        return factionId;
    }
}
