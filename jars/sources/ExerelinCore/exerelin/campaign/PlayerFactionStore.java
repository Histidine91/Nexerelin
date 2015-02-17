package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import java.util.Map;
import org.apache.log4j.Logger;

public class PlayerFactionStore {
    private static final String PLAYER_FACTION_ID_KEY = "$exerelin_playerFactionId";
    
    private static String factionId = "independent";
    
    public static Logger log = Global.getLogger(PlayerFactionStore.class);
    
    public static void setPlayerFactionId(String newFactionId)
    {
        factionId = newFactionId;
        MemoryAPI memory = Global.getSector().getMemory();
        memory.set(PLAYER_FACTION_ID_KEY, factionId);
        log.info("Stored player faction ID as " + factionId);
        String storedId = memory.getString(PLAYER_FACTION_ID_KEY);
        if (storedId != null) 
        {
            log.info("Found stored player faction ID " + storedId + " (just after save)");
        }
    }
    
    public static String getPlayerFactionId()
    {
        MemoryAPI memory = Global.getSector().getMemory();
        String storedId = memory.getString(PLAYER_FACTION_ID_KEY);
        if (storedId != null) 
        {
            log.info("Found stored player faction ID " + storedId);
            factionId = (String)storedId;
            return (String)storedId;
        }
        return factionId;
    }
}
