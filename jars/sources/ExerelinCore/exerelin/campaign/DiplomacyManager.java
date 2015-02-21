package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
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
        public List<String> allowedFactions1;   // TODO
        public List<String> allowedFactions2;   // TODO
        public boolean allowPirates;
        public float chance;
    }
    public static Logger log = Global.getLogger(DiplomacyManager.class);
    private static DiplomacyManager diplomacyManager;
    
    private static final String CONFIG_FILE = "data/config/diplomacyConfig.json";
    private static final String MANAGER_MAP_KEY = "exerelin_diplomacyManager";
    
    private static final List<String> disallowedFactions;
    private List<DiplomacyEventDef> eventDefs;
    private List<String> pirateFactions;
    
    private static final Float WAR_WEARINESS_DIVISOR = 6000f;
    private static final Float MIN_WAR_WEARINESS_FOR_PEACE = 2500f;
    private static final Float WAR_WEARINESS_CEASEFIRE_REDUCTION = 1600f;
    private static final Float WAR_WEARINESS_PEACE_TREATY_REDUCTION = 2500f;
    private static final Float WAR_WEARINESS_FLEET_WIN_MULT = 0.5f; // less war weariness from a fleet battle if you win
    private static final Float PEACE_TREATY_CHANCE = 0.3f;
    private Map<String, Float> warWeariness;
    private float warWearinessPerInterval;
    private DiplomacyEventDef peaceTreatyEvent;
    private DiplomacyEventDef ceasefireEvent;
    
    private float interval;
    private final IntervalUtil intervalUtil;
    
    // FIXME: this is kind of a hack
    static {
        String[] factions = {"knights_of_ludd", "luddic_path", "lions_guard", "templars", "independent"};
        disallowedFactions = Arrays.asList(factions);
    }
    
    private void loadSettings() throws IOException, JSONException {
        JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
        interval = config.getLong("eventFrequency");
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
        
        warWearinessPerInterval = (float)config.optDouble("warWearinessPerInterval", 20f);
    }

    public DiplomacyManager()
    {
        super(true);
        eventDefs = new ArrayList<>();
        pirateFactions = new ArrayList<>();
        
        try {
            loadSettings();
        } catch (IOException | JSONException ex) {
            Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
            interval = 10;
        }
        this.intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
              
        if (warWeariness == null)
        {
            warWeariness = new HashMap<>();
            List<String> factionIds = Arrays.asList(ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector()));
            for( String factionId : factionIds)
                warWeariness.put(factionId, 0f);
        }
    }
    
    public static ReputationAdjustmentResult adjustRelations(DiplomacyEventDef event, MarketAPI market, FactionAPI faction1, FactionAPI faction2, float delta)
    {   
        SectorAPI sector = Global.getSector();
        
        float before = faction1.getRelationship(faction2.getId());
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        FactionAPI playerFaction = sector.getPlayerFleet().getFaction();
        
        if (event.repEnsureAtBest != null) {
                faction1.ensureAtBest(faction2.getId(), event.repEnsureAtBest);
        }
        if (event.repEnsureAtWorst != null) {
                faction1.ensureAtWorst(faction2.getId(), event.repEnsureAtWorst);
        }
        if (event.repLimit != null)
            faction1.adjustRelationship(faction2.getId(), delta, event.repLimit);
        else
            faction1.adjustRelationship(faction2.getId(), delta);
       
        float after = faction1.getRelationship(faction2.getId());
        delta = after - before;

        if(faction1 == playerAlignedFaction)
           playerFaction.setRelationship(faction2.getId(), after);
        else if(faction2 == playerAlignedFaction)
           playerFaction.setRelationship(faction1.getId(), after);
        
        return new ReputationAdjustmentResult(delta);
    }
    
    public void doDiplomacyEvent(DiplomacyEventDef event, MarketAPI market, FactionAPI faction1, FactionAPI faction2)
    {
        SectorAPI sector = Global.getSector();
        
        float delta = MathUtils.getRandomNumberInRange(event.minRepChange, event.maxRepChange);
            
        if (delta < 0 && delta > -0.01f) delta = -0.01f;
        if (delta > 0 && delta < 0.01f) delta = 0.01f;
        delta = Math.round(delta * 100f) / 100f;
       
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
    
    public void createDiplomacyEvent()
    {
        log.info("Starting diplomacy event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        WeightedRandomPicker<DiplomacyEventDef> eventPicker = new WeightedRandomPicker();
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy())
            factions.add(sector.getFaction(factionId));
        
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();

        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            if (faction.isNeutralFaction() || faction.isPlayerFaction()) continue;
            if (disallowedFactions.contains(faction.getId())) continue;
            factionPicker.add(faction);
            factionCount++;
        }
        //log.info("Possible factions: " + factionCount);
        if (factionCount < 2) return;
        
        FactionAPI faction1 = factionPicker.pickAndRemove();
        FactionAPI faction2 = factionPicker.pickAndRemove();
        log.info("Factions are: " + faction1.getDisplayName() + ", " + faction2.getDisplayName());
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
            eventPicker.add(eventDef, eventDef.chance);
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
            if(market.getFaction() == faction1)
            {
                marketPicker.add(market);
            }
        }
        
        MarketAPI market = marketPicker.pick();
        if (market == null)
        {
            log.info("No market available");
            return;
        }
        
        doDiplomacyEvent(event, market, faction1, faction2);
    }
    
    private void updateWarWeariness()
    {
        SectorAPI sector = Global.getSector();
        String[] factionIds = ExerelinSetupData.getInstance().getAvailableFactions(sector);
        FactionAPI factionWithMostWars = null;
        int mostWarCount = 0;
        List<String> enemiesOfFaction = new ArrayList<>();
        
        for(String factionId : factionIds)
        {
            if (factionId.equals(Factions.PIRATES) || disallowedFactions.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            if(faction.isPlayerFaction() || faction.isNeutralFaction()) continue;

            float weariness = warWeariness.get(factionId);
            List<String> enemies = getFactionsAtWarWithFaction(faction, false);
            int warCount = enemies.size();
            if (warCount > 0)
            {
                log.info("Incrementing war weariness for " + faction.getDisplayName());
                weariness += enemies.size() * warWearinessPerInterval;
                if (weariness >= MIN_WAR_WEARINESS_FOR_PEACE)
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
            if (weariness < 0) weariness = 0;
            
            warWeariness.put(factionId, weariness);
        }
        
        // sue for peace?
        if (factionWithMostWars != null)
        {
            log.info("Faction " + factionWithMostWars.getDisplayName() + " wants to sue for peace");
            WeightedRandomPicker<String> picker = new WeightedRandomPicker();
            for (String enemy : enemiesOfFaction)
                picker.add(enemy, warWeariness.get(enemy));
            String toPeace = picker.pick();
            
            float sumWeariness = warWeariness.get(factionWithMostWars.getId()) + warWeariness.get(toPeace);
            log.info("Sum with " + sector.getFaction(toPeace).getDisplayName() + ": " + sumWeariness);
            if (Math.random() > sumWeariness/WAR_WEARINESS_DIVISOR)
                return;
            log.info("Negotiating treaty");
            boolean peaceTreaty = false;    // if false, only ceasefire
            // can't peace treaty if vengeful, only ceasefire
            if (factionWithMostWars.isAtWorst(toPeace, RepLevel.HOSTILE))
            {
                peaceTreaty = Math.random() < PEACE_TREATY_CHANCE;
            }
            DiplomacyEventDef event = peaceTreaty ? peaceTreatyEvent : ceasefireEvent;
            float reduction = peaceTreaty ? WAR_WEARINESS_PEACE_TREATY_REDUCTION : WAR_WEARINESS_CEASEFIRE_REDUCTION;
            // find someplace to sign the treaty
            List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
            for (MarketAPI market : markets)
            {
                if (market.getFaction() == factionWithMostWars)
                {
                    doDiplomacyEvent(event, market, factionWithMostWars, sector.getFaction(toPeace));
                    
                    float weariness1 = warWeariness.get(factionWithMostWars.getId());
                    weariness1 = Math.max(weariness1 - reduction, 0);
                    float weariness2 = warWeariness.get(toPeace);
                    weariness2 = Math.max(weariness2 - reduction, 0);
                    warWeariness.put(factionWithMostWars.getId(), weariness1);
                    warWeariness.put(toPeace, weariness2);
                    return; // done here
                }
            } 
        }
    }
    
    private void handleMarketCapture(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        float value = (market.getSize()^3) * 5;
        String loseFactionId = oldOwner.getId();
        warWeariness.put(loseFactionId, warWeariness.get(loseFactionId) + value);
    }
    
    @Override
    public void reportBattleOccurred(CampaignFleetAPI winner, CampaignFleetAPI loser)
    {
        super.reportBattleOccurred(winner, loser);
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
        
        // pirate battles don't cause war weariness
        if (winFactionId.equals(Factions.PIRATES)) {
            return;
        }
        if (loseFactionId.equals(Factions.PIRATES)) {
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
        
        warWeariness.put(winFactionId, warWeariness.get(winFactionId) + winnerLosses);
        warWeariness.put(loseFactionId, warWeariness.get(loseFactionId) + loserLosses);
    }
            
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        this.intervalUtil.advance(days);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        createDiplomacyEvent();
        updateWarWeariness();
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
            return diplomacyManager;
        
        diplomacyManager = new DiplomacyManager();
        data.put(MANAGER_MAP_KEY, diplomacyManager);
        return diplomacyManager;
    }
    
    public static List<String> getFactionsAtWarWithFaction(String factionId, boolean includePirates)
    {
        return getFactionsAtWarWithFaction(Global.getSector().getFaction(factionId), includePirates);
    }
    
    public static List<String> getFactionsAtWarWithFaction(FactionAPI faction, boolean includePirates)
    {
        SectorAPI sector = Global.getSector();
        List<String> enemies = new ArrayList<>();
        List<String> factions = Arrays.asList(ExerelinSetupData.getInstance().getAvailableFactions(sector));

        for(String otherFactionId : factions)
        {
            if (faction.isAtBest(otherFactionId, RepLevel.HOSTILE) && (includePirates || !otherFactionId.equals(Factions.PIRATES))
                    && !disallowedFactions.contains(otherFactionId))
            {
                enemies.add(otherFactionId);
            }
        }
        return enemies;
    }
    
    public static boolean isFactionAtWar(String factionId, boolean excludeNeutralAndRebels)
    {
        if(getFactionsAtWarWithFaction(factionId, !excludeNeutralAndRebels).size() > 0)
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
        if (!diplomacyManager.warWeariness.containsKey(factionId)) return 0.0f;
        return diplomacyManager.warWeariness.get(factionId);
    }
}
