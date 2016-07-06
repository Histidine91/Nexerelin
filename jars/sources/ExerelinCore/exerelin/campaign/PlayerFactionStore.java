package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinUtilsReputation;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

public class PlayerFactionStore {
    private static final String PLAYER_FACTION_ID_KEY = "exerelin_playerFactionId";
    private static final String PLAYER_RELATIONS_KEY = "exerelin_independentPlayerRelations";
    
    private static String factionId = ExerelinConstants.PLAYER_NPC_ID;
    
    //private static Map<String, Float> independentPlayerRelations = new HashSet<>();
    
    public static Logger log = Global.getLogger(PlayerFactionStore.class);
    
    public static void setPlayerFactionId(String newFactionId)
    {
        factionId = newFactionId;
        Map<String, Object> data = Global.getSector().getPersistentData();
        data.put(PLAYER_FACTION_ID_KEY, factionId);
        log.info("Stored player faction ID as " + factionId);
    }
   /**
    * only use for new game character creation
     * @param newFactionId
    */
    public static void setPlayerFactionIdNGC(String newFactionId)
    {
        factionId = newFactionId;
    }
    
    public static String getPlayerFactionIdNGC()
    {
        return factionId;
    }
    
    public static String getPlayerFactionId()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        String storedId = (String)data.get(PLAYER_FACTION_ID_KEY);
        if (storedId != null) 
        {
            factionId = (String)storedId;
            return (String)storedId;
        }
        return factionId;
    }
    
    public static void saveIndependentPlayerRelation(String factionId)
    {
        SectorAPI sector = Global.getSector();
        Map<String, Object> data = sector.getPersistentData();
        Map<String, Float> storedRelations = (HashMap<String, Float>)data.get(PLAYER_RELATIONS_KEY);
        if (storedRelations == null) storedRelations = new HashMap<>();
        
        FactionAPI playerFaction = sector.getFaction("player");
        float relation = playerFaction.getRelationship(factionId);
        storedRelations.put(factionId, relation);
        data.put(PLAYER_RELATIONS_KEY, storedRelations);
    }
    
    public static void saveIndependentPlayerRelations()
    {
        SectorAPI sector = Global.getSector();
        Map<String, Object> data = sector.getPersistentData();
        Map<String, Float> storedRelations = new HashMap<>();
        FactionAPI playerFaction = sector.getFaction("player");
        for (FactionAPI faction : sector.getAllFactions())
        {
            float relation = playerFaction.getRelationship(faction.getId());
            storedRelations.put(faction.getId(), relation);
            log.info("Saving independent player relations with " + faction.getDisplayName() + " as " + relation);
        }
        data.put(PLAYER_RELATIONS_KEY, storedRelations);
    }
    
    public static void loadIndependentPlayerRelations(boolean retainWithCurrentFaction)
    {
        SectorAPI sector = Global.getSector();
        Map<String, Object> data = sector.getPersistentData();
        Map<String, Float> storedRelations = (HashMap<String, Float>)data.get(PLAYER_RELATIONS_KEY);
        if (storedRelations == null) return;
        
        FactionAPI playerFaction = sector.getFaction("player");
        for (FactionAPI faction : sector.getAllFactions())
        {
            Float relation = storedRelations.get(faction.getId());
            if (relation != null && (!retainWithCurrentFaction || !faction.getId().equals(factionId)))
            {
                faction.setRelationship("player", relation);
                log.info("Loading independent player relations with " + faction.getDisplayName() + " as " + relation);
            }
        }
        //ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
    }
}
