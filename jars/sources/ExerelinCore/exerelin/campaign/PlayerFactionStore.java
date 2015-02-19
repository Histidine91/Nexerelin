package exerelin.campaign;

import com.fs.starfarer.api.Global;
import java.util.Map;
import org.apache.log4j.Logger;

public class PlayerFactionStore {
    private static final String PLAYER_FACTION_ID_KEY = "exerelin_playerFactionId";
    
    private static String factionId = "independent";
    
    public static Logger log = Global.getLogger(PlayerFactionStore.class);
    
    public static void setPlayerFactionId(String newFactionId)
    {
        factionId = newFactionId;
        Map<String, Object> data = Global.getSector().getPersistentData();
        data.put(PLAYER_FACTION_ID_KEY, factionId);
        log.info("Stored player faction ID as " + factionId);
        String storedId = (String)data.get(PLAYER_FACTION_ID_KEY);
        if (storedId != null) 
        {
            log.info("Found stored player faction ID " + storedId + " (just after save)");
        }
    }
   /**
    * only use for new game character creation
     * @param newFactionId
    */
    public static void setPlayerFactionIdNGC(String newFactionId)
    {
        factionId = newFactionId;
    }
    
    public static String getPlayerFactionId()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        String storedId = (String)data.get(PLAYER_FACTION_ID_KEY);
        if (storedId != null) 
        {
            log.info("Found stored player faction ID " + storedId);
            factionId = (String)storedId;
            return (String)storedId;
        }
        return factionId;
    }
}
