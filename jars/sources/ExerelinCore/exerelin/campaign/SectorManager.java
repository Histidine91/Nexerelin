package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class SectorManager extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(SectorManager.class);
    private static SectorManager sectorManager;

    private static final String MANAGER_MAP_KEY = "exerelin_sectorManager";
    
    private List<String> factionIdsAtStart;
    private List<String> liveFactionIds;
    private boolean victoryHasOccured;

    public SectorManager()
    {
        super(true);
        String[] temp = ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector());
        liveFactionIds = new ArrayList<>();
        factionIdsAtStart = new ArrayList<>();
        for (String factionId:temp)
        {
            if (ExerelinUtilsFaction.getFactionMarkets(factionId).size() > 0)
            {
                liveFactionIds.add(factionId);
                factionIdsAtStart.add(factionId);
            }   
        }
        victoryHasOccured = false;
    }

    @Override
    public void advance(float amount)
    {
        
    }
    
    @Override
    public boolean isDone()
    {
        return false;
    }
    
    @Override
    public boolean runWhilePaused()
    {
        return false;
    }
    
    public static SectorManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        sectorManager = (SectorManager)data.get(MANAGER_MAP_KEY);
        if (sectorManager != null)
            return sectorManager;
        
        sectorManager = new SectorManager();
        data.put(MANAGER_MAP_KEY, sectorManager);
        return sectorManager;
    }
    
    public static void factionEliminated(FactionAPI victor, FactionAPI defeated, MarketAPI market)
    {
        if (defeated.getId().equals("independent"))
            return;
        removeLiveFactionId(defeated.getId());
        Map<String, Object> params = new HashMap<>();
        params.put("defeatedFaction", defeated);
        params.put("victorFaction", victor);
        FactionAPI playerFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
        params.put("playerDefeated", defeated == playerFaction);
        params.put("playerVictory", victor == playerFaction && getLiveFactionIdsCopy().size() == 1);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_eliminated", params);
        
        if (!defeated.getId().equals("pirates"))
        {
            for (FactionAPI faction : Global.getSector().getAllFactions())
            {
                if (!faction.getId().equals("pirates"))
                    faction.setRelationship(defeated.getId(), 0);
            }
        }
        checkForVictory();
    }
    
    public static void factionRespawned(FactionAPI faction, MarketAPI market)
    {
        Map<String, Object> params = new HashMap<>();
        boolean originalFaction = false;
        if (sectorManager != null)
            originalFaction = sectorManager.factionIdsAtStart.contains(faction.getId());
        params.put("originalFaction", originalFaction);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_respawned", params);
        SectorManager.addLiveFactionId(faction.getId());
    }
    
    public static void checkForVictory()
    {
        if (sectorManager == null) return;
        if (sectorManager.victoryHasOccured) return;
        //FactionAPI faction = Global.getSector().getFaction(factionId);
        SectorAPI sector = Global.getSector();
        String playerFactionId = PlayerFactionStore.getPlayerFactionId();
        List<String> liveFactions = getLiveFactionIdsCopy();
        if (liveFactions.size() == 1)   // conquest victory
        {
            String victorFactionId = liveFactions.get(0);
            Map<String, Object> params = new HashMap<>();
            boolean playerVictory = victorFactionId.equals(PlayerFactionStore.getPlayerFactionId());
            params.put("victorFactionId", victorFactionId);
            params.put("diplomaticVictory", false);
            params.put("playerVictory", playerVictory);
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(sector.getPlayerFleet()), "exerelin_victory", params);
            sectorManager.victoryHasOccured = true;
        }
        else {
            // diplomatic victory
            for(String factionId : liveFactions)
            {
                FactionAPI faction = sector.getFaction(factionId);
                if (!faction.isAtWorst(playerFactionId, RepLevel.FRIENDLY))
                    return;
            }
            Map<String, Object> params = new HashMap<>();
            boolean playerVictory = true;
            params.put("victorFactionId", playerFactionId);
            params.put("diplomaticVictory", true);
            params.put("playerVictory", true);
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(sector.getPlayerFleet()), "exerelin_victory", params);
            sectorManager.victoryHasOccured = true;
        }
    }
    
    public static void notifyMarketCaptured(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner)
    {
        DiplomacyManager.notifyMarketCaptured(market, oldOwner, newOwner);
        
        int marketsRemaining = ExerelinUtilsFaction.getFactionMarkets(oldOwner.getId()).size();
        log.info("Faction " + oldOwner.getDisplayName() + " has " + marketsRemaining + " markets left");
        if (marketsRemaining == 0)
        {
            factionEliminated(newOwner, oldOwner, market);
        }
        
        marketsRemaining = ExerelinUtilsFaction.getFactionMarkets(newOwner.getId()).size();
        if (marketsRemaining == 1)
        {
            factionRespawned(newOwner, market);
        }
    }

    public static void addLiveFactionId(String factionId)
    {
        if (sectorManager == null) return;
        if (!sectorManager.liveFactionIds.contains(factionId))
            sectorManager.liveFactionIds.add(factionId);
    }
    
    public static void removeLiveFactionId(String factionId)
    {
        if (sectorManager == null) return;
        if (sectorManager.liveFactionIds.contains(factionId))
            sectorManager.liveFactionIds.remove(factionId);
    }
    
    public static ArrayList<String> getLiveFactionIdsCopy()
    {
        if (sectorManager == null) return new ArrayList<>();
        return new ArrayList<>(sectorManager.liveFactionIds);
    }
    
    public static boolean isFactionAlive(String factionId)
    {
        if (sectorManager == null) return false;
        return sectorManager.liveFactionIds.contains(factionId);
    }
}
