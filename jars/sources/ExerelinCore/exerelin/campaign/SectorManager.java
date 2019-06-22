package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
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
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeDataForSubmarket;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.SlavesSoldEvent;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.NexUtilsReputation;
import exerelin.campaign.intel.FactionInsuranceIntel;
import exerelin.campaign.intel.FactionSpawnedOrEliminatedIntel;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.RespawnBaseIntel;
import exerelin.campaign.intel.VictoryIntel;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
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
 * General sector handler
 */
public class SectorManager extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(SectorManager.class);
    protected static SectorManager sectorManager;

    protected static final String MANAGER_MAP_KEY = "exerelin_sectorManager";
    public static final String MEMORY_KEY_RECENTLY_CAPTURED = "$nex_recentlyCapturedFrom";
    public static final float MEMORY_KEY_RECENTLY_CAPTURED_EXPIRE = 90;
    public static final List<String> POSTS_TO_CHANGE_ON_CAPTURE = Arrays.asList(new String[]{
        Ranks.POST_BASE_COMMANDER,
        Ranks.POST_OUTPOST_COMMANDER,
        Ranks.POST_STATION_COMMANDER,
        Ranks.POST_PORTMASTER,
        Ranks.POST_SUPPLY_OFFICER,
        Ranks.POST_ADMINISTRATOR,
        //Ranks.FACTION_LEADER	// no maek sense
    });
    
    public static final Set<String> NO_BLACK_MARKET = new HashSet(Arrays.asList(new String[]{
        "SCY_overwatchStation",
        //"SCY_hephaistosStation",
        "uw_arigato"
    }));
    public static final Set<String> FORCE_MILITARY_MARKET = new HashSet(Arrays.asList(new String[]{
        "SCY_hephaistosStation",
    }));
    public static final Set<String> ALWAYS_CAPTURE_SUBMARKET = new HashSet(Arrays.asList(new String[]{
        "tiandong_retrofit",
    }));
    
    public static final Set<String> NO_WARMONGER_FACTIONS = new HashSet(Arrays.asList(new String[]{
        Factions.DERELICT, Factions.REMNANTS, Factions.NEUTRAL
    }));
    
    protected List<String> factionIdsAtStart = new ArrayList<>();
    protected Set<String> liveFactionIds = new HashSet<>();
    protected Set<String> historicFactionIds = new HashSet<>();
    protected Map<String, Integer> factionRespawnCounts = new HashMap<>();
    protected Map<FleetMemberAPI, Float[]> insuranceLostMembers = new HashMap<>();    // value is base buy value and number of D mods
    
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
    
    protected IntervalUtil liveFactionCheckUtil = new IntervalUtil(0.5f,0.5f);

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
        
        if (respawnFactions){
            ExerelinUtils.advanceIntervalDays(respawnIntervalUtil, amount);
            if (respawnIntervalUtil.intervalElapsed()) {
                handleFactionRespawn();
                
                respawnInterval = ExerelinConfig.factionRespawnInterval;
                respawnIntervalUtil.setInterval(respawnInterval * 0.75F, respawnInterval * 1.25F);
            }
        }
        
        ExerelinUtils.advanceIntervalDays(liveFactionCheckUtil, amount);
        if (liveFactionCheckUtil.intervalElapsed())
        {
            recheckLiveFactions();
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
        float crewRaw = 0;
        int prisoners = 0;
        float contrib = plugin.computePlayerContribFraction();
        List<FleetMemberData> casualties = plugin.getLoserData().getOwnCasualties();
        for (FleetMemberData member : casualties) {
            Status status = member.getStatus();
            if (status == Status.DESTROYED || status == Status.NORMAL) continue;
            fp += member.getMember().getFleetPointCost();
            crewRaw += member.getMember().getMinCrew();
            //log.info("Enemy lost: " + member.getMember().getVariant().getFullDesignationWithHullName());
            
            // officers as prisoners
            PersonAPI captain = member.getMember().getCaptain();
            if (captain != null && !captain.isDefault())
            {
                float survivalChance = 1f - (0.5f * member.getMember().getStats().getCrewLossMult().modified);
                float captureChance = 0.15f + (0.1f * captain.getStats().getLevel() / 20);    // FIXME magic number
                if (Math.random() < survivalChance * captureChance)
                    prisoners++;
            }
        }
        
        // old random prisoner drops
        for (int i=0; i<fp; i += 10)
        {
            if (Math.random() < ExerelinConfig.prisonerLootChancePer10Fp)
            {
                prisoners++;
            }
        }
        
        prisoners = (int)(prisoners * contrib + 0.5f);
        loot.addCommodity("prisoner", prisoners);
        numSurvivors += prisoners;
        
        if (ExerelinConfig.crewLootMult > 0) {
            crewRaw = crewRaw*MathUtils.getRandomNumberInRange(0.5f, 1.5f) * ExerelinConfig.crewLootMult;
            crewRaw += MathUtils.getRandomNumberInRange(-3, 3);
            crewRaw = crewRaw * contrib;
            int crew = (int)(crewRaw);
            if (crewRaw > 0) {
                loot.addCrew(crew);
                numSurvivors += crew;
            }
        }
        
        StatsTracker.getStatsTracker().modifyOrphansMadeByCrewCount(-numSurvivors, loserFactionId);
    }
    
    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) return;
        FactionInsuranceIntel insuranceIntel = new FactionInsuranceIntel(insuranceLostMembers, null);
		insuranceLostMembers.clear();
    }
    
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        checkForWarmongerEvent(result);
        checkForInsurance(result);
    }
    
    protected void checkForWarmongerEvent(EngagementResultAPI result)
    {
        boolean playerWin = result.didPlayerWin();
        EngagementResultForFleetAPI fleetResult = result.getWinnerResult();
        if (playerWin) fleetResult = result.getLoserResult();
        FactionAPI faction = fleetResult.getFleet().getFaction();
        
        // relationship is _before_ the reputation penalty caused by the combat
        if (faction.isHostileTo("player")) return;
        if (fleetResult.getDisabled().isEmpty() && fleetResult.getDestroyed().isEmpty()) return;
        CampaignFleetAPI fleet = fleetResult.getFleet();
        if (fleet.getMemoryWithoutUpdate().getBoolean("$exerelinFleetAggressAgainstPlayer")) return;
        // can't, it's e.g. used by customs inspectors even before you agree to the scan
        //if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE)) return;
        if (fleet.getMemoryWithoutUpdate().getBoolean("$Cabal_extortionAskedFor")) return;
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_LOW_REP_IMPACT)) return;
        if (!result.getBattle().isPlayerPrimary()) return;
        if (!fleet.knowsWhoPlayerIs()) return;
        
        createWarmongerEvent(faction.getId(), fleet);
    }
    
    protected void checkForInsurance(EngagementResultAPI result)
    {
        EngagementResultForFleetAPI er = result.didPlayerWin() ? result.getWinnerResult() : result.getLoserResult();
        List<FleetMemberAPI> disabledOrDestroyed = new ArrayList<>();
        disabledOrDestroyed.addAll(er.getDisabled());
        disabledOrDestroyed.addAll(er.getDestroyed());
        
        for (FleetMemberAPI member : disabledOrDestroyed)
        {
            if (insuranceLostMembers.containsKey(member))
                continue;    // though this shouldn't happen anyway
            if (member.isAlly())
                continue;
            if (member.isFighterWing())
                continue;
            //log.info("Member " + member.getShipName() + " disabled or destroyed");
            insuranceLostMembers.put(member, new Float[]{member.getBaseBuyValue(), (float)FactionInsuranceIntel.countDMods(member)});
        }
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
    
    public static SectorManager getManager()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        return (SectorManager)data.get(MANAGER_MAP_KEY);
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
    
    @Deprecated
    public static void createWarmongerEvent(String targetFactionId, SectorEntityToken location)
    {
        if (ExerelinConfig.warmongerPenalty == 0) return;
        if (NO_WARMONGER_FACTIONS.contains(targetFactionId)) return;
        
        FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (targetFaction.isHostileTo(Factions.PLAYER)) return;
        if (targetFactionId.equals(playerAlignedFactionId) || targetFactionId.equals(Factions.PLAYER)) return;
        
        int numFactions = 0;
        float totalRepLoss = 0;    // note: does not include the loss with player-aligned faction
        float myFactionLoss = 0;
        Map<String, Float> repLoss = new HashMap<>();
        List<String> factions = SectorManager.getLiveFactionIdsCopy();
        for (String factionId : factions)
        {
            if (factionId.equals(targetFactionId)) continue;
            //if (factionId.equals(Factions.PLAYER)) continue;
            if (targetFaction.isHostileTo(factionId)) continue;
            if (Nex_IsFactionRuler.isRuler(factionId)  && ExerelinConfig.warmongerPenalty <= 1) 
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
                if (!Nex_IsFactionRuler.isRuler(factionId)) myFactionLoss = (2*loss) + 0.05f;
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
        params.put("avgRepLoss", totalRepLoss/numFactions);
        params.put("numFactions", numFactions);
        params.put("repLoss", repLoss);
        params.put("myFactionLoss", myFactionLoss);
        params.put("targetFaction", targetFactionId);
        
        // TODO
        //WarmongerEvent event = WarmongerEvent.getOngoingEvent();
        //if (event != null) event.reportEvent(location, params);
    }
    
    public void handleSlaveTradeRep()
    {
        if (ExerelinConfig.prisonerSlaveRepValue > 0) return;
        
        LocationAPI loc = marketLastSoldSlaves.getPrimaryEntity().getContainingLocation();
        List<MarketAPI> markets = Misc.getMarketsInLocation(loc);
        List<String> factionsToNotify = new ArrayList<>();  
        Set<String> seenFactions = new HashSet<>();
        Map<String, Float> repPenalties = new HashMap<>();
        float sumRepDelta = 0;

        for (final MarketAPI market : markets) {
            FactionAPI faction = market.getFaction();
            String factionId = market.getFactionId();
            if (seenFactions.contains(factionId)) continue;
            
            seenFactions.add(factionId);
            
            float delta = SlavesSoldEvent.getSlaveRepPenalty(factionId, numSlavesRecentlySold);
            if (delta >= 0) continue;
            
            factionsToNotify.add(factionId);
            repPenalties.put(factionId, delta);
            sumRepDelta += delta;
        }
        if (factionsToNotify.isEmpty()) return;
        //log.info("Selling " + numSlavesRecentlySold + " slaves; rep penalty for each is " + ExerelinConfig.prisonerSlaveRepValue);
        
        Map<String, Object> params = new HashMap<>();

        params.put("factionsToNotify", factionsToNotify);
        params.put("numSlaves", numSlavesRecentlySold);
        params.put("repPenalties", repPenalties);
        params.put("avgRepChange", sumRepDelta/factionsToNotify.size());
        SlavesSoldEvent event = (SlavesSoldEvent)Global.getSector().getEventManager().getOngoingEvent(null, "exerelin_slaves_sold");
        event.reportSlaveTrade(marketLastSoldSlaves, params);
    }
    
    // regularly refresh live factions
    // since we don't have listeners for decivilization etc.
    public void recheckLiveFactions()
    {
        Set<String> newLive = new HashSet<>();
        Set<String> oldLive = new HashSet<>(liveFactionIds);
        Map<String, MarketAPI> factionMarkets = new HashMap<>();    // stores one market for each faction, for intel
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            String factionId = market.getFactionId();
            if (newLive.contains(factionId))
                continue;
            if (!ExerelinUtilsMarket.canBeInvaded(market, false))
                continue;
            newLive.add(factionId);
            factionMarkets.put(factionId, market);
        }
        for (String factionId : oldLive)
        {
            if (!newLive.contains(factionId))
            {
                factionEliminated(null, Global.getSector().getFaction(factionId), null);
            }
            newLive.remove(factionId);    // no need to check this newLive entry against oldLive in next stage
        }
        for (String factionId : newLive)
        {
            if (oldLive.contains(factionId))
                continue;
            if (!ExerelinConfig.getExerelinFactionConfig(factionId).playableFaction)
                continue;
            factionRespawned(Global.getSector().getFaction(factionId), factionMarkets.get(factionId));
        }
    }
    
    public static RespawnInvasionIntel spawnRespawnFleet(FactionAPI respawnFaction, MarketAPI sourceMarket) {
        return spawnRespawnFleet(respawnFaction, sourceMarket, false);
    }
    
    /**
     * Creates a respawn fleet intel item.
     * If no market is available to spawn from, it generates a hidden base. Will not
     * create the respawn intel in this case, unless {@code proceedAfterSpawningBase} is true.
     * @param respawnFaction
     * @param sourceMarket
     * @param proceedAfterSpawningBase
     * @return
     */
    public static RespawnInvasionIntel spawnRespawnFleet(FactionAPI respawnFaction, 
            MarketAPI sourceMarket, boolean proceedAfterSpawningBase)
    {
        SectorAPI sector = Global.getSector();
        String respawnFactionId = respawnFaction.getId();
        
        WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
        
        if (sourceMarket == null)
        {
            List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(respawnFactionId);
            if (markets.isEmpty()) {
                // create a base to spawn respawn fleets from
                RespawnBaseIntel base = RespawnBaseIntel.generateBase(respawnFactionId);
                if (base != null && proceedAfterSpawningBase) {
                    markets.add(base.getMarket());
                }
                else
                    return null;
            }
            
            for (MarketAPI market : markets) {
                if (!market.hasSpaceport()) continue;
                sourcePicker.add(market, InvasionFleetManager.getMarketWeightForInvasionSource(market));
            }
            sourceMarket = sourcePicker.pick();
        }
        
        if (sourceMarket == null) {
            return null;
        }
        
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        for (MarketAPI market : markets) 
        {
            if (market.getFactionId().equals(respawnFactionId))    // could happen with console-spawned respawn fleets
                continue;
            if (!ExerelinUtilsMarket.shouldTargetForInvasions(market, 4))
                continue;
            
            float weight = market.getSize();
            boolean wasOriginalOwner = ExerelinUtilsMarket.wasOriginalOwner(market, respawnFactionId);
            if (wasOriginalOwner)
                weight *= 10;
            if (!market.getFaction().isHostileTo(respawnFaction))
				//weight *= 0.001f;
				continue;
				
            targetPicker.add(market, weight);
        }
        MarketAPI targetMarket = targetPicker.pick();
        if (targetMarket == null) {
            return null;
        }
        
        //log.info("Respawn fleet created for " + respawnFaction.getDisplayName());
        RespawnInvasionIntel intel = (RespawnInvasionIntel)InvasionFleetManager.getManager().generateInvasionOrRaidFleet(sourceMarket, targetMarket, 
                InvasionFleetManager.EventType.RESPAWN, 1);
		if (intel != null) {
		}
		return intel;
    }
    
    public int getNumRespawns(String factionId) {
        if (!factionRespawnCounts.containsKey(factionId)) {
            int base = -1;
            if (factionIdsAtStart.contains(factionId))
                base = 0;
            factionRespawnCounts.put(factionId, base);
        }
        return factionRespawnCounts.get(factionId);
    }
    
    public void incrementNumRespawns(String factionId) {
        int num = getNumRespawns(factionId);
        factionRespawnCounts.put(factionId, num + 1);
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
            factionIds = ExerelinConfig.getFactions(true, false);
        }
        
        for(String factionId : factionIds)
        {
            if (factionId.equals(Factions.PLAYER)) continue;
            if (factionId.equals(Factions.INDEPENDENT)) continue;
            ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
            if (config != null && !config.playableFaction) continue;
            
            // check if this faction has used up all its respawn chances
            int maxRespawns = ExerelinConfig.maxFactionRespawns;
            if (maxRespawns >= 0)
            {
                // note: zero maxRespawns means new factions can still enter, but factions that got knocked out can't return
                int count = getNumRespawns(factionId);
                if (count >= maxRespawns)
                    continue;
            }
            
            if (!liveFactionIds.contains(factionId)) factionPicker.add(Global.getSector().getFaction(factionId));
        }
        
        FactionAPI respawnFaction = factionPicker.pick();
        if (respawnFaction == null) return;
        
        RespawnInvasionIntel intel = spawnRespawnFleet(respawnFaction, null);
        if (intel != null) {
            incrementNumRespawns(respawnFaction.getId());
        }
    }
    
    public static void setShowFactionInIntelTab(String factionId, boolean show)
    {
        if (factionId.equals(Factions.PLAYER))
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
        String defeatedId = defeated.getId();
        if (defeatedId.equals(Factions.INDEPENDENT))
            return;
        if (!defeatedId.equals(Factions.PLAYER))
            AllianceManager.leaveAlliance(defeated.getId(), true);
        
        removeLiveFactionId(defeated.getId());
        
        FactionAPI playerFaction = PlayerFactionStore.getPlayerFaction();
        
        FactionSpawnedOrEliminatedIntel intel = new FactionSpawnedOrEliminatedIntel(defeatedId, 
            FactionSpawnedOrEliminatedIntel.EventType.ELIMINATED,
            market, false, defeated == playerFaction);
        ExerelinUtils.addExpiringIntel(intel);
        
        //String defeatedId = defeated.getId();
        //DiplomacyManager.resetFactionRelationships(defeatedId);
        
        setShowFactionInIntelTab(defeated.getId(), false);
        
        // player leaves faction on defeat
        if (defeated == playerFaction && ExerelinConfig.leaveEliminatedFaction 
                && !ExerelinUtilsFaction.isExiInCorvus(defeatedId) && !defeatedId.equals(Factions.PLAYER))
        {
            expelPlayerFromFaction(true);
        }
        
        NexUtilsReputation.syncPlayerRelationshipsToFaction();
        checkForVictory();
    }
    
    public static void factionRespawned(FactionAPI faction, MarketAPI market)
    {
        String factionId = faction.getId();
        boolean existedBefore = false;
        if (sectorManager != null)
        {
            existedBefore = sectorManager.historicFactionIds.contains(factionId);
        }
        
        FactionSpawnedOrEliminatedIntel.EventType type = FactionSpawnedOrEliminatedIntel.EventType.SPAWNED;
        if (existedBefore) type = FactionSpawnedOrEliminatedIntel.EventType.RESPAWNED;
        
        FactionSpawnedOrEliminatedIntel intel = new FactionSpawnedOrEliminatedIntel(factionId, 
            type, market, false, false);
        ExerelinUtils.addExpiringIntel(intel);
        
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
                    int pop = ExerelinUtilsFaction.getFactionMarketSizeSum(factionId);
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
    
    /**
     * Removes any existing victory intel item and unsets {@code victoryHasOccured}.
     * @return True if game was in victory state, false otherwise.
     */
    public boolean clearVictory()
    {
        boolean didAnything = victoryHasOccured;
        for (IntelInfoPlugin vic : Global.getSector().getIntelManager().getIntel(VictoryIntel.class)) {
            ((VictoryIntel)vic).endImmediately();
            didAnything = true;
        }
        victoryHasOccured = false;
        
        return didAnything;
    }
    
    public boolean hasVictoryOccured() {
        return victoryHasOccured;
    }
    
    public void setVictoryOccured(boolean bool) {
        victoryHasOccured = bool;
    }
    
    public static void transferMarket(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
            boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength)
    {
        transferMarket(market, newOwner, oldOwner, playerInvolved, isCapture, 
                factionsToNotify, repChangeStrength, false);
    }
    
    /**
     * Called when a market is transferred to another faction
     * @param market
     * @param newOwner
     * @param oldOwner
     * @param playerInvolved Captured by player?
     * @param isCapture False means transfered peacefully (i.e. player_npc transfer market function)
     * @param factionsToNotify Factions to cause reputation gain with on capture
     * @param repChangeStrength
     * @param silent If true, suppresses intel notifications and such
     */
    public static void transferMarket(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
            boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, 
            float repChangeStrength, boolean silent)
    {
        // forcibly refreshes the market before capture so we can loot their faction-specific goodies once we capture it
        // already did this in InvasionRound
        //ExerelinUtilsMarket.forceMarketUpdate(market);
        
        // transfer market and associated entities
        String newOwnerId = newOwner.getId();
        String oldOwnerId = oldOwner.getId();
        Set<SectorEntityToken> linkedEntities = market.getConnectedEntities();
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
        market.setPlayerOwned(newOwnerId.equals(Factions.PLAYER));
        
        // transfer defense station
        if (Misc.getStationFleet(market) != null)
        {
            Misc.getStationFleet(market).setFaction(newOwnerId, true);
        }
        if (Misc.getStationBaseFleet(market) != null)
        {
            Misc.getStationBaseFleet(market).setFaction(newOwnerId, true);
        }
        
        // don't lock player out of freshly captured market
        if (!newOwner.isHostileTo(Factions.PLAYER))
        {
            market.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET);
        }
        
        // player: free storage unlock
        if (Nex_IsFactionRuler.isRuler(newOwnerId))
        {
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage != null)
            {
                StoragePlugin plugin = (StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
                if (plugin != null)
                    plugin.setPlayerPaidToUnlock(true);
            }
        }
        
        updateSubmarkets(market, oldOwnerId, newOwnerId);
        
        // Templar stuff
        if (newOwnerId.equals("templars") && !oldOwnerId.equals("templars"))
        {
            if (!market.hasCondition("exerelin_templar_control")) market.addCondition("exerelin_templar_control");
        }
        else if (!newOwnerId.equals("templars") && oldOwnerId.equals("templars"))
        {
            if (market.hasCondition("exerelin_templar_control")) market.removeCondition("exerelin_templar_control");
        }
        
        // tariffs
        
        // no, this risks screwing market-specific tariffs
        //market.getTariff().modifyFlat("generator", Global.getSector().getFaction(newOwnerId).getTariffFraction());    
        
        // (un)apply free port if needed
        ColonyManager.updateFreePortSetting(market);
        
        ExerelinUtilsMarket.setTariffs(market);
        
        // set submarket factions
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
        market.reapplyIndustries();
        
        // prompt player to name faction if needed
        if (newOwnerId.equals(Factions.PLAYER) && !Misc.isPlayerFactionSetUp()) {
            //Global.getSector().getCampaignUI().showPlayerFactionConfigDialog();
            Global.getSector().addTransientScript(new PlayerFactionSetupNag());
        }
        
        // intel report
        if (!silent) {
            MarketTransferIntel intel = new MarketTransferIntel(market, oldOwnerId, newOwnerId, isCapture, playerInvolved, 
                factionsToNotify, repChangeStrength);
            ExerelinUtils.addExpiringIntel(intel);
        }
        
        DiplomacyManager.notifyMarketCaptured(market, oldOwner, newOwner);
        if (playerInvolved) StatsTracker.getStatsTracker().notifyMarketCaptured(market);
        
        int marketsRemaining = ExerelinUtilsFaction.getFactionMarkets(oldOwner.getId(), true).size();
        log.info("Faction " + oldOwner.getDisplayName() + " has " + marketsRemaining + " markets left");
        if (marketsRemaining == 0)
        {
            factionEliminated(newOwner, oldOwner, market);
        }
        
        // faction respawn event if needed
        marketsRemaining = ExerelinUtilsFaction.getFactionMarkets(newOwner.getId(), true).size();
        if (marketsRemaining == 1)
        {
            factionRespawned(newOwner, market);
        }
        
        // rebellion
        RebellionEvent rebEvent = RebellionEvent.getOngoingEvent(market);
        if (rebEvent != null) rebEvent.marketCaptured(newOwnerId, oldOwnerId);
                
        // revengeance fleet
        if (isCapture && newOwnerId.equals(PlayerFactionStore.getPlayerFactionId()) || newOwnerId.equals(Factions.PLAYER))
        {
            RevengeanceManager rvngEvent = RevengeanceManager.getManager();
            if (rvngEvent!= null) 
            {
                float sizeSq = market.getSize() * market.getSize();
                rvngEvent.addPoints(sizeSq * ExerelinConfig.revengePointsForMarketCaptureMult);
            }
        }
        
        if (newOwner.isPlayerFaction() && isCapture) {
            market.getMemoryWithoutUpdate().set(MEMORY_KEY_RECENTLY_CAPTURED, 
                    oldOwnerId, MEMORY_KEY_RECENTLY_CAPTURED_EXPIRE);
        }
        
        ExerelinUtilsMarket.reportMarketTransferred(market, newOwner, oldOwner, 
                playerInvolved, isCapture, factionsToNotify, repChangeStrength);
    }
    
    public static void addOrRemoveSubmarket(MarketAPI market, String submarketId, boolean shouldHave)
    {
        if (market.hasSubmarket(submarketId) && !shouldHave)
            market.removeSubmarket(submarketId);
        else if (!market.hasSubmarket(submarketId) && shouldHave)
        {
            market.addSubmarket(submarketId);
            market.getSubmarket(submarketId).getCargo();    // force cargo to generate if needed; fixes military submarket crash
        }
        
    }
    
    /**
     * Update <code>@market</code>'s submarkets on market capture/transfer.
     * @param market
     * @param oldOwnerId
     * @param newOwnerId
     */
    public static void updateSubmarkets(MarketAPI market, String oldOwnerId, String newOwnerId)
    {
        boolean haveLocalResources = newOwnerId.equals(Factions.PLAYER);
        boolean haveOpen = false;
        boolean haveMilitary = false;
        boolean haveBlackMarket = false;
        boolean haveTemplar = newOwnerId.equals("templars");
        
        if (!newOwnerId.equals("templars") && !newOwnerId.equals(Factions.PLAYER))
        {
            if (market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND) 
                    || market.hasCondition("tem_avalon") || FORCE_MILITARY_MARKET.contains(market.getId()))
                haveMilitary = true;
            if (!NO_BLACK_MARKET.contains(market.getId()))
                haveBlackMarket = true;
        }        
        
        if (!newOwnerId.equals("templars") && (!newOwnerId.equals(Factions.PLAYER) || market.hasIndustry("commerce")))
            haveOpen = true;
        
        addOrRemoveSubmarket(market, Submarkets.LOCAL_RESOURCES, haveLocalResources);
        addOrRemoveSubmarket(market, Submarkets.SUBMARKET_OPEN, haveOpen);
        addOrRemoveSubmarket(market, Submarkets.SUBMARKET_BLACK, haveBlackMarket);
        addOrRemoveSubmarket(market, "tem_templarmarket", haveTemplar);
        
        // handle ApproLight special market
        if (newOwnerId.equals("approlight") && !oldOwnerId.equals("approlight"))
        {
            if (haveMilitary)
            {
                market.removeSubmarket(Submarkets.GENERIC_MILITARY);
                addOrRemoveSubmarket(market, "AL_militaryMarket", true);
            }
        }
        else if (!newOwnerId.equals("approlight") && oldOwnerId.equals("approlight"))
        {
            if (haveMilitary)
            {
                if (!newOwnerId.equals("templars"))
                    addOrRemoveSubmarket(market, Submarkets.GENERIC_MILITARY, true);
            }
            market.removeSubmarket("AL_militaryMarket");
        }
        else
            addOrRemoveSubmarket(market, Submarkets.GENERIC_MILITARY, haveMilitary);
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
    
	@Deprecated
    protected static void expelPlayerFromFaction(boolean silent)
    {
        String oldFactionId = PlayerFactionStore.getPlayerFactionId();
        if (oldFactionId.equals(Factions.PLAYER)) return;

        SectorAPI sector = Global.getSector();
        FactionAPI newFaction = sector.getFaction(Factions.PLAYER);
        FactionAPI oldFaction = sector.getFaction(oldFactionId);

        if (!ExerelinUtilsFaction.isPirateFaction(oldFactionId))
            PlayerFactionStore.loadIndependentPlayerRelations(true);
        PlayerFactionStore.setPlayerFactionId(Factions.PLAYER);
        NexUtilsReputation.syncFactionRelationshipsToPlayer(Factions.PLAYER);
        
        if (!silent)
        {
			/*
            CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, "exerelin_faction_changed");
            if (eventSuper == null) 
                eventSuper = sector.getEventManager().startEvent(null, "exerelin_faction_changed", null);
            FactionChangedEvent event = (FactionChangedEvent)eventSuper;
            
            MarketAPI market = ExerelinUtils.getClosestMarket(oldFactionId);
            event.changeIntel(oldFaction, newFaction, "expelled", market.getPrimaryEntity());
			*/
        }
    }
        
    public static void reinitLiveFactions()
    {
        if (sectorManager == null) return;
        List<String> temp = ExerelinConfig.getFactions(false, false);
        sectorManager.liveFactionIds = new HashSet<>();
        sectorManager.factionIdsAtStart = new ArrayList<>();
        sectorManager.historicFactionIds = new HashSet<>();
        
        for (String factionId : temp)
        {
            if (!ExerelinUtilsFaction.getFactionMarkets(factionId, true).isEmpty())
            {
                ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
                if (config != null && !config.playableFaction)
                    continue;
                sectorManager.liveFactionIds.add(factionId);
                sectorManager.factionIdsAtStart.add(factionId);
                sectorManager.historicFactionIds.add(factionId);
                setShowFactionInIntelTab(factionId, true);
            }
            else    // no need for showIntelEvenIfDead check, that's done in setShowFactionInIntelTab()
            {
                setShowFactionInIntelTab(factionId, false);
            }
        }
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
        RETIRED;
        
        public boolean isConquest()
        {
            return this == CONQUEST || this == CONQUEST_ALLY || this == DEFEAT_CONQUEST;
        }
        public boolean isDiplomatic()
        {
            return this == DIPLOMATIC || this == DIPLOMATIC_ALLY || this == DEFEAT_DIPLOMATIC;
        }
        public boolean isDefeat()
        {
            return this == DEFEAT_CONQUEST || this == DEFEAT_DIPLOMATIC;
        }
    }
}
