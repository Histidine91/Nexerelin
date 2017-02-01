package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.Status;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeDataForSubmarket;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.ExerelinCorvusLocations;
import exerelin.ExerelinConstants;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.world.InvasionFleetManager;
import exerelin.world.InvasionFleetManager.InvasionFleetData;
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
    protected static SectorManager sectorManager;

    protected static final String MANAGER_MAP_KEY = "exerelin_sectorManager";
    protected static final List<String> POSTS_TO_CHANGE_ON_CAPTURE = Arrays.asList(new String[]{
        Ranks.POST_BASE_COMMANDER,
        Ranks.POST_OUTPOST_COMMANDER,
        Ranks.POST_STATION_COMMANDER,
        Ranks.POST_PORTMASTER,
        Ranks.POST_SUPPLY_OFFICER,
    });
    
    protected static final Set<String> NO_BLACK_MARKET = new HashSet(Arrays.asList(new String[]{
        "SCY_overwatchStation",
        "SCY_hephaistosStation",
    }));
    protected static final Set<String> FORCE_MILITARY_MARKET = new HashSet(Arrays.asList(new String[]{
        "SCY_hephaistosStation",
    }));
    protected static final Set<String> ALWAYS_CAPTURE_SUBMARKET = new HashSet(Arrays.asList(new String[]{
        "tiandong_retrofit",
    }));
    
    protected List<String> factionIdsAtStart = new ArrayList<>();
    protected List<String> liveFactionIds = new ArrayList<>();
    protected Set<String> historicFactionIds = new HashSet<>();
    protected Map<String, Integer> factionRespawnCounts = new HashMap<>();
    protected Map<String, String> systemToRelayMap = new HashMap<>();
    protected Map<String, String> planetToRelayMap = new HashMap<>();
    
    protected boolean victoryHasOccured = false;
    protected boolean respawnFactions = false;
    protected boolean onlyRespawnStartingFactions = false;
    protected SectorEntityToken homeworld;
    
    protected boolean corvusMode = false;
    protected boolean hardMode = false;
    protected boolean freeStart = false;
    
    protected int numSlavesRecentlySold = 0;
    protected MarketAPI marketLastSoldSlaves = null;
    
    protected float respawnInterval = 60f;
    protected final IntervalUtil respawnIntervalUtil;
    
    protected boolean wantExpelPlayerFromFaction = false;

    public SectorManager()
    {
        super(true);
        //SectorManager.reinitLiveFactions();
        respawnFactions = ExerelinSetupData.getInstance().respawnFactions;
        onlyRespawnStartingFactions = ExerelinSetupData.getInstance().onlyRespawnStartingFactions;
        respawnInterval = ExerelinConfig.factionRespawnInterval;
        respawnIntervalUtil = new IntervalUtil(respawnInterval * 0.75F, respawnInterval * 1.25F);
        
        // Templars don't normally post bounties, but they do here
        //if (Arrays.asList(ExerelinSetupData.getInstance().getAvailableFactions()).contains("templars"))
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
        int prisoners = 0;
        float contrib = plugin.computePlayerContribFraction();
        List<FleetMemberData> casualties = plugin.getLoserData().getOwnCasualties();
        for (FleetMemberData member : casualties) {
            Status status = member.getStatus();
            if (status == Status.DESTROYED || status == Status.NORMAL) continue;
            fp += member.getMember().getFleetPointCost();
            crew += member.getMember().getMinCrew();
            //log.info("Enemy lost: " + member.getMember().getVariant().getFullDesignationWithHullName());
            
            // officers as prisoners
            PersonAPI captain = member.getMember().getCaptain();
            if (captain != null && !captain.isDefault())
            {
                float survivalChance = 1f - (0.5f * member.getMember().getStats().getCrewLossMult().modified);
                float captureChance = 0.3f + (0.3f * captain.getStats().getLevel() / 20);    // FIXME magic number
                if (Math.random() < survivalChance * captureChance)
                    prisoners++;
            }
        }
        
        // old random prisoner drops
        for (int i=0; i<fp; i = i + 10)
        {
            if (Math.random() < ExerelinConfig.prisonerLootChancePer10Fp)
            {
                prisoners++;
            }
        }
        
        prisoners = (int)(prisoners * contrib + 0.5f);
        loot.addCommodity("prisoner", prisoners);
        numSurvivors += prisoners;
        
        crew = (int)(crew*ExerelinConfig.crewLootMult*MathUtils.getRandomNumberInRange(0.5f, 1.5f));
        crew = crew + MathUtils.getRandomNumberInRange(-3, 3);
        crew = (int)(crew * contrib);
        if (crew > 0) {
            loot.addCrew(CargoAPI.CrewXPLevel.GREEN, crew);
            numSurvivors += crew;
        }
        
        StatsTracker.getStatsTracker().modifyOrphansMadeByCrewCount(-numSurvivors, loserFactionId);
    }
    
    /*
    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) return;
        CampaignFleetAPI fleet = battle.getPrimary(battle.getNonPlayerSide());
        FactionAPI faction = fleet.getFaction();
        
        // relationship is _before_ the reputation penalty caused by the combat
        if (faction.isHostileTo("player")) return;
        if (fleet.getMemoryWithoutUpdate().getBoolean("$exerelinFleetAggressAgainstPlayer")) return;
        if (!fleet.knowsWhoPlayerIs()) return;
        
        log.info("Checking for warmonger event");
        boolean losses = false;
        List<FleetMemberAPI> currentMembers = fleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
            if (!currentMembers.contains(member)) {
                losses = true;
                break;
            }
        }
        if (losses) createWarmongerEvent(faction.getId(), fleet);
    }
    */
    
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        boolean playerWin = result.didPlayerWin();
        EngagementResultForFleetAPI fleetResult = result.getWinnerResult();
        if (playerWin) fleetResult = result.getLoserResult();
        FactionAPI faction = fleetResult.getFleet().getFaction();
        
        // relationship is _before_ the reputation penalty caused by the combat
        if (faction.isHostileTo("player")) return;
        if (fleetResult.getDisabled().isEmpty() && fleetResult.getDestroyed().isEmpty()) return;
        CampaignFleetAPI fleet = fleetResult.getFleet();
        if (fleet.getMemoryWithoutUpdate().getBoolean("$exerelinFleetAggressAgainstPlayer")) return;
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_LOW_REP_IMPACT)) return;
        if (!result.getBattle().isPlayerPrimary()) return;
        if (!fleet.knowsWhoPlayerIs()) return;
        
        createWarmongerEvent(faction.getId(), fleet);
    }
    
    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMORY_KEY_VISITED_BEFORE, true);
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
    
    public static boolean isSectorManagerSaved()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        sectorManager = (SectorManager)data.get(MANAGER_MAP_KEY);
        if (sectorManager != null)
            return true;
        
        return false;
    }
    
    public static void setCorvusMode(boolean mode)
    {
        if (sectorManager == null) return;
        sectorManager.corvusMode = mode;
    }
    
    public static boolean getCorvusMode()
    {
        if (sectorManager == null) create();    // try to make sure we have an answer for whoever calls this
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
        if (ExerelinConfig.warmongerPenalty == 0) return;
        
        FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (targetFaction.isHostileTo(Factions.PLAYER)) return;
        if (targetFactionId.equals(playerAlignedFactionId) || targetFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;
        
        int numFactions = 0;
        float totalRepLoss = 0;    // note: does not include the loss with player-aligned faction
        float myFactionLoss = 0;
        Map<String, Float> repLoss = new HashMap<>();
        List<String> factions = SectorManager.getLiveFactionIdsCopy();
        for (String factionId : factions)
        {
            if (factionId.equals(targetFactionId)) continue;
            //if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
            if (targetFaction.isHostileTo(factionId)) continue;
            if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID) && ExerelinConfig.warmongerPenalty <= 1) 
                continue;
            
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
                if (!factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) myFactionLoss = (2*loss) + 0.05f;
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
            FactionAPI faction = market.getFaction();
            String factionId = market.getFactionId();
            if (ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId)) continue;
            if (faction.isNeutralFaction()) continue;
            if (faction.isPlayerFaction()) continue;
            if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
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
    
    public static InvasionFleetData spawnRespawnFleet(FactionAPI respawnFaction, MarketAPI sourceMarket, boolean useOriginLoc)
    {
        SectorAPI sector = Global.getSector();
        String respawnFactionId = respawnFaction.getId();
        
        WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
        
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        for (MarketAPI market : markets) 
        {
            if (!ExerelinUtilsMarket.isValidInvasionTarget(market, 4))
                continue;
            
            int size = market.getSize();
            if (market.hasCondition("headquarters")) size *= 0.1f;
            targetPicker.add(market, size);
        }
        MarketAPI targetMarket = (MarketAPI)targetPicker.pick();
        if (targetMarket == null) {
            return null;
        }
        
        if (sourceMarket == null)
        {
            for (MarketAPI market : markets) 
            {
                FactionAPI marketFaction = market.getFaction();
                float weight = 100;
                if (market == targetMarket) continue;
                if (marketFaction.isHostileTo(respawnFaction)) weight = 0.0001f;
                sourcePicker.add(market, weight);
            }

            sourceMarket = (MarketAPI)sourcePicker.pick();
        }
        
        if (sourceMarket == null) {
            return null;
        }
        
        //log.info("Respawn fleet created for " + respawnFaction.getDisplayName());
        return InvasionFleetManager.spawnRespawnFleet(respawnFaction, sourceMarket, targetMarket, useOriginLoc);
    }
    
    public void handleFactionRespawn()
    {
        if (factionRespawnCounts == null)
        {
            factionRespawnCounts = new HashMap<>();
        }
        
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        
        List<String> factionIds = factionIdsAtStart;
        if (!onlyRespawnStartingFactions)
        {
            factionIds = ExerelinSetupData.getInstance().getPlayableFactions();
        }
        
        for(String factionId : factionIds)
        {
            if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
            if (factionId.equals(Factions.INDEPENDENT)) continue;
            ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
            if (config != null && !config.playableFaction) continue;
            
            // check if this faction has used up all its respawn chances
            int maxRespawns = ExerelinConfig.maxFactionRespawns;
            if (maxRespawns >= 0)
            {
                // note: zero maxRespawns means new factions can still enter, but factions that got knocked out can't return
                int count = -1;
                if (factionRespawnCounts.containsKey(factionId))
                    count = factionRespawnCounts.get(factionId);
                else if (factionIdsAtStart.contains(factionId))
                    count++;
                if (count >= maxRespawns)
                    continue;
            }
            
            if (!liveFactionIds.contains(factionId)) factionPicker.add(Global.getSector().getFaction(factionId));
        }
        
        FactionAPI respawnFaction = factionPicker.pick();
        if (respawnFaction == null) return;
        
        spawnRespawnFleet(respawnFaction, null, false);
    }
    
    public static void setShowFactionInIntelTab(String factionId, boolean show)
    {
        if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
            return;    // do nothing
        
        if (!show && ExerelinUtilsFaction.isExiInCorvus(factionId)) // don't hide Exi in Corvus mode
            return;
        
        ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (conf != null && !conf.showIntelEvenIfDead)
        {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            faction.setShowInIntelTab(show);
        }
    }
    
    public static void factionEliminated(FactionAPI victor, FactionAPI defeated, MarketAPI market)
    {
        if (defeated.getId().equals("independent"))
            return;
		if (!defeated.getId().equals(ExerelinConstants.PLAYER_NPC_ID))
			AllianceManager.leaveAlliance(defeated.getId(), true);
        removeLiveFactionId(defeated.getId());
        Map<String, Object> params = new HashMap<>();
        params.put("defeatedFaction", defeated);
        params.put("victorFaction", victor);
        FactionAPI playerFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
        params.put("playerDefeated", defeated == playerFaction);
        //params.put("playerVictory", victor == playerFaction && getLiveFactionIdsCopy().size() == 1);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_eliminated", params);
        
        //String defeatedId = defeated.getId();
        //DiplomacyManager.resetFactionRelationships(defeatedId);
        
        setShowFactionInIntelTab(defeated.getId(), false);
        
        ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(true);
        checkForVictory();
    }
    
    public static void factionRespawned(FactionAPI faction, MarketAPI market)
    {
        String factionId = faction.getId();
        Map<String, Object> params = new HashMap<>();
        boolean existedBefore = false;
        if (sectorManager != null)
        {
            existedBefore = sectorManager.historicFactionIds.contains(factionId);
        }
        params.put("existedBefore", existedBefore);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_respawned", params);
        SectorManager.addLiveFactionId(faction.getId());
        if (sectorManager != null && !existedBefore)
        {
            sectorManager.historicFactionIds.add(factionId);
        }
        
        setShowFactionInIntelTab(factionId, true);
        
        // increment "times respawned" count
        if (sectorManager != null)
        {
            int count = 0;
            if (sectorManager.factionRespawnCounts.containsKey(factionId))
                count = sectorManager.factionRespawnCounts.get(factionId) + 1;
            else if (sectorManager.factionIdsAtStart.contains(factionId))
                count++;
            sectorManager.factionRespawnCounts.put(factionId, count);
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
            // don't count pirate factions unless config says so or we belong to it
            if (ExerelinUtilsFaction.isPirateFaction(factionId) && !ExerelinConfig.countPiratesForVictory && !factionId.equals(playerAlignedFactionId))
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
	
	public static void retire() 
	{
		Global.getSector().addScript(new VictoryScreenScript(Factions.PLAYER, VictoryType.RETIRED));
		if (sectorManager != null)
			sectorManager.victoryHasOccured = true;
	}
    
    public static void captureMarket(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, List<String> factionsToNotify, float repChangeStrength)
    {
        // forcibly refreshes the market before capture so we can loot their faction-specific goodies once we capture it
        // already did this in InvasionRound
        //ExerelinUtilsMarket.forceMarketUpdate(market);
        
        // transfer market and associated entities
        String newOwnerId = newOwner.getId();
        String oldOwnerId = oldOwner.getId();
        List<SectorEntityToken> linkedEntities = market.getConnectedEntities();
        for (SectorEntityToken entity : linkedEntities)
        {
            entity.setFaction(newOwnerId);
        }
        List<PersonAPI> people = market.getPeopleCopy();
        for (PersonAPI person : people)
        {
            // TODO should probably switch them out completely instead of making them defect
            if (POSTS_TO_CHANGE_ON_CAPTURE.contains(person.getPostId()))
                person.setFaction(newOwnerId);
        }
        market.setFactionId(newOwnerId);
        
        // don't lock player out of freshly captured market
        if (!newOwner.isHostileTo(Factions.PLAYER))
        {
            market.getMemoryWithoutUpdate().unset("$playerHostileTimeout");
        }
        
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
            if (!NO_BLACK_MARKET.contains(market.getId()))
                market.addSubmarket(Submarkets.SUBMARKET_BLACK);
            if (market.hasCondition(Conditions.MILITARY_BASE) || market.hasCondition("tem_avalon") || FORCE_MILITARY_MARKET.contains(market.getId())) 
                market.addSubmarket(Submarkets.GENERIC_MILITARY);
            
            market.removeSubmarket("tem_templarmarket");
            if (market.hasCondition("exerelin_templar_control")) market.removeCondition("exerelin_templar_control");
        }
        
        // ApproLight
        if (newOwnerId.equals("approlight") && !oldOwnerId.equals("approlight"))
        {
            
            if (market.hasCondition(Conditions.MILITARY_BASE) || market.hasCondition("tem_avalon") || FORCE_MILITARY_MARKET.contains(market.getId())
                && market.hasCondition(Conditions.HEADQUARTERS))
            {
                market.removeSubmarket(Submarkets.GENERIC_MILITARY);
                market.addSubmarket("AL_militaryMarket");
                market.addSubmarket("AL_plugofbarrack");
            }
        }
        else if (!newOwnerId.equals("approlight") && oldOwnerId.equals("approlight"))
        {
            if (market.hasCondition(Conditions.MILITARY_BASE) || market.hasCondition("tem_avalon") || FORCE_MILITARY_MARKET.contains(market.getId()))
            {
                if (!newOwnerId.equals("templars"))
                    market.addSubmarket(Submarkets.GENERIC_MILITARY);
            }
            market.removeSubmarket("AL_militaryMarket");
            market.removeSubmarket("AL_plugofbarrack");
        }
        
        ExerelinFactionConfig newOwnerConfig = ExerelinConfig.getExerelinFactionConfig(newOwnerId);
        if (!sectorManager.corvusMode && newOwnerConfig != null)
        {
            if (newOwnerConfig.freeMarket)
            {
                if (!market.hasCondition(Conditions.FREE_PORT)) market.addCondition(Conditions.FREE_PORT);
                market.getTariff().modifyMult("isFreeMarket", ExerelinConfig.freeMarketTariffMult);
            }
            else 
            {
                market.removeCondition(Conditions.FREE_PORT);
                market.getTariff().unmodify("isFreeMarket");
            }
        }
        
        List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
        
        for (SubmarketAPI submarket : submarkets)
        {
            //if (submarket.getFaction() != oldOwner) continue;
            String submarketId = submarket.getSpecId();
            if (!ALWAYS_CAPTURE_SUBMARKET.contains(submarketId))
            {
                if (submarket.getPlugin().isFreeTransfer()) continue;
                if (!submarket.getPlugin().isParticipatesInEconomy()) continue;
            }
            // this doesn't behave as expected for pirate markets (it checks if submarket faction is hostile to market faction)
            //if (submarket.getPlugin().isBlackMarket()) continue;	
            
            // reset smuggling suspicion
            if (submarketId.equals(Submarkets.SUBMARKET_BLACK)) {  
              PlayerTradeDataForSubmarket tradeData = SharedData.getData().getPlayerActivityTracker().getPlayerTradeData(submarket);  
              tradeData.setTotalPlayerTradeValue(0);
              continue;
            }  
            if (submarketId.equals("ssp_cabalmarket")) continue;
            if (submarketId.equals("uw_cabalmarket")) continue;
            
            submarket.setFaction(newOwner);
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
                //else flipRelay = market.hasCondition("regional_capital") || market.hasCondition("headquarters");
            }
            
            if (flipRelay)
            {
                StarSystemAPI loc = market.getStarSystem();
                if (loc != null)
                {
                    // safety
                    if (sectorManager.systemToRelayMap == null)
                    {
                        setSystemToRelayMap(new HashMap<String, String>());
                    }
                    String relayId = sectorManager.systemToRelayMap.get(loc.getId());
                    if (relayId != null)
                    {
                        SectorEntityToken relay = Global.getSector().getEntityById(relayId);
                        relay.setFaction(newOwnerId);
                    }
                    else 
                    {
                        List<SectorEntityToken> relays = loc.getEntitiesWithTag(Tags.COMM_RELAY);
                        //log.info("#entities: " + relays.size());
                        if (!relays.isEmpty() && relays.get(0).getMarket() == null) relays.get(0).setFaction(newOwnerId);
                    }
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
    
    public static void setHomeworld(SectorEntityToken entity)
    {
        if (sectorManager == null) return;
        sectorManager.homeworld = entity;
    }
    
    public static SectorEntityToken getHomeworld()
    {
        if (sectorManager == null) return null;
        return sectorManager.homeworld;
    }
    
    public static void setFreeStart(boolean freeStart)
    {
        if (sectorManager == null) return;
        sectorManager.freeStart = freeStart;
    }
    
    public static boolean getFreeStart()
    {
        if (sectorManager == null) return false;
        return sectorManager.freeStart;
    }
    
    protected static void expelPlayerFromFaction()
    {
        String oldFactionId = PlayerFactionStore.getPlayerFactionId();
        if (oldFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;

        SectorAPI sector = Global.getSector();
        FactionAPI newFaction = sector.getFaction(ExerelinConstants.PLAYER_NPC_ID);
        FactionAPI oldFaction = sector.getFaction(oldFactionId);

        if (!ExerelinUtilsFaction.isPirateFaction(oldFactionId))
            PlayerFactionStore.loadIndependentPlayerRelations(true);
        PlayerFactionStore.setPlayerFactionId(ExerelinConstants.PLAYER_NPC_ID);
        ExerelinUtilsReputation.syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);

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
        List<String> temp = ExerelinSetupData.getInstance().getAllFactions();
        sectorManager.liveFactionIds = new ArrayList<>();
        sectorManager.factionIdsAtStart = new ArrayList<>();
        sectorManager.historicFactionIds = new HashSet<>();
        
        for (String factionId:temp)
        {
            if (ExerelinUtilsFaction.getFactionMarkets(factionId).size() > 0)
            {
                ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
                if (config != null && !config.playableFaction)
                    continue;
                sectorManager.liveFactionIds.add(factionId);
                sectorManager.factionIdsAtStart.add(factionId);
                sectorManager.historicFactionIds.add(factionId);
            }
            else	// no need for showIntelEvenIfDead check, that's done in setShowFactionInIntelTab()
            {
                setShowFactionInIntelTab(factionId, false);
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
    
    public static void setAllowRespawnFactions(boolean respawn, boolean allowNew)
    {
        if (sectorManager == null) return;
        sectorManager.respawnFactions = respawn;
        sectorManager.onlyRespawnStartingFactions = !allowNew;
    }
    
    public enum VictoryType
    {
        CONQUEST,
        CONQUEST_ALLY,
        DIPLOMATIC,
        DIPLOMATIC_ALLY,
        DEFEAT_CONQUEST,  //not a victory type but who's counting?
        DEFEAT_DIPLOMATIC,
		RETIRED
    }
}
