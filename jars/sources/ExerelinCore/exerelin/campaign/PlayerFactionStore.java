package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Stores the selected player faction in new game dialog, and also used mid-campaign
 * to determine which faction the player belongs to.
 * The latter's methods are now just a wrapper for Misc.getCommissionFactionId().
 * 
 * This class also used to handle restoring player relationships on leaving their faction,
 * but that behavior is no longer applied.
 */
public class PlayerFactionStore {
    public static final String PLAYER_FACTION_ID_KEY = "exerelin_playerFactionId";
    public static final String PLAYER_RELATIONS_KEY = "exerelin_independentPlayerRelations";
    public static final String STARTING_FACTION_ID_MEMKEY = "$nex_startingFactionId";
    
    private static String factionId = Factions.PLAYER;
    
    //private static Map<String, Float> independentPlayerRelations = new HashSet<>();
    
    public static Logger log = Global.getLogger(PlayerFactionStore.class);
    
    @Deprecated
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
        String cfId = Misc.getCommissionFactionId();
        if (cfId == null) return Factions.PLAYER;
        return cfId;
    }
    
    public static FactionAPI getPlayerFaction()
    {
        return Global.getSector().getFaction(getPlayerFactionId());
    }
    
    public static void saveIndependentPlayerRelation(String factionId)
    {
        SectorAPI sector = Global.getSector();
        Map<String, Object> data = sector.getPersistentData();
        Map<String, Float> storedRelations = (Map<String, Float>)data.get(PLAYER_RELATIONS_KEY);
        if (storedRelations == null) storedRelations = new HashMap<>();
        
        FactionAPI playerFaction = sector.getFaction(Factions.PLAYER);
        float relation = playerFaction.getRelationship(factionId);
        storedRelations.put(factionId, relation);
        data.put(PLAYER_RELATIONS_KEY, storedRelations);
    }
    
    public static void saveIndependentPlayerRelations()
    {
        SectorAPI sector = Global.getSector();
        Map<String, Object> data = sector.getPersistentData();
        Map<String, Float> storedRelations = new HashMap<>();
        FactionAPI playerFaction = sector.getFaction(Factions.PLAYER);
        for (FactionAPI faction : sector.getAllFactions())
        {
            float relation = playerFaction.getRelationship(faction.getId());
            storedRelations.put(faction.getId(), relation);
            log.info("Saving independent player relations with " + faction.getDisplayName() + " as " + relation);
        }
        data.put(PLAYER_RELATIONS_KEY, storedRelations);
    }
    
    @Deprecated
    public static void loadIndependentPlayerRelations(boolean retainWithCurrentFaction)
    {
        SectorAPI sector = Global.getSector();
        Map<String, Object> data = sector.getPersistentData();
        Map<String, Float> storedRelations = (Map<String, Float>)data.get(PLAYER_RELATIONS_KEY);
        if (storedRelations == null) return;
        
        for (FactionAPI faction : sector.getAllFactions())
        {
            Float relation = storedRelations.get(faction.getId());
            if (relation != null && (!retainWithCurrentFaction || !faction.getId().equals(factionId)))
            {
                faction.setRelationship(Factions.PLAYER, relation);
                log.info("Loading independent player relations with " + faction.getDisplayName() + " as " + relation);
            }
        }
        //ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
    }
}
