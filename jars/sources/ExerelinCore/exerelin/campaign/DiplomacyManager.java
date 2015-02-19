package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    private static final String CONFIG_FILE = "data/config/diplomacyConfig.json";
    private static final List<String> disallowedFactions;
    public static Logger log = Global.getLogger(DiplomacyManager.class);

    private List<DiplomacyEventDef> eventDefs;
    private List<String> pirateFactions;
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
        }
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
    }
    
    public ReputationAdjustmentResult CreateDiplomacyEvent()
    {
        log.info("Starting diplomacy event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        WeightedRandomPicker<DiplomacyEventDef> eventPicker = new WeightedRandomPicker();
        List<FactionAPI> factions = sector.getAllFactions();
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
        if (factionCount < 2) return new ReputationAdjustmentResult(0);
        
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
            return new ReputationAdjustmentResult(0);
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
            return new ReputationAdjustmentResult(0);
        }
        
        float delta = MathUtils.getRandomNumberInRange(event.minRepChange, event.maxRepChange);
        boolean positive = delta > 0;
            
        if (delta < 0 && delta > -0.01f) delta = -0.01f;
        if (delta > 0 && delta < 0.01f) delta = 0.01f;
        delta = Math.round(delta * 100f) / 100f;

        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        FactionAPI playerFaction = sector.getPlayerFleet().getFaction();
        
        float before = faction1.getRelationship(faction2.getId());
        
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
        
        log.info("Relationship delta is " + delta);
        
        if (Math.abs(delta) >= 0.01f) {
            log.info("Transmitting event: " + event.name);
            HashMap<String, Object> params = new HashMap<>();
            String eventType = "exerelin_diplomacy_" + (positive ? "positive" : "negative");
            params.put("stage", event.stage);
            params.put("otherFaction", faction2);
            params.put("before", before);
            params.put("after", after);
            params.put("delta", delta);
            sector.getEventManager().startEvent(new CampaignEventTarget(market), eventType, params);
        }
        return new ReputationAdjustmentResult(delta);
    }
    
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        this.intervalUtil.advance(days);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        CreateDiplomacyEvent();
        // TODO handle war weariness
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
}
