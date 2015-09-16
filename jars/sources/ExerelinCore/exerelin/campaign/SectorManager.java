package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.ExerelinCorvusLocations;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.world.InvasionFleetManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class SectorManager extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(SectorManager.class);
    private static SectorManager sectorManager;

    private static final String MANAGER_MAP_KEY = "exerelin_sectorManager";
    
    private List<String> factionIdsAtStart = new ArrayList<>();
    private List<String> liveFactionIds = new ArrayList<>();
    private List<String> historicFactionIds = new ArrayList<>();
    private Map<String, String> systemToRelayMap;
    private Map<String, String> planetToRelayMap;
    private boolean victoryHasOccured = false;
    private boolean respawnFactions = false;
    private boolean onlyRespawnStartingFactions = false;
    
    protected boolean corvusMode = false;
    protected boolean hardMode = false;
    
    private int numSlavesRecentlySold = 0;
    private MarketAPI marketLastSoldSlaves = null;
    
    private float respawnInterval = 60f;
    private final IntervalUtil respawnIntervalUtil;
    
    private boolean wantExpelPlayerFromFaction = false;

    public SectorManager()
    {
        super(true);
        //SectorManager.reinitLiveFactions();
        respawnFactions = ExerelinSetupData.getInstance().respawnFactions;
        onlyRespawnStartingFactions = ExerelinSetupData.getInstance().onlyRespawnStartingFactions;
        respawnInterval = ExerelinConfig.factionRespawnInterval;
        respawnIntervalUtil = new IntervalUtil(respawnInterval * 0.75F, respawnInterval * 1.25F);
        
        // Templars don't normally post bounties, but they do here
        //if (Arrays.asList(ExerelinSetupData.getInstance().getPossibleFactions()).contains("templars"))
        //    SharedData.getData().getPersonBountyEventData().addParticipatingFaction("templars");
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
        String loserFactionId = loser.getFaction().getId();
        ExerelinFactionConfig loserConfig = ExerelinConfig.getExerelinFactionConfig(loserFactionId);
        if (loserConfig != null && loserConfig.dropPrisoners == false)
            return;
        
        int numSurvivors = 0;
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
                numSurvivors++;
            }
        }
        crew = (int)(crew*ExerelinConfig.crewLootMult*MathUtils.getRandomNumberInRange(0.5f, 1.5f));
        crew = crew + MathUtils.getRandomNumberInRange(-3, 3);
        if (crew > 0) {
            loot.addCrew(CargoAPI.CrewXPLevel.GREEN, crew);
            numSurvivors += crew;
        }
        
        StatsTracker.getStatsTracker().modifyOrphansMadeByCrewCount(-numSurvivors, loserFactionId);
    }
    
    
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        boolean playerWin = result.didPlayerWin();
        EngagementResultForFleetAPI fleetResult = result.getWinnerResult();
        if (playerWin) fleetResult = result.getLoserResult();
        FactionAPI faction = fleetResult.getFleet().getFaction();
        
        // relationship is _before_ the reputation penalty caused by the combat
        if (faction.isHostileTo("player")) return;
        
        createWarmongerEvent(faction.getId(), fleetResult.getFleet());
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
    
    public static void setCorvusMode(boolean mode)
    {
        if (sectorManager == null) return;
        sectorManager.corvusMode = mode;
    }
    
    public static boolean getCorvusMode()
    {
        if (sectorManager == null) return false;
        return sectorManager.corvusMode;
    }
    
    public static void setHardMode(boolean mode)
    {
        if (sectorManager == null) return;
        sectorManager.hardMode = mode;
    }
    
    public static boolean getHardMode()
    {
        if (sectorManager == null) return false;
        return sectorManager.hardMode;
    }
    
    public static void createWarmongerEvent(String targetFactionId, SectorEntityToken location)
    {
        if (!ExerelinConfig.warmongerPenalty) return;
        
        FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		if (targetFaction.isHostileTo(Factions.PLAYER)) return;
		
        int numFactions = 0;
        float totalRepLoss = 0;
        float myFactionLoss = 0;
        Map<String, Float> repLoss = new HashMap<>();
        List<String> factions = SectorManager.getLiveFactionIdsCopy();
        for (String factionId : factions)
        {
            if (factionId.equals(targetFactionId)) continue;
            //if (factionId.equals("player_npc")) continue;
            if (targetFaction.isHostileTo(factionId)) continue;
            
            float loss = 0;
            RepLevel level = targetFaction.getRelationshipLevel(factionId);
            if (level == RepLevel.COOPERATIVE)
                loss = 30;
            else if (level == RepLevel.FRIENDLY)
                loss = 24;
            else if (level == RepLevel.WELCOMING)
                loss = 18;
            else if (level == RepLevel.FAVORABLE)
                loss = 12;
            else if (level == RepLevel.NEUTRAL)
                loss = 8;
            else if (level == RepLevel.SUSPICIOUS)
                loss = 5;
            //else if (level == RepLevel.INHOSPITABLE)
            //    loss = 2;
            
            loss *= 0.01f;
            
            if (factionId.equals(playerAlignedFactionId))
            {
                myFactionLoss = loss;
                if (!factionId.equals("player_npc")) myFactionLoss = (2*loss) + 0.05f;
                repLoss.put(factionId, myFactionLoss);
                continue;
            }
            if (loss <= 0) continue;
            
            numFactions++;
            totalRepLoss += loss;
            repLoss.put(factionId, loss);
        }
        if (numFactions == 0 && myFactionLoss == 0) return;
        
        Map<String, Object> params = new HashMap<>();
        params.put("avgRepLoss", totalRepLoss/(float)numFactions);
        params.put("numFactions", numFactions);
        params.put("repLoss", repLoss);
        params.put("myFactionLoss", myFactionLoss);
        params.put("targetFaction", targetFactionId);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(location), "exerelin_warmonger", params);
    }
    
    public void handleSlaveTradeRep()
    {
        LocationAPI loc = marketLastSoldSlaves.getPrimaryEntity().getContainingLocation();
        List<MarketAPI> markets = Misc.getMarketsInLocation(loc);
        List<String> factionsToNotify = new ArrayList<>();  
        Set<String> seenFactions = new HashSet<>();

        for (final MarketAPI market : markets) {
            String factionId = market.getFactionId();
            if (ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId)) continue;
            if (factionId.equals("player_npc")) continue;
            if (seenFactions.contains(factionId)) continue;

            seenFactions.add(factionId);
            factionsToNotify.add(factionId);
        }
		if (factionsToNotify.isEmpty()) return;
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
            if (marketFaction.isNeutralFaction() || marketFaction.isPlayerFaction()) continue; 
            if (marketFaction.getId().equals("independent")) continue;
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
        AllianceManager.leaveAlliance(defeated.getId(), true);
        removeLiveFactionId(defeated.getId());
        Map<String, Object> params = new HashMap<>();
        params.put("defeatedFaction", defeated);
        params.put("victorFaction", victor);
        FactionAPI playerFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
        params.put("playerDefeated", defeated == playerFaction);
        //params.put("playerVictory", victor == playerFaction && getLiveFactionIdsCopy().size() == 1);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_eliminated", params);
        
        String defeatedId = defeated.getId();
        if (!defeatedId.equals(PlayerFactionStore.getPlayerFactionId()) 
                && !ExerelinUtilsFaction.isPirateOrTemplarFaction(defeatedId)
                && !ExerelinUtilsFaction.isExiInCorvus(defeatedId))
        {
            for (FactionAPI faction : Global.getSector().getAllFactions())
            {
                String factionId = faction.getId();
                if (!ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId)
                        && !ExerelinUtilsFaction.isExiInCorvus(factionId)
                        && !factionId.equals(defeatedId))
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
        
        if (sector.isInNewGameAdvance()) return;
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = Global.getSector().getFaction(playerAlignedFactionId);
        String victorFactionId = playerAlignedFactionId;
        VictoryType victoryType = VictoryType.CONQUEST;
        
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
            victorFactionId = liveFactions.get(0);
            if (!victorFactionId.equals(playerAlignedFactionId))
            {
                if (sector.getFaction(Factions.PLAYER).isAtBest(victorFactionId, RepLevel.WELCOMING))
                {
                    victoryType = VictoryType.DEFEAT_CONQUEST;
                }
                else {
                    victoryType = VictoryType.CONQUEST_ALLY;
                }
            }
            sectorManager.victoryHasOccured = true;
        }
        else {
            // diplomatic victory         
            List<String> eligibleWinners = new ArrayList<>();
            for(String factionId : liveFactions)
            {
                boolean canWin = true;
                FactionAPI faction = sector.getFaction(factionId);
                if (faction.isNeutralFaction()) continue;
                for (String otherFactionId: liveFactions)
                {
                    if (!faction.isAtWorst(otherFactionId, RepLevel.FRIENDLY))
                    {
                        canWin = false;
                        break;
                    }
                }
                if (canWin) eligibleWinners.add(factionId);
            }
            if (eligibleWinners.isEmpty()) return;
            String winner = eligibleWinners.get(0);
            int largestPopulation = 0;
            if (eligibleWinners.size() > 1)
            {
                for (String factionId : eligibleWinners)
                {
                    int pop = ExerelinUtilsFaction.getFactionPopulation(factionId);
                    if (pop > largestPopulation)
                    {
                        winner = factionId;
                        largestPopulation = pop;
                    }
                }
            }
            if (winner.equals(playerAlignedFactionId)) victoryType = VictoryType.DIPLOMATIC;
            else if (playerAlignedFaction.isAtWorst(winner, RepLevel.FRIENDLY)) victoryType = VictoryType.DIPLOMATIC_ALLY;
            else victoryType = VictoryType.DEFEAT_DIPLOMATIC;
            
            victorFactionId = winner;
            sectorManager.victoryHasOccured = true;
        }
        
        if (sectorManager.victoryHasOccured)
        {
            Global.getSector().addScript(new VictoryScreenScript(victorFactionId, victoryType));
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
        
        // Templar stuff
        if (newOwnerId.equals("templars") && !oldOwnerId.equals("templars"))
        {
            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
            market.removeSubmarket(Submarkets.SUBMARKET_BLACK);
            market.removeSubmarket(Submarkets.GENERIC_MILITARY);
            
            market.addSubmarket("tem_templarmarket");
            if (!market.hasCondition("exerelin_templar_control")) market.addCondition("exerelin_templar_control");
        }
        else if (!newOwnerId.equals("templars") && oldOwnerId.equals("templars"))
        {
            market.addSubmarket(Submarkets.SUBMARKET_OPEN);
            market.addSubmarket(Submarkets.SUBMARKET_BLACK);
            if (market.hasCondition("military_base") || market.hasCondition("tem_avalon")) 
                market.addSubmarket(Submarkets.GENERIC_MILITARY);
            
            market.removeSubmarket("tem_templarmarket");
            if (market.hasCondition("exerelin_templar_control")) market.removeCondition("exerelin_templar_control");
        }
        
        ExerelinFactionConfig newOwnerConfig = ExerelinConfig.getExerelinFactionConfig(newOwnerId);
        if (newOwnerConfig != null)
        {
            if (newOwnerConfig.freeMarket)
            {
                if (!market.hasCondition("free_market")) market.addCondition("free_market");
                market.getTariff().modifyMult("isFreeMarket", 0.5f);
            }
            else 
            {
                market.removeCondition("free_market");
                market.getTariff().unmodify("isFreeMarket");
            }
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
        if (playerInvolved) StatsTracker.getStatsTracker().notifyMarketCaptured(market);
        
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
            
            if (!flipRelay)
            {
                if (ExerelinCorvusLocations.getSystemCapitalsCopy().containsValue(market.getPrimaryEntity().getId()))
                    flipRelay = true;
                else flipRelay = market.hasCondition("regional_capital") || market.hasCondition("headquarters");
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
                else 
                {
                    List<SectorEntityToken> relays = loc.getEntitiesWithTag(Tags.COMM_RELAY);
                    log.info("#entities: " + relays.size());
                    if (!relays.isEmpty()) relays.get(0).setFaction(newOwnerId);
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
    
    public static void reinitLiveFactions()
    {
        if (sectorManager == null) return;
        String[] temp = ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector());
        sectorManager.liveFactionIds = new ArrayList<>();
        sectorManager.factionIdsAtStart = new ArrayList<>();
        sectorManager.historicFactionIds = new ArrayList<>();
        
        for (String factionId:temp)
        {
            if (ExerelinUtilsFaction.getFactionMarkets(factionId).size() > 0)
            {
                sectorManager.liveFactionIds.add(factionId);
                sectorManager.factionIdsAtStart.add(factionId);
                sectorManager.historicFactionIds.add(factionId);
            }   
        }
    }
    
    public static String getFirstStarName()
    {
        if (sectorManager != null && sectorManager.corvusMode == true) return "Corvus";
        
        String firstStar = "Exerelin";
        try {
                JSONObject planetConfig = Global.getSettings().loadJSON("data/config/exerelin/planetNames.json");
                JSONArray systemNames = planetConfig.getJSONArray("stars");
                firstStar = systemNames.getString(0);
        } catch (JSONException | IOException ex) {
                log.error(ex);
        }
        
        return firstStar;
    }
    
    public enum VictoryType
    {
        CONQUEST,
        CONQUEST_ALLY,
        DIPLOMATIC,
        DIPLOMATIC_ALLY,
        DEFEAT_CONQUEST,  //not a victory type but who's counting?
        DEFEAT_DIPLOMATIC
    }
}
