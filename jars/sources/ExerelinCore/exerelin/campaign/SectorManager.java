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
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
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
    private Map<String, String> systemToRelayMap;
    private boolean victoryHasOccured;
    
    private int numSlavesRecentlySold;
    private MarketAPI marketLastSoldSlaves;

    public SectorManager()
    {
        super(true);
        String[] temp = ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector());
        liveFactionIds = new ArrayList<>();
        factionIdsAtStart = new ArrayList<>();
        numSlavesRecentlySold = 0;
        marketLastSoldSlaves = null;
        
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
        if (numSlavesRecentlySold > 0)
        {
            handleSlaveTradeRep();
            numSlavesRecentlySold = 0;
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
            if (marketLastSoldSlaves.getPrimaryEntity().isInOrNearSystem(market.getStarSystem())) continue;	// station capture news is sector-wide
            if (seenFactions.contains(factionId)) continue;

            seenFactions.add(factionId);
            factionsToNotify.add(factionId);
        }
        float repPenalty = ExerelinConfig.prisonerSlaveRepValue * numSlavesRecentlySold;
        
        Map<String, Object> params = new HashMap<>();

        params.put("factionsToNotify", factionsToNotify);
        params.put("repPenalty", repPenalty);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(marketLastSoldSlaves), "exerelin_slaves_sold", params);
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
        if (!defeatedId.equals(PlayerFactionStore.getPlayerFactionId()))
        {
            if (!ExerelinUtilsFaction.isPirateFaction(defeatedId))
            {
                for (FactionAPI faction : Global.getSector().getAllFactions())
                {
                    if (!ExerelinUtilsFaction.isPirateFaction(faction.getId()) && !faction.getId().equals(defeatedId))
                    {
                        faction.setRelationship(defeatedId, 0f);
                    }
                }
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
        List<SectorEntityToken> linkedEntities = market.getConnectedEntities();
        for (SectorEntityToken entity : linkedEntities)
        {
                entity.setFaction(newOwnerId);
        }
        market.setFactionId(newOwnerId);
        List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
        for (SubmarketAPI submarket : submarkets)
        {
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
        
        // FIXME: probably needs to be more robust (what if the star system has both a HQ and regional capital?
        if (market.hasCondition("regional_capital") || market.hasCondition("headquarters"))
        {
            StarSystemAPI loc = market.getStarSystem();
            if (sectorManager != null)
            {
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
}
