package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.world.InvasionFleetManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class SectorManager extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(SectorManager.class);
    private static SectorManager sectorManager;

    private static final String MANAGER_MAP_KEY = "exerelin_sectorManager";
    
    private List<String> factionIdsAtStart;
    private List<String> liveFactionIds;
    private List<String> historicFactionIds;
    private Map<String, String> systemToRelayMap;
    private Map<String, String> planetToRelayMap;
    private boolean victoryHasOccured = false;
    private boolean respawnFactions = false;
    private boolean onlyRespawnStartingFactions = false;
    
    private int numSlavesRecentlySold = 0;
    private MarketAPI marketLastSoldSlaves = null;
    
    private float respawnInterval = 60f;
    private final IntervalUtil respawnIntervalUtil;
    
    private boolean wantExpelPlayerFromFaction = false;

    public SectorManager()
    {
        super(true);
        String[] temp = ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector());
        liveFactionIds = new ArrayList<>();
        factionIdsAtStart = new ArrayList<>();
        historicFactionIds = new ArrayList<>();
        
        for (String factionId:temp)
        {
            if (ExerelinUtilsFaction.getFactionMarkets(factionId).size() > 0)
            {
                liveFactionIds.add(factionId);
                factionIdsAtStart.add(factionId);
                historicFactionIds.add(factionId);
            }   
        }
        respawnFactions = ExerelinSetupData.getInstance().respawnFactions;
        onlyRespawnStartingFactions = ExerelinSetupData.getInstance().onlyRespawnStartingFactions;
        respawnInterval = ExerelinConfig.factionRespawnInterval;
        respawnIntervalUtil = new IntervalUtil(respawnInterval * 0.75F, respawnInterval * 1.25F);
    }
   
    @Override
    public void advance(float amount)
    {
        if (numSlavesRecentlySold > 0)
        {
            handleSlaveTradeRep();
            numSlavesRecentlySold = 0;
        }
        if (wantExpelPlayerFromFaction)
        {
            wantExpelPlayerFromFaction = false;
            expelPlayerFromFaction();
        }
        
        if (respawnFactions){
            float days = Global.getSector().getClock().convertToDays(amount);
            respawnIntervalUtil.advance(days);
            if (respawnIntervalUtil.intervalElapsed()) {
                handleFactionRespawn();
                
                respawnInterval = ExerelinConfig.factionRespawnInterval;
                respawnIntervalUtil.setInterval(respawnInterval * 0.75F, respawnInterval * 1.25F);
            }
        }
    }
    
    // adds prisoners to loot
    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
        CampaignFleetAPI loser = plugin.getLoser();
        if (loser == null) return;
        
        int fp = 0;
        int crew = 0;
        List<FleetMemberAPI> fleetCurrent = loser.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : loser.getFleetData().getSnapshot()) {
            if (!fleetCurrent.contains(member)) {
                fp += member.getFleetPointCost();
                crew += member.getNeededCrew();
            }
        }
        for (int i=0; i<fp; i = i + 10)
        {
            if (Math.random() < ExerelinConfig.prisonerLootChancePer10Fp)
            {
                loot.addCommodity("prisoner", 1);
            }
        }
        crew = (int)(crew*ExerelinConfig.crewLootMult*MathUtils.getRandomNumberInRange(0.5f, 1.5f));
        crew = crew + MathUtils.getRandomNumberInRange(-3, 3);
        if (crew > 0) loot.addCrew(CargoAPI.CrewXPLevel.GREEN, crew);
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
    
    public void handleSlaveTradeRep()
    {
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        List<String> factionsToNotify = new ArrayList<>();  
        Set<String> seenFactions = new HashSet<>();

        for (final MarketAPI market : markets) {
            String factionId = market.getFactionId();
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;
            if (factionId.equals("templars")) continue;
            if (marketLastSoldSlaves.getPrimaryEntity().isInOrNearSystem(market.getStarSystem())) continue;	// station capture news is sector-wide
            if (seenFactions.contains(factionId)) continue;

            seenFactions.add(factionId);
            factionsToNotify.add(factionId);
        }
        //log.info("Selling " + numSlavesRecentlySold + " slaves; rep penalty for each is " + ExerelinConfig.prisonerSlaveRepValue);
        float repPenalty = ExerelinConfig.prisonerSlaveRepValue * numSlavesRecentlySold;
        
        Map<String, Object> params = new HashMap<>();

        params.put("factionsToNotify", factionsToNotify);
        params.put("repPenalty", repPenalty);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(marketLastSoldSlaves), "exerelin_slaves_sold", params);
    }
    
    public void handleFactionRespawn()
    {
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
        List<String> factionIds = factionIdsAtStart;
        if (!onlyRespawnStartingFactions)
        {
            factionIds = new ArrayList<>(Arrays.asList(ExerelinSetupData.getInstance().getAvailableFactions(sector)));
        }
        
        for(String factionId : factionIds)
        {
            if (factionId.equals("player_npc")) continue;
            if (!liveFactionIds.contains(factionId)) factionPicker.add(Global.getSector().getFaction(factionId));
        }
        
        FactionAPI respawnFaction = factionPicker.pick();
        if (respawnFaction == null) return;
        
        boolean allowPirates = ExerelinConfig.allowPirateInvasions;
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        for (MarketAPI market : markets) 
        {
            FactionAPI marketFaction = market.getFaction();
            if (!allowPirates && ExerelinUtilsFaction.isPirateFaction(marketFaction.getId()))
                continue;
            int size = market.getSize();
            if (size < 4) continue;
            
            if (market.hasCondition("headquarters")) size *= 0.1f;
            targetPicker.add(market, size);
        }
        MarketAPI targetMarket = (MarketAPI)targetPicker.pick();
        if (targetMarket == null) {
            return;
        }
        
        for (MarketAPI market : markets) 
        {
            FactionAPI marketFaction = market.getFaction();
            float weight = 100;
            if (marketFaction.isHostileTo(respawnFaction)) weight = 0.0001f;
            sourcePicker.add(market, weight);
        }
        
        MarketAPI sourceMarket = (MarketAPI)sourcePicker.pick();
        if (sourceMarket == null) {
            return;
        }
        
        //log.info("Respawn fleet created for " + respawnFaction.getDisplayName());
        InvasionFleetManager.spawnRespawnFleet(respawnFaction, sourceMarket, targetMarket);
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
        
        String defeatedId = defeated.getId();
        if (!defeatedId.equals(PlayerFactionStore.getPlayerFactionId()) && !ExerelinUtilsFaction.isPirateFaction(defeatedId) && !defeatedId.equals("templars"))
        {
            for (FactionAPI faction : Global.getSector().getAllFactions())
            {
                String factionId = faction.getId();
                if (!ExerelinUtilsFaction.isPirateFaction(factionId) && !factionId.equals(defeatedId) && !factionId.equals("templars"))
                {
                    faction.setRelationship(defeatedId, 0f);
                }
            }
        }
        checkForVictory();
    }
    
    public static void factionRespawned(FactionAPI faction, MarketAPI market)
    {
        Map<String, Object> params = new HashMap<>();
        boolean existedBefore = false;
        if (sectorManager != null)
        {
            existedBefore = sectorManager.historicFactionIds.contains(faction.getId());
        }
        params.put("existedBefore", existedBefore);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_respawned", params);
        SectorManager.addLiveFactionId(faction.getId());
        if (sectorManager != null && !existedBefore)
        {
            sectorManager.historicFactionIds.add(faction.getId());
        }
    }
    
    public static void checkForVictory()
    {
        if (sectorManager == null) return;
        if (sectorManager.victoryHasOccured) return;
        //FactionAPI faction = Global.getSector().getFaction(factionId);
        SectorAPI sector = Global.getSector();
        String playerFactionId = PlayerFactionStore.getPlayerFactionId();
        
        List<String> liveFactions = getLiveFactionIdsCopy();
        for (String factionId : getLiveFactionIdsCopy())
        {
            if (ExerelinUtilsFaction.isPirateFaction(factionId) && !ExerelinConfig.countPiratesForVictory)
            {
                liveFactions.remove(factionId);
            }
        }
        
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
                if (faction.isNeutralFaction()) continue;
                if (!faction.isAtWorst(playerFactionId, RepLevel.FRIENDLY))
                    return;
            }
            Map<String, Object> params = new HashMap<>();
            params.put("victorFactionId", playerFactionId);
            params.put("diplomaticVictory", true);
            params.put("playerVictory", true);
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(sector.getPlayerFleet()), "exerelin_victory", params);
            sectorManager.victoryHasOccured = true;
        }
    }
    
    public static void captureMarket(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, List<String> factionsToNotify, float repChangeStrength)
    {
        // transfer market and associated entities
        String newOwnerId = newOwner.getId();
        String oldOwnerId = oldOwner.getId();
        List<SectorEntityToken> linkedEntities = market.getConnectedEntities();
        for (SectorEntityToken entity : linkedEntities)
        {
                entity.setFaction(newOwnerId);
        }
        market.setFactionId(newOwnerId);
        if (newOwnerId.equals("templars") && !oldOwnerId.equals("templars"))
        {
            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
            market.removeSubmarket(Submarkets.SUBMARKET_BLACK);
            market.removeSubmarket(Submarkets.GENERIC_MILITARY);
            
            market.addSubmarket("tem_templarmarket");
        }
        else if (!newOwnerId.equals("templars") && oldOwnerId.equals("templars"))
        {
            market.addSubmarket(Submarkets.SUBMARKET_OPEN);
            market.addSubmarket(Submarkets.SUBMARKET_BLACK);
            if (market.hasCondition("military_base")) market.addSubmarket(Submarkets.GENERIC_MILITARY);
            
            market.removeSubmarket("tem_templarmarket");
        }
        
        ExerelinFactionConfig newOwnerConfig = ExerelinConfig.getExerelinFactionConfig(newOwnerId);
        if (newOwnerConfig.freeMarket)
        {
            if (!market.hasCondition("free_market")) market.addCondition("free_market");
            market.getTariff().modifyFlat("isFreeMarket", 0.1f);
        }
        else 
        {
            market.removeCondition("free_market");
            market.getTariff().modifyFlat("isFreeMarket", 0.2f);
        }
        
        List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
        
        for (SubmarketAPI submarket : submarkets)
        {
            if (submarket.getFaction() != oldOwner) continue;
            String submarketName = submarket.getNameOneLine().toLowerCase();
            if(!submarketName.contains("storage") && !submarketName.contains("black market"))
            {
                submarket.setFaction(newOwner);
            }
        }
        market.reapplyConditions();
        Map<String, Object> params = new HashMap<>();
        params.put("newOwner", newOwner);
        params.put("oldOwner", oldOwner);
        params.put("playerInvolved", playerInvolved);
        params.put("factionsToNotify", factionsToNotify);
        params.put("repChangeStrength", repChangeStrength);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_market_captured", params);
                
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
        
        // flip relay
        if (sectorManager != null)
        {
            boolean flipRelay = false;
            if (sectorManager.planetToRelayMap != null)   // reverse compatibility; may not have been set
            {
                flipRelay = sectorManager.planetToRelayMap.containsKey(market.getPrimaryEntity().getId());
            }
            else
            {
                flipRelay = market.hasCondition("regional_capital") || market.hasCondition("headquarters");
            }
            
            if (flipRelay)
            {
                StarSystemAPI loc = market.getStarSystem();
                String relayId = sectorManager.systemToRelayMap.get(loc.getId());
                if (relayId != null)
                {
                    SectorEntityToken relay = Global.getSector().getEntityById(relayId);
                    relay.setFaction(newOwnerId);
                }
            }
        }
    }
    
    public static void notifySlavesSold(MarketAPI market, int count)
    {
        if (sectorManager == null) return;
        sectorManager.numSlavesRecentlySold += count;
        sectorManager.marketLastSoldSlaves = market;
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
    
    public static void setSystemToRelayMap(Map<String, String> map)
    {
        if (sectorManager == null) return;
        sectorManager.systemToRelayMap = map;
    }
    
    public static void setPlanetToRelayMap(Map<String, String> map)
    {
        if (sectorManager == null) return;
        sectorManager.planetToRelayMap = map;
    }
    
    private static void expelPlayerFromFaction()
    {
        String oldFactionId = PlayerFactionStore.getPlayerFactionId();
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (playerAlignedFactionId.equals("player_npc")) return;

        SectorAPI sector = Global.getSector();
        FactionAPI newFaction = sector.getFaction("player_npc");
        FactionAPI oldFaction = sector.getFaction(oldFactionId);

        PlayerFactionStore.loadIndependentPlayerRelations(true);
        PlayerFactionStore.setPlayerFactionId("player_npc");
        ExerelinUtilsReputation.syncFactionRelationshipsToPlayer("player_npc");

        CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, "exerelin_faction_changed");
        if (eventSuper == null) 
            eventSuper = sector.getEventManager().startEvent(null, "exerelin_faction_changed", null);
        FactionChangedEvent event = (FactionChangedEvent)eventSuper;

        MarketAPI market = ExerelinUtils.getClosestMarket(oldFactionId);
        event.reportEvent(oldFaction, newFaction, "expelled", market.getPrimaryEntity());
    }
    
    public static void scheduleExpelPlayerFromFaction()
    {
        if (sectorManager == null) return;
        sectorManager.wantExpelPlayerFromFaction = true;
    }
}
