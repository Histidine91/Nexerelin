package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.Status;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeDataForSubmarket;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.VayraModPlugin;
import exerelin.ExerelinConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.battle.EncounterLootHandler;
import exerelin.campaign.econ.RaidCondition;
import exerelin.campaign.events.NexRepTrackerEvent;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.*;
import exerelin.campaign.intel.VictoryScoreboardIntel.ScoreEntry;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
import exerelin.campaign.intel.raid.RemnantRaidFleetInteractionConfigGen;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.campaign.ui.PlayerFactionSetupNag;
import exerelin.campaign.ui.VictoryScreenScript;
import exerelin.campaign.ui.VictoryScreenScript.CustomVictoryParams;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

import static exerelin.campaign.intel.VictoryScoreboardIntel.getNeededSizeForVictory;

/**
 * General sector handler
 */
public class SectorManager extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(SectorManager.class);

    protected static final String MANAGER_MAP_KEY = "exerelin_sectorManager";
    public static final String MEMORY_KEY_RECENTLY_CAPTURED = "$nex_recentlyCapturedFrom";
    public static final String MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER = "$nex_recentlyCapturedByPlayer";
    public static final float MEMORY_KEY_RECENTLY_CAPTURED_EXPIRE = 90;
    public static final String MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT = "$nex_captureStabilizeTimeout";
    
    //public static final float SIZE_FRACTION_FOR_VICTORY = 0.501f;
    //public static final float HI_FRACTION_FOR_VICTORY = 0.67f;
    
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
        "uw_arigato",
        "tahlan_lethia_p05_market"
    }));
    public static final Set<String> FORCE_MILITARY_MARKET = new HashSet(Arrays.asList(new String[]{
        "SCY_hephaistosStation",
    }));
    public static final Set<String> NO_MILITARY_MARKET = new HashSet(Arrays.asList(new String[]{
        "uw_arigato"
    }));
    public static final Set<String> NEVER_CAPTURE_SUBMARKET = new HashSet(Arrays.asList(new String[]{
        "roider_unionMarket"
    }));
    public static final Set<String> ALWAYS_CAPTURE_SUBMARKET = new HashSet(Arrays.asList(new String[]{
        "tiandong_retrofit", "ii_ebay"
    }));
    
    public static final Set<String> NO_WARMONGER_FACTIONS = new HashSet(Arrays.asList(new String[]{
        Factions.DERELICT, Factions.REMNANTS, Factions.NEUTRAL, "nex_derelict"
    }));
    
    public static final Set<String> DO_NOT_RESPAWN_FACTIONS = new HashSet<>();
    
    protected List<String> factionIdsAtStart = new ArrayList<>();
    protected Set<String> liveFactionIds = new HashSet<>();
    protected Set<String> presentFactionIds = new HashSet<>();    // like live factions but includes non-playable factions
    protected Set<String> historicFactionIds = new HashSet<>();    // factions that have ever been present in the Sector
    protected Map<String, Integer> factionRespawnCounts = new HashMap<>();
    protected transient Map<FleetMemberAPI, Integer[]> insuranceLostMembers = new HashMap<>();    // array contains base value, number of D-mods, and CR
    protected transient List<OfficerDataAPI> insuranceLostOfficers = new ArrayList<>();
    protected InsuranceIntelV2 insurance = new InsuranceIntelV2();
    
	protected VictoryScoreboardIntel scoreboard;
    protected boolean victoryHasOccured = false;
	
    protected boolean respawnFactions = false;
    protected boolean onlyRespawnStartingFactions = false;
    protected SectorEntityToken homeworld;
    
    protected boolean corvusMode = true;
    protected boolean hardMode = false;
    protected boolean freeStart = false;
    
    protected int numSlavesRecentlySold = 0;
    protected MarketAPI marketLastSoldSlaves = null;
    
    protected float respawnInterval = 60f;
    protected final IntervalUtil respawnIntervalUtil;
    
    protected IntervalUtil liveFactionCheckUtil = new IntervalUtil(0.5f,0.5f);
	
	static {
		// Disable respawning of Vayra's factions if they're disabled in their config
		if (Global.getSettings().getModManager().isModEnabled("vayrasector")) {
			if (!VayraModPlugin.POPULAR_FRONT_ENABLED) {
				DO_NOT_RESPAWN_FACTIONS.add("communist_clouds");
			}
			if (!VayraModPlugin.COLONIAL_FACTIONS_ENABLED) {
				DO_NOT_RESPAWN_FACTIONS.add("almighty_dollar");
				DO_NOT_RESPAWN_FACTIONS.add("ashen_keepers");
				DO_NOT_RESPAWN_FACTIONS.add("science_fuckers");
				DO_NOT_RESPAWN_FACTIONS.add("warhawk_republic");
			}
		}
	}
    
    protected Object readResolve() {
        insuranceLostMembers = new HashMap<>();
        insuranceLostOfficers = new ArrayList<>();
        
        if (presentFactionIds == null) {
            presentFactionIds = new HashSet<>(liveFactionIds);
        }
        
        return this;
    }
    
    public InsuranceIntelV2 getInsurance() {
        return insurance;
    }

    public SectorManager()
    {
        super(true);
        //SectorManager.reinitLiveFactions();
        respawnFactions = ExerelinSetupData.getInstance().respawnFactions;
        onlyRespawnStartingFactions = ExerelinSetupData.getInstance().onlyRespawnStartingFactions;
        respawnInterval = NexConfig.factionRespawnInterval;
        respawnIntervalUtil = new IntervalUtil(respawnInterval * 0.75F, respawnInterval * 1.25F);
        
        scoreboard = new VictoryScoreboardIntel();
		Global.getSector().getIntelManager().addIntel(scoreboard);
    }
	
	public void reverseCompatibility() {
		if (scoreboard == null) {
			scoreboard = new VictoryScoreboardIntel();
			Global.getSector().getIntelManager().addIntel(scoreboard);
		}
	}
   
    @Override
    public void advance(float amount)
    {
        if (TutorialMissionIntel.isTutorialInProgress()) 
            return;
        
        if (respawnFactions){
            NexUtils.advanceIntervalDays(respawnIntervalUtil, amount);
            if (respawnIntervalUtil.intervalElapsed()) {
                handleFactionRespawn();
                
                respawnInterval = NexConfig.factionRespawnInterval;
                respawnIntervalUtil.setInterval(respawnInterval * 0.75F, respawnInterval * 1.25F);
            }
        }
        
        NexUtils.advanceIntervalDays(liveFactionCheckUtil, amount);
        if (liveFactionCheckUtil.intervalElapsed())
        {
            recheckLiveFactions();
        }
    }
    
    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (!ExerelinModPlugin.isNexDev)
            return;
        
        if (fleet.isStationMode()) {
            String str = String.format("Despawning fleet %s in %s: %s", fleet.getNameWithFactionKeepCase(), fleet.getContainingLocation().getNameWithLowercaseType(),
                    reason);
            
            //Global.getSector().getCampaignUI().addMessage(str);
        }
    }
    
    // adds prisoners to loot
    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
        CampaignFleetAPI loser = plugin.getLoser();
        if (loser == null) return;
        String loserFactionId = loser.getFaction().getId();
        NexFactionConfig loserConfig = NexConfig.getFactionConfig(loserFactionId);
        if (!loserConfig.dropPrisoners)
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
            float thisCrew = member.getMember().getMinCrew();
            if (thisCrew <= 0) continue;
            
            fp += member.getMember().getFleetPointCost();
            crewRaw += thisCrew;
            //log.info("Enemy lost: " + member.getMember().getVariant().getFullDesignationWithHullName());
            
            // officers as prisoners
            PersonAPI captain = member.getMember().getCaptain();
            if (captain != null && !captain.isDefault() && !captain.isAICore())
            {
                float survivalChance = 1f - (0.5f * member.getMember().getStats().getCrewLossMult().modified);
                float captureChance = 0.15f + (0.1f * captain.getStats().getLevel() / Global.getSettings().getLevelupPlugin().getMaxLevel());
                if (Math.random() < survivalChance * captureChance)
                    prisoners++;
            }
        }
        
        // none of the ships had crew, may as well quit here
        if (crewRaw <= 0) return;
        
        // old random prisoner drops
        for (int i=0; i<fp; i += 10)
        {
            if (Math.random() < NexConfig.prisonerLootChancePer10Fp)
            {
                prisoners++;
            }
        }
        
        prisoners = (int)(prisoners * contrib + 0.5f);
        loot.addCommodity("prisoner", prisoners);
        numSurvivors += prisoners;
        
        if (NexConfig.crewLootMult > 0) {
            crewRaw = crewRaw*MathUtils.getRandomNumberInRange(0.5f, 1.5f) * NexConfig.crewLootMult;
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
        
        if (NexConfig.legacyInsurance) {
            FactionInsuranceIntel legacyInsurance = new FactionInsuranceIntel(insuranceLostMembers, insuranceLostOfficers);
        }
        else 
            insurance.reportBattle(insuranceLostMembers, insuranceLostOfficers);
        
        insuranceLostMembers.clear();
        insuranceLostOfficers.clear();
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
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_NO_REP_IMPACT)) return;
        if (!result.getBattle().isPlayerPrimary()) return;
        if (!fleet.knowsWhoPlayerIs()) return;
        
        createWarmongerEvent(faction.getId(), fleet);
    }
    
    // Check losses every engagement round
    // If we just compared against the snapshot once at battle end, we wouldn't know who was recovered
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
            //log.info("Member " + member.getShipName() + " base value " + member.getBaseValue());
            insuranceLostMembers.put(member, new Integer[]{
                (int)member.getBaseValue(), 
                FactionInsuranceIntel.countDMods(member), 
                (int)Math.round(member.getRepairTracker().getBaseCR() * 100)
            });
        }
    }
    
    public void addInsuredOfficers(List<OfficerDataAPI> officers) {
        insuranceLostOfficers.addAll(officers);
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
    
    public boolean isHardMode() {
        return hardMode;
    }
    
    public void setHardMode(boolean mode)
    {
        hardMode = mode;
        ColonyManager.updateIncome();
    }
    
    public boolean isCorvusMode() {
        return corvusMode;
    }
    
    public void setCorvusMode(boolean mode)
    {
        corvusMode = mode;
        Global.getSector().getMemoryWithoutUpdate().set("$nex_corvusMode", mode);
    }
    
    public boolean isFreeStart()
    {
        return freeStart;
    }
    
    public void setFreeStart(boolean freeStart)
    {
        this.freeStart = freeStart;
    }
    
    public boolean isRespawnFactions() {
        return respawnFactions;
    }

    public boolean isOnlyRespawnStartingFactions() {
        return onlyRespawnStartingFactions;
    }
    
    public static SectorManager create()
    {
        SectorManager manager = getManager();
        if (manager != null)
            return manager;
        
        Map<String, Object> data = Global.getSector().getPersistentData();
        manager = new SectorManager();
        data.put(MANAGER_MAP_KEY, manager);
        return manager;
    }
    
    public static SectorManager getManager()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        return (SectorManager)data.get(MANAGER_MAP_KEY);
    }
    
    /**
     * Use {@code SectorManager.getManager().isCorvusMode()} instead.
     * @return
     * @deprecated
     */
    @Deprecated
    public static boolean getCorvusMode()
    {
        return getManager().corvusMode;
    }
    
    /**
     * Use {@code SectorManager.getManager().isHardMode()} instead.
     * @return
     * @deprecated
     */
    public static boolean getHardMode()
    {
        return getManager().hardMode;
    }
    
    @Deprecated
    public static void createWarmongerEvent(String targetFactionId, SectorEntityToken location)
    {
        if (NexConfig.warmongerPenalty == 0) return;
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
            if (Nex_IsFactionRuler.isRuler(factionId)  && NexConfig.warmongerPenalty <= 1) 
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
    
    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (fleet.getFaction().getId().equals("nex_derelict")) {
            // TODO: comment this out after hotfix release? don't need encounter loot handler for AI core drops any more
            fleet.getMemoryWithoutUpdate().set(EncounterLootHandler.FLEET_MEMORY_KEY, "derelict");
            fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
                   new RemnantRaidFleetInteractionConfigGen());
        }
    }

    // runcode exerelin.campaign.SectorManager.getManager().recheckLiveFactions()
    // regularly refresh live factions
    // since we don't have listeners for decivilization etc.
    // well now we do, but since markets might up and vanish in other ways, do it anyway
    public void recheckLiveFactions()
    {
        Set<String> newLive = new HashSet<>();
        Set<String> oldLive = new HashSet<>(liveFactionIds);
        presentFactionIds.clear();
        Map<String, MarketAPI> factionMarkets = new HashMap<>();    // stores one market for each faction, for intel
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            String factionId = market.getFactionId();
            if (newLive.contains(factionId))
                continue;
            if (!NexUtilsMarket.canBeInvaded(market, false))
                continue;
            newLive.add(factionId);
            presentFactionIds.add(factionId);
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
            factionRespawned(Global.getSector().getFaction(factionId), factionMarkets.get(factionId));
        }
    }
    
    public static MarketAPI pickRespawnTarget(FactionAPI respawnFaction, List<MarketAPI> markets) 
    {
        String respawnFactionId = respawnFaction.getId();
        WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> targetPickerBackup = new WeightedRandomPicker();
        
        for (MarketAPI market : markets) 
        {
            if (market.getFactionId().equals(respawnFactionId))    // could happen with console-spawned respawn fleets
                continue;
            if (!NexUtilsMarket.shouldTargetForInvasions(market, 4))
                continue;
            
            float weight = market.getSize();
            boolean wasOriginalOwner = NexUtilsMarket.wasOriginalOwner(market, respawnFactionId);
            if (wasOriginalOwner)
                weight *= 10;
            
            // prefer to attack hostile factions, but use non-hostile markets as backup
            if (!market.getFaction().isHostileTo(respawnFaction)) 
            {
                if (market.getFaction().isAtWorst(respawnFaction, RepLevel.WELCOMING))
                    continue;
                targetPickerBackup.add(market, weight);
            }
            else
                targetPicker.add(market, weight);
        }
        
        if (!targetPicker.isEmpty())
            return targetPicker.pick();
        else 
            return targetPickerBackup.pick();
    }
    
    public static RespawnInvasionIntel spawnRespawnFleet(FactionAPI respawnFaction, 
            MarketAPI sourceMarket, boolean fromConsole) {
        return spawnRespawnFleet(respawnFaction, sourceMarket, false, fromConsole);
    }
    
    /**
     * Creates a respawn fleet intel item. If no market is available to spawn from, it generates a hidden base.
     * Will not create the respawn intel in this case, unless {@code proceedAfterSpawningBase} is true.
     * @param respawnFaction
     * @param sourceMarket
     * @param proceedAfterSpawningBase
     * @param fromConsole If true, prints logging messages to console
     * @return
     */
    public static RespawnInvasionIntel spawnRespawnFleet(FactionAPI respawnFaction, 
            MarketAPI sourceMarket, boolean proceedAfterSpawningBase, boolean fromConsole)
    {
        SectorAPI sector = Global.getSector();
        String respawnFactionId = respawnFaction.getId();
        
        WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
        
        if (sourceMarket == null)
        {
            List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(respawnFactionId);
            if (markets.isEmpty()) {
                // create a base to spawn respawn fleets from
                RespawnBaseIntel base = RespawnBaseIntel.generateBase(respawnFactionId);
                if (base != null && proceedAfterSpawningBase) {
                    if (fromConsole)
                        Console.showMessage("Generating respawn base");
                    markets.add(base.getMarket());
                }
                else
                    return null;
            }
            
            for (MarketAPI market : markets) {
                if (!NexUtilsMarket.hasWorkingSpaceport(market)) continue;
                sourcePicker.add(market, InvasionFleetManager.getMarketWeightForInvasionSource(market));
            }
            sourceMarket = sourcePicker.pick();
        }
        
        if (sourceMarket == null) {
            if (fromConsole)
                Console.showMessage("No source market to launch respawn event");
            else
                log.info("No source market to launch respawn event");
            return null;
        }
        
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        
        MarketAPI targetMarket = pickRespawnTarget(respawnFaction, markets);
        if (targetMarket == null) {
            if (fromConsole)
                Console.showMessage("No target for respawn event");
            else
                log.info("No target for respawn event");
            return null;
        }
        
        // declare war if respawn target is non-hostile
        if (!targetMarket.getFaction().isHostileTo(respawnFaction)) {
            DiplomacyManager.createDiplomacyEvent(respawnFaction, targetMarket.getFaction(), 
                    "declare_war", null);
        }
        
        //log.info("Respawn fleet created for " + respawnFaction.getDisplayName());
        RespawnInvasionIntel intel = (RespawnInvasionIntel)InvasionFleetManager.getManager().generateInvasionOrRaidFleet(sourceMarket, targetMarket, 
                InvasionFleetManager.EventType.RESPAWN, 1, null);
        if (intel != null) {
            respawnFaction.getMemoryWithoutUpdate().set("$nex_respawn_cooldown", true, 
                    Global.getSettings().getFloat("nex_faction_respawn_cooldown"));
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
        if (!NexConfig.enableHostileFleetEvents) return;
        if (!NexConfig.enableInvasions) return;
        
        if (factionRespawnCounts == null)
        {
            factionRespawnCounts = new HashMap<>();
        }
        
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        
        List<String> factionIds = factionIdsAtStart;
        if (!onlyRespawnStartingFactions)
        {
            factionIds = NexConfig.getFactions(true, false);
        }
        
        for(String factionId : factionIds)
        {
            if (factionId.equals(Factions.PLAYER)) continue;
            if (factionId.equals(Factions.INDEPENDENT)) continue;
            if (DO_NOT_RESPAWN_FACTIONS.contains(factionId)) continue;
            
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction.getMemoryWithoutUpdate().contains("$nex_respawn_cooldown")) continue;
            
            NexFactionConfig config = NexConfig.getFactionConfig(factionId);
            if (config != null && !config.playableFaction) continue;
            
            // check if this faction has used up all its respawn chances
            int maxRespawns = NexConfig.maxFactionRespawns;
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
        
        RespawnInvasionIntel intel = spawnRespawnFleet(respawnFaction, null, false);
        if (intel != null) {
            incrementNumRespawns(respawnFaction.getId());
        }
    }
    
    public static void setShowFactionInIntelTab(String factionId, boolean show)
    {
        if (factionId.equals(Factions.PLAYER))
            return;    // do nothing
        
        if (!show && NexUtilsFaction.isExiInCorvus(factionId)) // don't hide Exi in Corvus mode
            return;
        
        NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
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
        NexUtils.addExpiringIntel(intel);
        
        //String defeatedId = defeated.getId();
        //DiplomacyManager.resetFactionRelationships(defeatedId);
        
        setShowFactionInIntelTab(defeated.getId(), false);
        
        // player leaves faction on defeat
        if (defeated == playerFaction && NexConfig.leaveEliminatedFaction 
                && !NexUtilsFaction.isExiInCorvus(defeatedId) && !defeatedId.equals(Factions.PLAYER))
        {
            expelPlayerFromFaction(true);
        }

        StrategicAI.removeAI(defeatedId);

        checkForVictory();
    }
    
    public static void factionRespawned(FactionAPI faction, MarketAPI market)
    {
        if (!NexConfig.getFactionConfig(faction.getId()).playableFaction)
            return;

        SectorManager manager = getManager();
        String factionId = faction.getId();

        if (getLiveFactionIdsCopy().contains(factionId))
            return;

        boolean existedBefore = manager.historicFactionIds.contains(factionId);
        
        FactionSpawnedOrEliminatedIntel.EventType type = FactionSpawnedOrEliminatedIntel.EventType.SPAWNED;
        if (existedBefore) type = FactionSpawnedOrEliminatedIntel.EventType.RESPAWNED;
        
        FactionSpawnedOrEliminatedIntel intel = new FactionSpawnedOrEliminatedIntel(factionId, 
            type, market, false, false);
        NexUtils.addExpiringIntel(intel);
        
        SectorManager.addLiveFactionId(faction.getId());
        manager.historicFactionIds.add(factionId);
        
        setShowFactionInIntelTab(factionId, true);
        
        // increment "times respawned" count
        // no, we already did that on launching the respawn event in the first place, or on market transfer
        //manager.incrementNumRespawns(factionId);

        if (NexConfig.enableStrategicAI) {
            if (factionId.equals(Factions.PLAYER)) return;
            if (NexConfig.getFactionConfig(factionId).pirateFaction) return;
            StrategicAI.addAIIfNeeded(factionId);
        }
    }
    
    public static int getAllianceTotalFromMap(Alliance alliance, Map<String, Integer> map) 
    {
        int result = 0;
        for (String member : alliance.getMembersCopy()) {
            Integer thisAmt = map.get(member);
            if (thisAmt != null) result += thisAmt;
        }
        return result;
    }
    
    public static String getAllianceMemberWithHighestValue(Alliance alliance, Map<String, Integer> map) 
    {
        int best = 0;
        String bestId = null;
        for (String member : alliance.getMembersCopy()) {
            Integer thisAmt = map.get(member);
            if (thisAmt == null) continue;
            if (thisAmt > best) {
                best = thisAmt;
                bestId = member;
            }
        }
        return bestId;
    }
    
	/**
	 * @param factionsToCheck
	 * @return Winning faction ID and victory type
	 */
	public static String[] checkForVictory(Collection<String> factionsToCheck) 
    {
        List<ScoreEntry> sizeRanked = new ArrayList<>();
		List<ScoreEntry> hiRanked = new ArrayList<>();
		List<ScoreEntry> friendsRanked = new ArrayList<>();
		List<String> factions = VictoryScoreboardIntel.getWinnableFactions();
		
		float hiFractionForVictory = Global.getSettings().getFloat("nex_heavyIndustryFractionForVictory");
		
		int[] totals = VictoryScoreboardIntel.generateRankings(factions, sizeRanked, hiRanked, friendsRanked);
		
		int neededPop = getNeededSizeForVictory(totals[0], sizeRanked);
		int neededHI = (int)Math.ceil(totals[1] * hiFractionForVictory);
		int neededFriends = factions.size() - 1;
		
		if (!sizeRanked.isEmpty() && sizeRanked.get(0).score >= neededPop) {
			return new String[] {sizeRanked.get(0).factionId, "conquest"};
		}
		if (!hiRanked.isEmpty() && hiRanked.get(0).score >= neededHI) {
			return new String[] {hiRanked.get(0).factionId, "conquest"};
		}
		if (!friendsRanked.isEmpty() && friendsRanked.get(0).score >= neededFriends) {
			return new String[] {friendsRanked.get(0).factionId, "diplomacy"};
		}
		
		return null;
    }
    
    // runcode exerelin.campaign.SectorManager.checkForVictory()
    public static void checkForVictory()
    {
        if (!NexConfig.enableVictory) return;
        
        SectorManager manager = getManager();
        if (manager.victoryHasOccured) return;
        //FactionAPI faction = Global.getSector().getFaction(factionId);
        SectorAPI sector = Global.getSector();
        
        if (sector.isInNewGameAdvance()) return;
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        String victorFactionId = null;
        VictoryType victoryType = null;
        
        Set<String> liveFactions = new HashSet<>(getLiveFactionIdsCopy());
        
        for (String factionId : getLiveFactionIdsCopy())
        {
            // don't count pirate factions unless config says so or we belong to it
            if (NexUtilsFaction.isPirateFaction(factionId) && !NexConfig.countPiratesForVictory && !factionId.equals(playerAlignedFactionId))
            {
                liveFactions.remove(factionId);
            }
        }
        
        String[] winner = checkForVictory(liveFactions);
		if (winner == null) {
			return;
		}
		victorFactionId = winner[0];
		String type = winner[1];
		if (type.equals("conquest"))
		{
            if (victorFactionId.equals(playerAlignedFactionId))
            {
                victoryType = VictoryType.CONQUEST;
            }
            else if (NexConfig.allyVictories && AllianceManager.areFactionsAllied(victorFactionId, 
                    playerAlignedFactionId))
            {
                victoryType = VictoryType.CONQUEST_ALLY;
            }
            else {

                victoryType = VictoryType.DEFEAT_CONQUEST;
            }
        }
		else if (type.equals("diplomacy")) {
            if (victorFactionId.equals(playerAlignedFactionId)) {
                victoryType = VictoryType.DIPLOMATIC;
            }
            else if (NexConfig.allyVictories && AllianceManager.areFactionsAllied(victorFactionId, 
                        playerAlignedFactionId)) {
                victoryType = VictoryType.DIPLOMATIC_ALLY;
            }
            else victoryType = VictoryType.DEFEAT_DIPLOMATIC;
        }
        
		manager.victoryHasOccured = true;
        Global.getSector().addScript(new VictoryScreenScript(victorFactionId, victoryType));
    }
    
    public void customVictory(CustomVictoryParams params) {
        victoryHasOccured = true;
        VictoryScreenScript script = new VictoryScreenScript(params.factionId, VictoryType.CUSTOM);
        script.setCustomParams(params);
        Global.getSector().addScript(script);
    }
    
    public static void retire() 
    {
        Global.getSector().addScript(new VictoryScreenScript(Factions.PLAYER, VictoryType.RETIRED));
        getManager().victoryHasOccured = true;
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
    
    /**
     * Determine the credits to be lost when player loses a market to an invasion.
     * @param lostMarket
     * @return
     */
    public static final int calcCreditsLost(MarketAPI lostMarket) {
        List<MarketAPI> markets = Misc.getFactionMarkets(Global.getSector().getPlayerFaction());
        int mySize = lostMarket.getSize() * lostMarket.getSize();
        int totalSize = mySize;
        for (MarketAPI market : markets) {
            if (!market.isPlayerOwned()) continue;
            totalSize += market.getSize() * market.getSize();
        }
        if (totalSize == 0) return 0;    // div0 protection
        
        float mult = (float)mySize/(float)totalSize;
        log.info("Invasion credit loss size mult: " + mySize + "/" + totalSize);
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        float lossMult = mult * NexConfig.creditLossOnColonyLossMult;
        if (SectorManager.getManager().isHardMode()) lossMult *= 2;
        if (lossMult > 0.75f) lossMult = 0.75f;
        
        return Math.round(lossMult * credits);
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
        if (factionsToNotify == null) factionsToNotify = (new ArrayList<>());
        
        boolean wasPlayerOwned = market.isPlayerOwned();
        
        // first forcibly update trade data (so it doesn't get attributed to new faction)
        if (NexRepTrackerEvent.getTracker() != null) {
            NexRepTrackerEvent.getTracker().checkForTradeReputationChanges(oldOwner, true);
        }
        
        // transfer market and associated entities
        String newOwnerId = newOwner.getId();
        String oldOwnerId = oldOwner.getId();
        Set<SectorEntityToken> linkedEntities = market.getConnectedEntities();
        if (market.getPlanetEntity() != null) {
            linkedEntities.addAll(NexUtilsAstro.getCapturableEntitiesAroundPlanet(market.getPlanetEntity()));
        }
        
        for (SectorEntityToken entity : linkedEntities)
        {
            entity.setFaction(newOwnerId);
        }
        
        // Use comm board people instead of market people, 
        // because some appear on the former but not the latter 
        // (specifically when a new market admin is assigned, old one disappears from the market)
        // Also, this way it won't mess with player-assigned admins
        for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
        {
            if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
            PersonAPI person = (PersonAPI)dir.getEntryData();
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
        
        // handle local resources stockpiles taken
        if (market.hasSubmarket(Submarkets.LOCAL_RESOURCES)) {
            boolean canCast = market.getSubmarket(Submarkets.LOCAL_RESOURCES).getPlugin() instanceof Nex_LocalResourcesSubmarketPlugin;

            if (canCast) {
                Nex_LocalResourcesSubmarketPlugin plugin = (Nex_LocalResourcesSubmarketPlugin)
                        market.getSubmarket(Submarkets.LOCAL_RESOURCES).getPlugin();
                plugin.billCargo();
            }
        }
        
        updateSubmarkets(market, oldOwnerId, newOwnerId);
        
        // Faction ruler mode
        // Do this after updating submarkets so they're not the wrong submarkets
        if (playerInvolved && newOwnerId.equals(Misc.getCommissionFactionId()) && Nex_IsFactionRuler.isRuler(newOwnerId)) 
        {
            market.setPlayerOwned(true);
            market.getMemoryWithoutUpdate().set(ColonyManager.MEMORY_KEY_RULER_TEMP_OWNERSHIP, market.getAdmin(), 0);
        }
        
        // Templar stuff
        if (newOwnerId.equals("templars") && !oldOwnerId.equals("templars"))
        {
            //if (!market.hasCondition("exerelin_templar_control")) market.addCondition("exerelin_templar_control");
        }
        else if (!newOwnerId.equals("templars") && oldOwnerId.equals("templars"))
        {
            //if (market.hasCondition("exerelin_templar_control")) market.removeCondition("exerelin_templar_control");
        }
        
        // cancel industries under construction or upgrading?
        // only as exploit prevention
        if (isCapture && market.isPlayerOwned() && market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER)) 
        {
            for (Industry ind : new ArrayList<>(market.getIndustries())) {
                if (ind.isUpgrading()) ind.cancelUpgrade();
                else if (ind.isBuilding()) {
                    // first move any special items on the industry to storage
                    String aiCore = ind.getAICoreId();
                    SpecialItemData special = ind.getSpecialItem();
                    CargoAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
                    if (aiCore != null) {
                        storage.addCommodity(aiCore, 1);
                    }
                    if (special != null) {
                        storage.addSpecial(special, 1);
                    }

                    market.removeIndustry(ind.getId(), null, false);
                }
                String str = String.format(StringHelper.getString("exerelin_markets", "cancelWorkMsg"),
                        ind.getCurrentName(), market.getName());
                Global.getSector().getCampaignUI().addMessage(str, Misc.getTextColor(), ind.getCurrentName(), market.getName(),
                        Misc.getHighlightColor(), oldOwner.getBaseUIColor());
            }
            String str = StringHelper.getString("exerelin_invasion", "captureAntiExploitMsg");
			str = String.format(str, market.getName());
			if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
				Global.getSector().getCampaignUI().getCurrentInteractionDialog().getTextPanel().addPara(str, Misc.getHighlightColor());
			} else {
				Global.getSector().getCampaignUI().addMessage(str);
			}
            
        }
        
        
        // tariffs
        
        // no, this risks screwing market-specific tariffs
        //market.getTariff().modifyFlat("generator", Global.getSector().getFaction(newOwnerId).getTariffFraction());    
        
        // (un)apply free port and hazard pay if needed
        ColonyManager.updateFreePortSetting(market);
        boolean shouldKeepIncentives = market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY) 
                || Factions.PLAYER.equals(NexUtilsMarket.getOriginalOwner(market));
        if (!shouldKeepIncentives) 
        {
            market.setImmigrationIncentivesOn(null);
        }
        ColonyManager.updateIncome(market);
        
        NexUtilsMarket.setTariffs(market);
        
        // set submarket factions
        List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
        for (SubmarketAPI submarket : submarkets)
        {
            //if (submarket.getFaction() != oldOwner) continue;
            //log.info(String.format("Submarket %s has spec faction %s", submarket.getNameOneLine(), submarket.getSpec().getFactionId()));
            String submarketId = submarket.getSpecId();
            if (NEVER_CAPTURE_SUBMARKET.contains(submarketId)) continue;
            
            if (!ALWAYS_CAPTURE_SUBMARKET.contains(submarketId))
            {
                if (submarket.getPlugin().isFreeTransfer()) continue;
                if (!submarket.getPlugin().isParticipatesInEconomy()) continue;
                
                //if (submarket.getSpec().getFactionId() != null && !submarket.getSpec().getFactionId().isEmpty()) continue;
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
        
        // credits lost
        Integer creditsLost = null;
        if (isCapture && oldOwner.isPlayerFaction() && !newOwner.isPlayerFaction() && wasPlayerOwned) 
        {
            creditsLost = calcCreditsLost(market);
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(creditsLost);
        }
        
        // prompt player to name faction if needed
        if (newOwnerId.equals(Factions.PLAYER) && !Misc.isPlayerFactionSetUp()) {
            //Global.getSector().getCampaignUI().showPlayerFactionConfigDialog();
            Global.getSector().addTransientScript(new PlayerFactionSetupNag());
        }
        
        // intel report
        if (!silent) {
            MarketTransferIntel intel = new MarketTransferIntel(market, oldOwnerId, newOwnerId, isCapture, playerInvolved, 
                factionsToNotify, repChangeStrength, creditsLost);
            NexUtils.addExpiringIntel(intel);
        }
        
        DiplomacyManager.notifyMarketCaptured(market, oldOwner, newOwner, isCapture);
        if (playerInvolved) StatsTracker.getStatsTracker().notifyMarketCaptured(market);
        
        int marketsRemaining = NexUtilsFaction.getFactionMarkets(oldOwner.getId(), true).size();
        log.info("Faction " + oldOwner.getDisplayName() + " has " + marketsRemaining + " markets left");
        if (marketsRemaining == 0)
        {
            factionEliminated(newOwner, oldOwner, market);
        }
        
        // faction respawn event if needed
        marketsRemaining = NexUtilsFaction.getFactionMarkets(newOwnerId, true).size();
        if (marketsRemaining == 1)
        {
			log.info("Respawning faction on market transfer: " + newOwner);
            factionRespawned(newOwner, market);
            getManager().incrementNumRespawns(newOwnerId);
        }
        
        // Update raid condition
        if (market.hasCondition(RaidCondition.CONDITION_ID)) {
            ((RaidCondition)market.getCondition(RaidCondition.CONDITION_ID).getPlugin()).refreshRaids();
        }
        
        // revengeance fleet
        if (isCapture && newOwnerId.equals(PlayerFactionStore.getPlayerFactionId()) || newOwnerId.equals(Factions.PLAYER))
        {
            RevengeanceManager rvngEvent = RevengeanceManager.getManager();
            if (rvngEvent!= null) 
            {
                float sizeSq = market.getSize() * market.getSize();
                rvngEvent.addPoints(sizeSq * NexConfig.revengePointsForMarketCaptureMult, oldOwnerId);
            }
        }
        
        // rebellion
        if (isCapture && !AllianceManager.areFactionsAllied(newOwner.getId(), NexUtilsMarket.getOriginalOwner(market))
                && !RebellionIntel.isOngoing(market) && !oldOwnerId.equals("nex_derelict"))
        {
            RebellionIntel rebel = RebellionCreator.getInstance().createRebellion(market, oldOwnerId, true);
            if (rebel != null) rebel.setInitialStrengthsAfterInvasion(playerInvolved);
        }
        
        //boolean playerTaken = newOwner.isPlayerFaction() || newOwner == Misc.getCommissionFaction();
        if (isCapture) {
            market.getMemoryWithoutUpdate().set(MEMORY_KEY_RECENTLY_CAPTURED, 
                    oldOwnerId, MEMORY_KEY_RECENTLY_CAPTURED_EXPIRE);
        }
        
        // no stabilization time
        if (isCapture) {
            float timeout = (float)(7 * Math.pow(2, market.getSize() - 4));
            timeout *= Global.getSettings().getFloat("nex_invasionStabilizeTimeoutMult");
            if (timeout > 0)
                market.getMemoryWithoutUpdate().set(MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT, 
                        true, timeout);
        }
        
        if ((newOwner.isPlayerFaction() || newOwner == Misc.getCommissionFaction())
                && isCapture)
        {
            oldOwner.getMemoryWithoutUpdate().set("$nex_recentlyInvaded", true, 
                    Global.getSettings().getFloat("nex_aiCoreAndPrisonerCooldownAfterInvasion"));
        }
        
        if (playerInvolved) {
            market.getMemoryWithoutUpdate().set(MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER, true, 60);
        }
        
        // valid for mission target?
        boolean oldAllowMissions = !NexConfig.getFactionConfig(oldOwnerId).noMissionTarget;
        boolean newAllowMissions = !NexConfig.getFactionConfig(newOwnerId).noMissionTarget;
        
        if (oldAllowMissions && !newAllowMissions) {
            market.setInvalidMissionTarget(true);
        }
        else if (!oldAllowMissions && newAllowMissions) {
            market.setInvalidMissionTarget(null);
        }
        
        Nex_MarketCMD.unsetResponseFleet(market);
        
        NexUtilsMarket.reportMarketTransferred(market, newOwner, oldOwner, 
                playerInvolved, isCapture, factionsToNotify, repChangeStrength);
        
        checkForVictory();
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
    
    public static boolean shouldHaveMilitarySubmarket(MarketAPI market) 
    {
        String factionId = market.getFactionId();
        return shouldHaveMilitarySubmarket(market, factionId, 
                factionId.equals(Factions.PLAYER) || market.isPlayerOwned());
    }
    
    public static boolean shouldHaveMilitarySubmarket(MarketAPI market, String factionId, 
            boolean isPlayer) {
        if (isPlayer) return false;
        //if (factionId.equals("templars")) return false;
        if (NO_MILITARY_MARKET.contains(market.getId())) return false;
        
        return market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND) 
                || market.hasCondition("tem_avalon") || market.hasIndustry("tiandong_merchq") 
                || FORCE_MILITARY_MARKET.contains(market.getId());
    }
    
    public static void addOrRemoveMilitarySubmarket(MarketAPI market, String factionId, 
            boolean haveMilitary) {
        
        // handling for ApproLight custom military submarket
        // if market is AL-held, remove any regular military submarket if present and add AL version
        // if not, do the reverse
        // (this assumes we want any kind of military base OFC, if not remove both
        String wanted = Submarkets.GENERIC_MILITARY, unwanted = "AL_militaryMarket";
        if (factionId.equals("approlight")) {
            wanted = unwanted;
            unwanted = Submarkets.GENERIC_MILITARY;
        }
        
        addOrRemoveSubmarket(market, wanted, haveMilitary);
        addOrRemoveSubmarket(market, unwanted, false);
    }
    
    /**
     * Update <code>@market</code>'s submarkets on market capture/transfer.
     * @param market
     * @param oldOwnerId
     * @param newOwnerId
     */
    public static void updateSubmarkets(MarketAPI market, String oldOwnerId, String newOwnerId)
    {
        boolean isPlayer = newOwnerId.equals(Factions.PLAYER) || market.isPlayerOwned();
        boolean haveLocalResources = isPlayer;
        boolean haveOpen = false;
        boolean haveMilitary = shouldHaveMilitarySubmarket(market, newOwnerId, isPlayer);
        boolean haveBlackMarket = false;
        boolean haveTemplar = newOwnerId.equals("templars");
        
        if (!newOwnerId.equals("templars") && !isPlayer)
        {
            if (!NO_BLACK_MARKET.contains(market.getId()))
                haveBlackMarket = true;
        }
        
        if (!isPlayer || market.hasIndustry("commerce"))
            haveOpen = true;
        
        addOrRemoveSubmarket(market, Submarkets.LOCAL_RESOURCES, haveLocalResources);
        addOrRemoveSubmarket(market, Submarkets.SUBMARKET_OPEN, haveOpen);
        addOrRemoveSubmarket(market, Submarkets.SUBMARKET_BLACK, haveBlackMarket);
        //addOrRemoveSubmarket(market, "tem_templarmarket", haveTemplar);
        addOrRemoveMilitarySubmarket(market, newOwnerId, haveMilitary);
    }
    
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(RebellionIntel.class))
        {
            RebellionIntel rebel = (RebellionIntel)intel;
            rebel.reportPlayerMarketTransaction(transaction);
        }
    }
    
    public static void notifySlavesSold(MarketAPI market, int count)
    {
        SectorManager manager = getManager();
        manager.numSlavesRecentlySold += count;
        manager.marketLastSoldSlaves = market;
    }

    public static void addLiveFactionId(String factionId)
    {
        SectorManager manager = getManager();
        if (!manager.liveFactionIds.contains(factionId)) {
            manager.liveFactionIds.add(factionId);
            DiplomacyManager.getManager().createDiplomacyProfile(factionId);
        }
    }
    
    public List<String> getStartingFactionIdsCopy() {
        return new ArrayList<>(factionIdsAtStart);
    }
    
    /**
     * Similar to live faction IDs, but includes non-playable factions.
     * @return
     */
    public HashSet<String> getPresentFactionIdsCopy() {
        return new HashSet<>(presentFactionIds);
    }
    
    public static void removeLiveFactionId(String factionId)
    {
        SectorManager manager = getManager();
        if (manager.liveFactionIds.contains(factionId)) {
            manager.liveFactionIds.remove(factionId);
            DiplomacyManager.getManager().removeDiplomacyProfile(factionId);
        }
    }

    // runcode Console.showMessage(exerelin.campaign.SectorManager.getLiveFactionIdsCopy())
    public static ArrayList<String> getLiveFactionIdsCopy()
    {
        return new ArrayList<>(getManager().liveFactionIds);
    }
    
    public static boolean isFactionAlive(String factionId)
    {
        return getManager().liveFactionIds.contains(factionId);
    }
    
    public static void setHomeworld(SectorEntityToken entity)
    {
        getManager().homeworld = entity;
    }
    
    public static SectorEntityToken getHomeworld()
    {
        return getManager().homeworld;
    }
    
    protected static void expelPlayerFromFaction(boolean silent)
    {
        String oldFactionId = PlayerFactionStore.getPlayerFactionId();
        if (oldFactionId.equals(Factions.PLAYER)) return;
        
        NexUtilsFaction.revokeCommission();
    }
        
    public static void reinitLiveFactions()
    {
        SectorManager manager = getManager();
        List<String> temp = NexConfig.getFactions(false, false);
        manager.liveFactionIds = new HashSet<>();
        manager.factionIdsAtStart = new ArrayList<>();
        manager.historicFactionIds = new HashSet<>();
        
        for (String factionId : temp)
        {
            if (!NexUtilsFaction.getFactionMarkets(factionId, true).isEmpty())
            {
                NexFactionConfig config = NexConfig.getFactionConfig(factionId);
                if (config != null && !config.playableFaction)
                    continue;
                manager.liveFactionIds.add(factionId);
                DiplomacyManager.getManager().createDiplomacyProfile(factionId);
                manager.factionIdsAtStart.add(factionId);
                manager.historicFactionIds.add(factionId);
                setShowFactionInIntelTab(factionId, true);
            }
            else    // no need for showIntelEvenIfDead check, that's done in setShowFactionInIntelTab()
            {
                setShowFactionInIntelTab(factionId, false);
                DiplomacyManager.getManager().removeDiplomacyProfile(factionId);
            }
        }
    }
    
    public static void setAllowRespawnFactions(boolean respawn, boolean allowNew)
    {
        SectorManager manager = getManager();
        manager.respawnFactions = respawn;
        manager.onlyRespawnStartingFactions = !allowNew;
    }
    
    public enum VictoryType
    {
        CONQUEST,
        CONQUEST_ALLY,
        DIPLOMATIC,
        DIPLOMATIC_ALLY,
        DEFEAT_CONQUEST,  //not a victory type but who's counting?
        DEFEAT_DIPLOMATIC,
        CUSTOM,
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
    
    public static Comparator<Pair<Object, Integer>> victoryComparator = new Comparator<Pair<Object, Integer>>() 
    {
        @Override
        public int compare(Pair<Object, Integer> one, Pair<Object, Integer> two) {
            return Integer.compare(two.two, one.two);
        }
    };
}
