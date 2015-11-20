package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager.Alliance;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class DiplomacyManager extends BaseCampaignEventListener implements EveryFrameScript
{
    public static class DiplomacyEventDef {
        public String name;
        public String stage;
        public RepLevel minRepLevelToOccur;
        public RepLevel maxRepLevelToOccur;
        public RepLevel repEnsureAtWorst;
        public RepLevel repEnsureAtBest;
        public RepLevel repLimit;
        public float minRepChange;
        public float maxRepChange;
        public List<String> allowedFactions1;
        public List<String> allowedFactions2;
        public boolean allowPirates;
        public float chance;
    }
    public static Logger log = Global.getLogger(DiplomacyManager.class);
    private static DiplomacyManager diplomacyManager;
    
    protected static final String CONFIG_FILE = "data/config/exerelin/diplomacyConfig.json";
    protected static final String MANAGER_MAP_KEY = "exerelin_diplomacyManager";
    
    protected static final List<String> disallowedFactions;
    protected static List<String> pirateFactions;
        
    protected static List<DiplomacyEventDef> eventDefs;
    
    public static final float STARTING_RELATIONSHIP_HOSTILE = -0.6f;
    public static final float STARTING_RELATIONSHIP_INHOSPITABLE = -0.4f;
    public static final float STARTING_RELATIONSHIP_WELCOMING = 0.4f;
    public static final float STARTING_RELATIONSHIP_FRIENDLY = 0.6f;
    public static final float WAR_WEARINESS_INTERVAL = 10f;
    public static final float WAR_WEARINESS_FLEET_WIN_MULT = 0.5f; // less war weariness from a fleet battle if you win
    public static final float PEACE_TREATY_CHANCE = 0.3f;
    
    public static final float DOMINANCE_MIN = 0.25f;
    public static final float DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD = -0.5f;
    public static final float DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD = 2f;
    public static final float HARD_MODE_DOMINANCE_MOD = 1.5f;
    
    protected Map<String, Float> warWeariness;
    protected static float warWearinessPerInterval = 10f;
    protected static DiplomacyEventDef peaceTreatyEvent;
    protected static DiplomacyEventDef ceasefireEvent;
    
    protected static float baseInterval = 10f;
    protected float interval = baseInterval;
    protected final IntervalUtil intervalUtil;
    
    protected float daysElapsed = 0;
    protected boolean randomFactionRelationships = false;
    
    static {
        String[] factions = {"templars", "independent"};
        disallowedFactions = Arrays.asList(factions);
        pirateFactions = new ArrayList<>();
        eventDefs = new ArrayList<>();
        
        try {
            loadSettings();
        } catch (IOException | JSONException ex) {
            Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
        }
    }
    
    private static void loadSettings() throws IOException, JSONException {
        JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
        baseInterval = (float)config.optDouble("eventFrequency", 10f);
        warWearinessPerInterval = (float)config.optDouble("warWearinessPerInterval", 10f);
        
        JSONArray pirateFactionsJson = config.getJSONArray("pirateFactions");
        for (int i=0;i<pirateFactionsJson.length();i++){ 
            pirateFactions.add(pirateFactionsJson.getString(i));
        } 
        
        JSONArray eventsJson = config.getJSONArray("events");
        for(int i=0; i<eventsJson.length(); i++)
        {
            JSONObject eventDefJson = eventsJson.getJSONObject(i);
            DiplomacyEventDef eventDef = new DiplomacyEventDef();
            eventDef.name = eventDefJson.getString("name");
            log.info("Adding diplomacy event " + eventDef.name);
            eventDef.stage = eventDefJson.getString("stage");
            
            eventDef.minRepChange = (float)eventDefJson.getDouble("minRepChange");
            eventDef.maxRepChange = (float)eventDefJson.getDouble("maxRepChange");
            eventDef.allowPirates = eventDefJson.optBoolean("allowPirates", false);
            eventDef.chance = (float)eventDefJson.optDouble("chance", 1f);
            if (eventDefJson.has("allowedFactions1"))
                eventDef.allowedFactions1 = ExerelinUtils.JSONArrayToArrayList(eventDefJson.getJSONArray("allowedFactions1"));
            if (eventDefJson.has("allowedFactions2"))
                eventDef.allowedFactions2 = ExerelinUtils.JSONArrayToArrayList(eventDefJson.getJSONArray("allowedFactions2"));
            
            String repLimit = eventDefJson.optString("repLimit");
            if (!repLimit.isEmpty())
                eventDef.repLimit = RepLevel.valueOf(repLimit.toUpperCase());
            String minRepLevelToOccur = eventDefJson.optString("minRepLevelToOccur");
            if (!minRepLevelToOccur.isEmpty())
                eventDef.minRepLevelToOccur = RepLevel.valueOf(minRepLevelToOccur.toUpperCase());
            String maxRepLevelToOccur = eventDefJson.optString("maxRepLevelToOccur");
            if (!maxRepLevelToOccur.isEmpty())
                eventDef.maxRepLevelToOccur = RepLevel.valueOf(maxRepLevelToOccur.toUpperCase());
            String repEnsureAtWorst = eventDefJson.optString("repEnsureAtWorst");
            if (!repEnsureAtWorst.isEmpty())
                eventDef.repEnsureAtWorst = RepLevel.valueOf(repEnsureAtWorst.toUpperCase());
            String repEnsureAtBest = eventDefJson.optString("repEnsureAtBest");
            if (!repEnsureAtBest.isEmpty())
                eventDef.repEnsureAtBest = RepLevel.valueOf(repEnsureAtBest.toUpperCase()); 
            
            eventDefs.add(eventDef);
            
            if(eventDef.name.equals("Peace Treaty"))
                peaceTreatyEvent = eventDef;
            else if (eventDef.name.equals("Ceasefire"))
                ceasefireEvent = eventDef;
        }
    }

    public DiplomacyManager()
    {
        super(true);
        
        interval = getDiplomacyInterval();
        this.intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
              
        if (warWeariness == null)
        {
            warWeariness = new HashMap<>();
        }
    }
    
    public static float getDominanceFactor(String factionId)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        int globalSize = 0;
        for (MarketAPI market: allMarkets) globalSize += market.getSize();
        if (globalSize == 0) return 0;
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        List<MarketAPI> ourMarkets = null;
        Alliance alliance = AllianceManager.getFactionAlliance(factionId);
        if (alliance != null) ourMarkets = alliance.getAllianceMarkets();
        else ourMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId);
        //ourMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId);
        if (factionId.equals(playerAlignedFactionId) && !playerAlignedFactionId.equals("player_npc"))
        {
            List<MarketAPI> playerNpcMarkets = ExerelinUtilsFaction.getFactionMarkets("player_npc");
            ourMarkets.addAll(playerNpcMarkets);
        }
        
        int ourSize = 0;
        for (MarketAPI market: ourMarkets) ourSize += market.getSize();
        
        if (ourSize == 0) return 0;
        
        boolean isPlayer = factionId.equals(playerAlignedFactionId) || (alliance != null && alliance == AllianceManager.getFactionAlliance(playerAlignedFactionId));
        if (SectorManager.getHardMode() && isPlayer)
            ourSize *= HARD_MODE_DOMINANCE_MOD;
        
        return (float)ourSize / globalSize;
    }
    
    protected float getDiplomacyInterval()
    {
        int numFactions = SectorManager.getLiveFactionIdsCopy().size() - 2;
        if (numFactions < 0) numFactions = 0;
        return baseInterval * (float)Math.pow(0.95, numFactions);
    }
    
    // started working on this but decided I don't need it no more
    /*
    public static ReputationAdjustmentResult testAdjustRelations(FactionAPI faction1, FactionAPI faction2, float delta,
            RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit)
    {
        SectorAPI sector = Global.getSector();
        
        float before = faction1.getRelationship(faction2.getId());
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        FactionAPI playerFaction = sector.getPlayerFleet().getFaction();
        String faction1Id = faction1.getId();
        String faction2Id = faction2.getId();
    }
    */
    
    public static ReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, float delta,
            RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit)
    {   
        SectorAPI sector = Global.getSector();
        
        float before = faction1.getRelationship(faction2.getId());
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        FactionAPI playerFaction = sector.getPlayerFleet().getFaction();
        String faction1Id = faction1.getId();
        String faction2Id = faction2.getId();
        
        if (limit != null)
            faction1.adjustRelationship(faction2Id, delta, limit);
        else
            faction1.adjustRelationship(faction2Id, delta);
        
        if (ensureAtBest != null) {
                faction1.ensureAtBest(faction2Id, ensureAtBest);
        }
        if (ensureAtWorst != null) {
                faction1.ensureAtWorst(faction2Id, ensureAtWorst);
        }
       
        float after = faction1.getRelationship(faction2Id);
        delta = after - before;
        //log.info("Relationship delta: " + delta);

        if(faction1 == playerAlignedFaction)
        {
            playerFaction.setRelationship(faction2Id, after);
            faction2.setRelationship("player_npc", after);
        }
        else if(faction2 == playerAlignedFaction)
        {
            playerFaction.setRelationship(faction1Id, after);
            faction1.setRelationship("player_npc", after);
        }
        AllianceManager.remainInAllianceCheck(faction1Id, faction2Id);
        AllianceManager.syncAllianceRelationshipsToFactionRelationship(faction1Id, faction2Id);
        ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(true);
        
        SectorManager.checkForVictory();
        return new ReputationAdjustmentResult(delta);
    }
    
    public static ReputationAdjustmentResult adjustRelations(DiplomacyEventDef event, FactionAPI faction1, FactionAPI faction2, float delta)
    {
        return adjustRelations(faction1, faction2, delta, event.repEnsureAtBest, event.repEnsureAtWorst, event.repLimit);
    }
    
    public void doDiplomacyEvent(DiplomacyEventDef event, MarketAPI market, FactionAPI faction1, FactionAPI faction2)
    {
        SectorAPI sector = Global.getSector();
        
        float delta = MathUtils.getRandomNumberInRange(event.minRepChange, event.maxRepChange);
            
        if (delta < 0 && delta > -0.01f) delta = -0.01f;
        if (delta > 0 && delta < 0.01f) delta = 0.01f;
        delta = Math.round(delta * 100f) / 100f;
       
        delta = DiplomacyManager.adjustRelations(event, faction1, faction2, delta).delta;
        
        if (Math.abs(delta) >= 0.01f) {
            log.info("Transmitting event: " + event.name);
            HashMap<String, Object> params = new HashMap<>();
            String eventType = "exerelin_diplomacy";
            params.put("event", event);
            params.put("delta", delta);
            params.put("otherFaction", faction2);
            sector.getEventManager().startEvent(new CampaignEventTarget(market), eventType, params);
        }
    }
    
    public static void createDiplomacyEvent(FactionAPI faction1, FactionAPI faction2)
    {
        if (diplomacyManager == null) return;
        
        WeightedRandomPicker<DiplomacyEventDef> eventPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(faction1.getId());
        
        log.info("Factions are: " + faction1.getDisplayName() + ", " + faction2.getDisplayName());
        //float dominance = getDominanceFactor(faction1.getId()) + getDominanceFactor(faction2.getId());
        //dominance = dominance/2;
        float dominance = Math.max( getDominanceFactor(faction1.getId()), getDominanceFactor(faction2.getId()) );
        log.info("Dominance factor: " + dominance);
        for (DiplomacyEventDef eventDef: eventDefs)
        {
            if ((pirateFactions.contains(faction1.getId()) || pirateFactions.contains(faction2.getId())) && !eventDef.allowPirates)
            {
                //log.info("Pirates on non-pirate event, invalid");
                continue;
            }
            
            //float rel = faction1.getRelationship(faction2.getId());
            if (eventDef.minRepLevelToOccur != null && !faction1.isAtWorst(faction2, eventDef.minRepLevelToOccur))
            {
                //log.info("Rep too low");
                continue;
            }
            if (eventDef.maxRepLevelToOccur != null && !faction1.isAtBest(faction2, eventDef.maxRepLevelToOccur))
            {
                //log.info("Rep too high");
                continue;
            }
            if (eventDef.allowedFactions1 != null && !eventDef.allowedFactions1.contains(faction1.getId()))
                continue;
            if (eventDef.allowedFactions2 != null && !eventDef.allowedFactions2.contains(faction2.getId()))
                continue;
            
            boolean isNegative = (eventDef.maxRepChange + eventDef.minRepChange)/2 < 0;
            
            float chance = eventDef.chance;
            if (dominance > DOMINANCE_MIN)
            {
                float strength = (dominance - DOMINANCE_MIN)/(1 - DOMINANCE_MIN);
                if (isNegative) chance = chance + (DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD * strength);
                else chance = chance + (DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD * strength);
            }
            if (chance <= 0) continue;
            eventPicker.add(eventDef, chance);
        }
        DiplomacyEventDef event = eventPicker.pick();
        if (event == null)
        {
            log.info("No event available");
            return;
        }
        log.info("Trying event: " + event.name);
        for (MarketAPI market:markets)
        {
            marketPicker.add(market);
        }
        
        MarketAPI market = marketPicker.pick();
        if (market == null)
        {
            log.info("No market available");
            return;
        }
        
        diplomacyManager.doDiplomacyEvent(event, market, faction1, faction2);
    }
    
    public static void createDiplomacyEvent()
    {
        if (diplomacyManager == null) return;
        
        log.info("Starting diplomacy event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy())
            factions.add(sector.getFaction(factionId));

        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            if (faction.isNeutralFaction()) continue;
            if (faction.getId().equals("player_npc") && !faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            
            if (disallowedFactions.contains(faction.getId())) continue;
            factionPicker.add(faction);
            factionCount++;
        }
        //log.info("Possible factions: " + factionCount);
        if (factionCount < 2) return;
        
        FactionAPI faction1 = factionPicker.pickAndRemove();
        FactionAPI faction2 = factionPicker.pickAndRemove();
        createDiplomacyEvent(faction1, faction2);
    }
    
    private void reduceWarWeariness(String factionId, float amount)
    {
        Alliance alliance = AllianceManager.getFactionAlliance(factionId);
        if (alliance != null)
        {
            for (String member : alliance.members) 
            {
                float weariness = getWarWeariness(member);
                weariness = Math.max(weariness - amount, 0);
                warWeariness.put(member, weariness);
            }
        }
        else
        {
            float weariness = getWarWeariness(factionId);
            weariness = Math.max(weariness - amount, 0);
            warWeariness.put(factionId, weariness);
        }
    }
    
    private void updateWarWeariness()
    {
        SectorAPI sector = Global.getSector();
        List<String> factionIds = SectorManager.getLiveFactionIdsCopy();
        FactionAPI factionWithMostWars = null;
        int mostWarCount = 0;
        List<String> enemiesOfFaction = new ArrayList<>();
        
        for(String factionId : factionIds)
        {
            if (pirateFactions.contains(factionId)) continue;
            if (disallowedFactions.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            if (faction.isNeutralFaction()) continue;
            if (faction.getId().equals("player_npc") && !faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;

            float weariness = getWarWeariness(factionId);
            List<String> enemies = getFactionsAtWarWithFaction(faction, false, false);
            int warCount = enemies.size();
            if (warCount > 0)
            {
                //log.info("Incrementing war weariness for " + faction.getDisplayName());
                weariness += enemies.size() * warWearinessPerInterval;
                if (weariness >= ExerelinConfig.minWarWearinessForPeace)
                {
                    if (warCount > mostWarCount)
                    {
                        factionWithMostWars = faction;
                        enemiesOfFaction = enemies;
                        mostWarCount = warCount;
                    }
                }
            }
            else weariness -= warWearinessPerInterval;
            if (weariness < 0) weariness = 0f;
            
            warWeariness.put(factionId, weariness);
        }
        
        // sue for peace?
        if (factionWithMostWars != null)
        {
            log.info("Faction " + factionWithMostWars.getDisplayName() + " wants to sue for peace");
            WeightedRandomPicker<String> picker = new WeightedRandomPicker();
            for (String enemy : enemiesOfFaction)
                picker.add(enemy, getWarWeariness(enemy));
            String toPeace = picker.pick();
            
            float sumWeariness = getWarWeariness(factionWithMostWars.getId()) + getWarWeariness(toPeace);
            log.info("Sum with " + sector.getFaction(toPeace).getDisplayName() + ": " + sumWeariness);
            if (Math.random() > sumWeariness/ExerelinConfig.warWearinessDivisor)
                return;
            log.info("Negotiating treaty");
            boolean peaceTreaty = false;    // if false, only ceasefire
            // can't peace treaty if vengeful, only ceasefire
            if (factionWithMostWars.isAtWorst(toPeace, RepLevel.HOSTILE))
            {
                peaceTreaty = Math.random() < PEACE_TREATY_CHANCE;
            }
            DiplomacyEventDef event = peaceTreaty ? peaceTreatyEvent : ceasefireEvent;
            float reduction = peaceTreaty ? ExerelinConfig.warWearinessPeaceTreatyReduction : ExerelinConfig.warWearinessCeasefireReduction;
            // find someplace to sign the treaty
            List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
            for (MarketAPI market : markets)
            {
                if (market.getFaction() == factionWithMostWars)
                {
                    doDiplomacyEvent(event, market, factionWithMostWars, sector.getFaction(toPeace));
                    reduceWarWeariness(factionWithMostWars.getId(), reduction);
                    reduceWarWeariness(toPeace, reduction);
                    return; // done here
                }
            } 
        }
    }
    
    private void handleMarketCapture(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        String loseFactionId = oldOwner.getId();
        if (!warWeariness.containsKey(loseFactionId)) return;
        float value = (market.getSize()^3) * 5;
        
        warWeariness.put(loseFactionId, getWarWeariness(loseFactionId) + value);
    }
    
    @Override
    public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
    {
        CampaignFleetAPI loser = battle.getPrimary(battle.getOtherSideFor(winner));
        FactionAPI winFaction = winner.getFaction();
        FactionAPI loseFaction = loser.getFaction();
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = Global.getSector().getFaction(playerAlignedFactionId);
        
        if (winFaction.isPlayerFaction())
        {
            winFaction = playerAlignedFaction;
        }
        else if (loseFaction.isPlayerFaction())
        {
            loseFaction = playerAlignedFaction;
        }
        
        String winFactionId = winFaction.getId();
        String loseFactionId = loseFaction.getId();
        
        // e.g. independents
        if (!warWeariness.containsKey(winFactionId) || !warWeariness.containsKey(loseFactionId))
        {
            return;
        }
        // pirate battles don't cause war weariness
        if (pirateFactions.contains(winFactionId)) {
            return;
        }
        if (pirateFactions.contains(loseFactionId)) {
            return;
        }

        float loserLosses = 0f;
        float winnerLosses = 0f;
        List<FleetMemberAPI> loserCurrent = loser.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : loser.getFleetData().getSnapshot()) {
            if (!loserCurrent.contains(member)) {
                loserLosses += member.getFleetPointCost();
            }
        }
        List<FleetMemberAPI> winnerCurrent = winner.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : winner.getFleetData().getSnapshot()) {
            if (!winnerCurrent.contains(member)) {
                winnerLosses += member.getFleetPointCost();
            }
        }
        winnerLosses *= WAR_WEARINESS_FLEET_WIN_MULT;
        
        warWeariness.put(winFactionId, getWarWeariness(winFactionId) + winnerLosses);
        warWeariness.put(loseFactionId, getWarWeariness(loseFactionId) + loserLosses);
    }
            
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        daysElapsed += amount;
        if (daysElapsed >= WAR_WEARINESS_INTERVAL)
        {
            daysElapsed -= WAR_WEARINESS_INTERVAL;
            updateWarWeariness();
        }
        
        this.intervalUtil.advance(days);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        createDiplomacyEvent();
        interval = getDiplomacyInterval();
        intervalUtil.setInterval(interval * 0.75f, interval * 1.25f);
    }
    
    @Override
    public void reportPlayerReputationChange(String faction, float delta) {
        FactionAPI player = Global.getSector().getFaction("player");
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", faction);
        ExerelinUtilsReputation.syncFactionRelationshipToPlayer(playerAlignedFactionId, faction);
        if (!playerAlignedFactionId.equals("player_npc"))
            ExerelinUtilsReputation.syncFactionRelationshipToPlayer("player_npc", faction);
        
        if (player.isAtBest(PlayerFactionStore.getPlayerFactionId(), RepLevel.INHOSPITABLE))
            SectorManager.scheduleExpelPlayerFromFaction();

        SectorManager.checkForVictory();
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
    
    public static DiplomacyManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        diplomacyManager = (DiplomacyManager)data.get(MANAGER_MAP_KEY);
        if (diplomacyManager != null)
        {
            try {
                diplomacyManager.loadSettings();
            } catch (IOException | JSONException ex) {
                Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
            }
            return diplomacyManager;
        }
        
        diplomacyManager = new DiplomacyManager();
        data.put(MANAGER_MAP_KEY, diplomacyManager);
        return diplomacyManager;
    }
    
    public static List<String> getFactionsAtWarWithFaction(String factionId, boolean includePirates, boolean includeTemplars)
    {
        return getFactionsAtWarWithFaction(Global.getSector().getFaction(factionId), includePirates, includeTemplars);
    }
    
    public static List<String> getFactionsAtWarWithFaction(FactionAPI faction, boolean includePirates, boolean includeTemplars)
    {
        SectorAPI sector = Global.getSector();
        List<String> enemies = new ArrayList<>();
        List<String> factions = SectorManager.getLiveFactionIdsCopy();

        for(String otherFactionId : factions)
        {
            if (faction.isAtBest(otherFactionId, RepLevel.HOSTILE) && (includePirates || !pirateFactions.contains(otherFactionId))
                    && (otherFactionId.equals("templars") && includeTemplars || !disallowedFactions.contains(otherFactionId) ))
            {
                enemies.add(otherFactionId);
            }
        }
        return enemies;
    }
    
    public static boolean isFactionAtWar(String factionId, boolean excludeNeutralAndRebels)
    {
        if(getFactionsAtWarWithFaction(factionId, !excludeNeutralAndRebels, true).size() > 0)
            return true;
        else
            return false;
    }
    
    public static List<String> getFactionsAlliedWithFaction(String factionId)
    {
        List<String> allies = new ArrayList<>();

        List<FactionAPI> factions = Global.getSector().getAllFactions();

        for(int i = 0; i < factions.size(); i++)
        {
            if(factions.get(i).isAtWorst(factionId, RepLevel.WELCOMING))
                allies.add(factions.get(i).getId());
        }

        return allies;
    }
    
    public static void notifyMarketCaptured(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        if (diplomacyManager == null) return;
        diplomacyManager.handleMarketCapture(market, oldOwner, newOwner);
    }
    
    public static float getWarWeariness(String factionId)
    {
        if (diplomacyManager == null) 
        {
            log.info("No diplomacy manager found");
            return 0.0f;
        }
        if (!diplomacyManager.warWeariness.containsKey(factionId))
        {
            diplomacyManager.warWeariness.put(factionId, 0f);
            return 0.0f;
        }
        return diplomacyManager.warWeariness.get(factionId);
    }
    
    public static List<String> getPirateFactionsCopy()
    {
        return new ArrayList<>(pirateFactions);
    }
    
    public static void setRelationshipAtBest(String factionId, String otherFactionId, float rel)
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        float currentRel = faction.getRelationship(otherFactionId);
        if (rel < currentRel)
            faction.setRelationship(otherFactionId, rel);
    }
    
    // set relationships for hostile-to-all factions (Templars, Dark Spire, infected)
    public static void handleHostileToAllFaction(String factionId, List<String> factionIds)
    {
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (factionConfig == null) return;
        SectorAPI sector = Global.getSector();
        String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        switch (factionConfig.hostileToAll) {
            case 3:
                for (String otherFactionId : factionIds)
                {
                    FactionAPI otherFaction = sector.getFaction(otherFactionId);
                    if (!otherFaction.isNeutralFaction() && !otherFactionId.equals(factionId))
                    {
                        setRelationshipAtBest(factionId, otherFactionId, -1f);
                    }
                }
                break;
            case 2:
                for (String otherFactionId : factionIds)
                {
                    FactionAPI otherFaction = sector.getFaction(otherFactionId);
                    if (!otherFaction.isNeutralFaction() && !otherFactionId.equals(factionId))
                    {
                        setRelationshipAtBest(factionId, otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                    }
                }
                break;
            case 1:
                for (String otherFactionId : factionIds)
                {
                    FactionAPI otherFaction = sector.getFaction(otherFactionId);
                    if (otherFactionId.equals("player_npc") && otherFactionId.equals(selectedFactionId))
                    {
                        otherFaction.setRelationship(factionId, STARTING_RELATIONSHIP_INHOSPITABLE);
                    }
                    else if (!otherFaction.isNeutralFaction() && !otherFactionId.equals("factionId"))
                    {
                        setRelationshipAtBest(factionId, otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                    }
                }
        }
    }
    
    public static void initFactionRelationships(boolean midgameReset)
    {
        SectorAPI sector = Global.getSector();
        FactionAPI player = sector.getFaction("player");
        String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI selectedFaction = sector.getFaction(selectedFactionId);
        log.info("Selected faction is " + selectedFaction + " | " + selectedFactionId);

        //List<String> factionIds = ectorManager.getLiveFactionIdsCopy();
        //factionIds.add("independent");
        //factionIds.add("player_npc");
        
        List<String> factionIds = new ArrayList<>();
        List<String> alreadyRandomizedIds = new ArrayList<>();
        alreadyRandomizedIds.add("independent");
        
        for (FactionAPI faction : sector.getAllFactions())
        {
            if (faction.isNeutralFaction() || faction.isPlayerFaction()) continue;
            factionIds.add(faction.getId());
        }
        for (String factionId : factionIds)
        {
            if (!SectorManager.isFactionAlive(factionId) && !factionId.equals("player_npc"))
            {
                if (!ExerelinUtilsFaction.isExiInCorvus(factionId)) alreadyRandomizedIds.add(factionId);
                handleHostileToAllFaction(factionId, factionIds);
            }
        }

        boolean randomize = false;
        if (diplomacyManager != null)
        {
            randomize = diplomacyManager.randomFactionRelationships;
        }
        
        
        
        if (SectorManager.getCorvusMode() && !randomize)
        {
            // load vanilla relationships
            FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
            FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
            FactionAPI pirates = sector.getFaction(Factions.PIRATES);
            FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
            FactionAPI kol = sector.getFaction(Factions.KOL);
            FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
            FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
            FactionAPI playerFac = sector.getFaction(Factions.PLAYER);
            FactionAPI diktat = sector.getFaction(Factions.DIKTAT);

            playerFac.setRelationship(hegemony.getId(), 0);
            playerFac.setRelationship(tritachyon.getId(), 0);
            playerFac.setRelationship(pirates.getId(), -0.65f);
            playerFac.setRelationship(independent.getId(), 0);
            playerFac.setRelationship(kol.getId(), 0);
            playerFac.setRelationship(church.getId(), 0);
            playerFac.setRelationship(path.getId(), 0);

            hegemony.setRelationship(tritachyon.getId(), RepLevel.HOSTILE);
            hegemony.setRelationship(pirates.getId(), RepLevel.HOSTILE);
            hegemony.setRelationship(path.getId(), RepLevel.HOSTILE);

            tritachyon.setRelationship(pirates.getId(), RepLevel.HOSTILE);
            tritachyon.setRelationship(kol.getId(), RepLevel.HOSTILE);
            tritachyon.setRelationship(church.getId(), RepLevel.HOSTILE);
            tritachyon.setRelationship(path.getId(), RepLevel.VENGEFUL);

            pirates.setRelationship(kol.getId(), RepLevel.HOSTILE);
            pirates.setRelationship(church.getId(), RepLevel.HOSTILE);
            pirates.setRelationship(path.getId(), RepLevel.HOSTILE);
            pirates.setRelationship(independent.getId(), RepLevel.HOSTILE);
            pirates.setRelationship(diktat.getId(), RepLevel.HOSTILE);
            pirates.setRelationship("player_npc", -0.65f);

            church.setRelationship(kol.getId(), RepLevel.COOPERATIVE);
            church.setRelationship(path.getId(), RepLevel.SUSPICIOUS);
            path.setRelationship(kol.getId(), RepLevel.FAVORABLE);
        }
        else
        {
            // first make everyone neutral to each other (for midgame reset)
            if (midgameReset)
            {
                for (String factionId : factionIds) 
                {
                    FactionAPI faction = sector.getFaction(factionId);
                    for (String otherFactionId: factionIds)
                    {
                        if (factionId.equals(otherFactionId)) continue;
                        faction.setRelationship(otherFactionId, 0);
                    }
                }
            }
            
            // pirates are hostile to everyone, except some factions like Mayorate
            for (String factionId : factionIds) 
            {
                FactionAPI faction = sector.getFaction(factionId);
                ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
                if ((factionConfig == null || !factionConfig.isPirateNeutral) && !faction.isNeutralFaction() && !ExerelinUtilsFaction.isPirateFaction(factionId))
                {
                    for (String pirateFactionId : pirateFactions) {
                        FactionAPI pirateFaction = sector.getFaction(pirateFactionId);
                        if (pirateFaction != null)
                            pirateFaction.setRelationship(factionId, STARTING_RELATIONSHIP_HOSTILE);
                    }
                }
            }

            // randomize if needed
            for (String factionId : factionIds) {
                FactionAPI faction = sector.getFaction(factionId);
                if (randomize)
                {
                    if (faction.isNeutralFaction() || faction.isPlayerFaction()) continue;
                    if (alreadyRandomizedIds.contains(factionId)) continue;
                    alreadyRandomizedIds.add(factionId);
                                        
                    for (String otherFactionId: factionIds)
                    {
                        if (alreadyRandomizedIds.contains(otherFactionId)) continue;
                        if (otherFactionId.equals(factionId)) continue;

                        FactionAPI otherFaction = sector.getFaction(otherFactionId);
                        if (otherFaction.isNeutralFaction() ||otherFaction.isPlayerFaction()) continue;

                        if (Math.random() < 0.5) // 50% chance to do nothing (lower clutter)
                        {
                            if (ExerelinUtilsFaction.isPirateFaction(factionId) || ExerelinUtilsFaction.isPirateFaction(otherFactionId))
                                faction.setRelationship(otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                            else
                                faction.setRelationship(otherFactionId, 0);
                        }
                        faction.setRelationship(otherFactionId, MathUtils.getRandomNumberInRange(-1f, 0.55f));
                    }
                    handleHostileToAllFaction(factionId, factionIds);
                }

                else    // start hostile with hated factions, friendly with liked ones (from config)
                {
                    // faction not currently alive; don't bother setting relationships
                    if (!SectorManager.isFactionAlive(factionId))
                    {
                        continue;
                    }
                    
                    ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
                    if (factionConfig == null) continue;
                    
                    handleHostileToAllFaction(factionId, factionIds);
                    
                    if (factionConfig.factionsLiked.length > 0)
                    {
                        for (String likedFactionId : factionConfig.factionsLiked) {
                            FactionAPI dislikedFaction = sector.getFaction(likedFactionId);
                            if (dislikedFaction != null && !dislikedFaction.isNeutralFaction())
                            {
                                //log.info(faction.getDisplayName() + " likes " + dislikedFaction.getDisplayName());
                                faction.setRelationship(likedFactionId, STARTING_RELATIONSHIP_WELCOMING);
                            }
                        }
                    }  
                    
                    if (factionConfig.factionsDisliked.length > 0)
                    {
                        for (String dislikedFactionId : factionConfig.factionsDisliked) {
                            FactionAPI dislikedFaction = sector.getFaction(dislikedFactionId);
                            if (dislikedFaction != null && !dislikedFaction.isNeutralFaction())
                            {
                                //log.info(faction.getDisplayName() + " hates " + dislikedFaction.getDisplayName());
                                setRelationshipAtBest(factionId, dislikedFactionId, STARTING_RELATIONSHIP_HOSTILE);
                            }
                        }
                    }  
                }
            }
        }
        
        FactionAPI bountyHunters = sector.getFaction("merc_hostile");
        if (bountyHunters != null)
        {
            bountyHunters.setRelationship(Factions.INDEPENDENT, 1f);
            bountyHunters.setRelationship(Factions.PLAYER, -1f);
            bountyHunters.setRelationship("player_npc", -1f);
        }
        
        FactionAPI famousBounty = sector.getFaction("famous_bounty");
        if (famousBounty != null)
        {
            for (String factionId : factionIds)
            {
                FactionAPI faction = sector.getFaction(factionId);
                if (!faction.isNeutralFaction() && !factionId.equals("famous_bounty"))
                {
                    famousBounty.setRelationship(factionId, 0f);
                }
            }
            famousBounty.setRelationship(Factions.PLAYER, -1f);
            //famousBounty.setRelationship("player_npc", -1f);
        }
        
        
        player.setRelationship("player_npc", 1f);
         // set player relations based on selected faction
        PlayerFactionStore.saveIndependentPlayerRelations();
        ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(selectedFactionId, true);
        
        if (selectedFactionId.equals("player_npc"))
        {

        }
        else {
            player.setRelationship(selectedFactionId, STARTING_RELATIONSHIP_FRIENDLY);
            //ExerelinUtilsReputation.syncFactionRelationshipsToPlayer("player_npc");	// already done in syncPlayerRelationshipsToFaction
        }
        
    }
    
    public static void resetFactionRelationships(String factionId)
    {
        if (!factionId.equals(PlayerFactionStore.getPlayerFactionId()) 
                && !ExerelinUtilsFaction.isFactionHostileToAll(factionId)
                && !ExerelinUtilsFaction.isExiInCorvus(factionId))
        {
            for (FactionAPI faction : Global.getSector().getAllFactions())
            {
                String otherFactionId = faction.getId();
                if (!ExerelinUtilsFaction.isFactionHostileToAll(otherFactionId)
                        && !ExerelinUtilsFaction.isExiInCorvus(otherFactionId)
                        && !otherFactionId.equals(factionId))
                {
                    faction.setRelationship(factionId, 0f);
                }
            }
        }
    }
    
    public static void setRandomFactionRelationships(boolean random)
    {
        if (diplomacyManager == null) return;
        diplomacyManager.randomFactionRelationships = random;
    }
}
