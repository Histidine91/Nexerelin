package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.covertops.*;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.utilities.ExerelinUtilsMarket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class CovertOpsManager extends BaseCampaignEventListener implements EveryFrameScript {
    
    protected enum CovertActionType {
        RAISE_RELATIONS,
        LOWER_RELATIONS,
        DESTABILIZE_MARKET,
        SABOTAGE_RESERVE,
        DESTROY_FOOD,
        INSTIGATE_REBELLION,
    }
    
    public static Logger log = Global.getLogger(CovertOpsManager.class);
    private static CovertOpsManager covertWarfareManager;
    
    private static final String MANAGER_MAP_KEY = "exerelin_covertWarfareManager";
    private static final String CONFIG_FILE = "data/config/exerelin/agentConfig.json";
    public static final float NPC_EFFECT_MULT = 1.5f;
    public static final List<String> DISALLOWED_FACTIONS;
     
    protected static Map<String, Object> config;
       
    private static float baseInterval = 45f;
    private float interval = baseInterval;
    private final IntervalUtil intervalUtil;
    
    static {
        String[] factions = {Factions.NEUTRAL, Factions.PLAYER, Factions.INDEPENDENT};    //{"templars", "independent"};
        DISALLOWED_FACTIONS = Arrays.asList(factions);
        
        try {
            loadSettings();
        } catch (IOException | JSONException | NullPointerException ex) {
            log.error(ex);
        }
    }
    
    private static void loadSettings() throws IOException, JSONException {
        JSONObject configJson = Global.getSettings().loadJSON(CONFIG_FILE);
                
        config = ExerelinUtils.jsonToMap(configJson);
        //baseInterval = (float)(double)config.get("eventFrequency");   // ClassCastException
        baseInterval = (float)configJson.optDouble("eventFrequency", 15f);
    }
    
    public static Map<String, Object> getConfig() {
        return config;
    }

    public CovertOpsManager()
    {
        super(true);
        intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
    }
    
    public void handleNpcCovertActions()
    {
        log.info("Starting covert warfare event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> agentFactionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<FactionAPI> targetFactionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        WeightedRandomPicker<CovertActionType> actionPicker = new WeightedRandomPicker();
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy())
            factions.add(sector.getFaction(factionId));
        
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();

        actionPicker.add(CovertActionType.RAISE_RELATIONS, 1.2f);
        actionPicker.add(CovertActionType.LOWER_RELATIONS, 1.5f);
        actionPicker.add(CovertActionType.DESTABILIZE_MARKET, 1.25f);
        actionPicker.add(CovertActionType.SABOTAGE_RESERVE, 1.25f);
        //actionPicker.add(CovertActionType.DESTROY_FOOD, 1.25f);
        actionPicker.add(CovertActionType.INSTIGATE_REBELLION, 0.25f);
        
        CovertActionType actionType = actionPicker.pick();
        
        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            String factionId = faction.getId();
            if (DISALLOWED_FACTIONS.contains(factionId)) continue;
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;  // pirates don't do covert warfare
            if (!ExerelinConfig.followersAgents && factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
            ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(factionId);
            if (factionConf != null && !factionConf.allowAgentActions) continue;
            
            agentFactionPicker.add(faction);
            factionCount++;
        }
        if (factionCount < 2) return;
        
        FactionAPI agentFaction = agentFactionPicker.pick();
        log.info("Trying action: " + actionType.name());
        
        factionCount = 0;
        for (FactionAPI faction: factions)
        {
            String factionId = faction.getId();
            ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(factionId);
            if (factionConf != null && !factionConf.allowAgentActions) continue;
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;  // pirates aren't targeted for covert warfare
            if (DISALLOWED_FACTIONS.contains(faction.getId())) continue;
            if (faction == agentFaction) continue;
            
            RepLevel repLevel = faction.getRelationshipLevel(agentFaction);
            float dominance = DiplomacyManager.getDominanceFactor(faction.getId());
            float weight = 1f;
            if (actionType == CovertActionType.RAISE_RELATIONS)
            {
                if (repLevel == RepLevel.FAVORABLE || repLevel == RepLevel.WELCOMING) weight = 1f;
                else if (repLevel == RepLevel.NEUTRAL) weight = 1.5f;
                else if (repLevel == RepLevel.SUSPICIOUS) weight = 2f;
                else if (repLevel == RepLevel.INHOSPITABLE) weight = 3f;
                else continue;
                
                weight = weight * (1.25f - dominance);
            }
            else if (actionType == CovertActionType.LOWER_RELATIONS)
            {
                if (repLevel == RepLevel.FAVORABLE) weight = 0.25f;
                else if (repLevel == RepLevel.NEUTRAL) weight = 1f;
                else if (repLevel == RepLevel.SUSPICIOUS) weight = 1.5f;
                else if (repLevel == RepLevel.INHOSPITABLE) weight = 2f;
                else if (repLevel == RepLevel.HOSTILE) weight = 2.5f;
                else if (repLevel == RepLevel.VENGEFUL) weight = 3f;
                else continue;
                
                weight = weight * (1 + dominance*2);
            }
            else if (actionType == CovertActionType.DESTABILIZE_MARKET 
                    || actionType == CovertActionType.DESTROY_FOOD
                    || actionType == CovertActionType.SABOTAGE_RESERVE)
            {
                if (repLevel == RepLevel.INHOSPITABLE) weight = 1f;
                else if (repLevel == RepLevel.HOSTILE) weight = 3f;
                else if (repLevel == RepLevel.VENGEFUL) weight = 5f;
                else continue;
                
                weight = weight * (1 + dominance);
            }
            else if (actionType == CovertActionType.INSTIGATE_REBELLION)
            {
                if (repLevel == RepLevel.HOSTILE) weight = 1f;
                else if (repLevel == RepLevel.VENGEFUL) weight = 2f;
                else continue;
                
                weight = weight * (1 + dominance);
            }
            if (ExerelinUtilsFaction.isPirateFaction(factionId))
                weight *= 0.25f;    // reduces factions constantly targeting pirates for covert action
            
            if (weight <= 0) continue;
            targetFactionPicker.add(faction, weight);
            factionCount++;
        }
        
        log.info("Number of target factions: " + factionCount);
        if (factionCount < 1 || (actionType == CovertActionType.LOWER_RELATIONS && factionCount < 2)) 
            return;
        
        FactionAPI targetFaction = targetFactionPicker.pickAndRemove();
        FactionAPI thirdFaction = null;
        if (factionCount >= 2)
            thirdFaction = targetFactionPicker.pickAndRemove();

        log.info("Target faction: " + targetFaction.getDisplayName());
        
        for (MarketAPI market:markets)
        {
            if (market.getFaction() == targetFaction)
            {
                float weight = 1;
                // rebellion special handling
                if (actionType == CovertActionType.INSTIGATE_REBELLION)
                {
					if (RebellionEventCreator.getRebellionPointsStatic(market) < 75)
						continue;
					
                    if (ExerelinUtilsMarket.wasOriginalOwner(market, agentFaction.getId()))
                        weight *= 4;
                }
                marketPicker.add(market, weight);
            }
        }
        
        MarketAPI market = marketPicker.pick();
        if (market == null)
        {
            log.info("No market available");
            return;
        }
        
        // do stuff
        if (actionType == CovertActionType.RAISE_RELATIONS)
        {
            agentRaiseRelations(market, agentFaction, targetFaction, false);
        }
        else if (actionType == CovertActionType.LOWER_RELATIONS)
        {
            agentLowerRelations(market, agentFaction, targetFaction, thirdFaction, false);
        }
        else if (actionType == CovertActionType.DESTABILIZE_MARKET)
        {
            agentDestabilizeMarket(market, agentFaction, targetFaction, false);
        }
        else if (actionType == CovertActionType.SABOTAGE_RESERVE)
        {
            saboteurSabotageReserve(market, agentFaction, targetFaction, false);
        }
        else if (actionType == CovertActionType.DESTROY_FOOD)
        {
            saboteurDestroyFood(market, agentFaction, targetFaction, false);
        }
        else if (actionType == CovertActionType.INSTIGATE_REBELLION)
        {
            instigateRebellion(market, agentFaction, targetFaction, false);
        }
    }

    @Override
    public void advance(float amount)
    {
        ExerelinUtils.advanceIntervalDays(intervalUtil, amount);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        handleNpcCovertActions();
        
        interval = getCovertWarfareInterval();
        intervalUtil.setInterval(interval * 0.75f, interval * 1.25f);
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
    
    public float getCovertWarfareInterval()
    {
        int numFactions = SectorManager.getLiveFactionIdsCopy().size() - 2;
        if (numFactions < 0) numFactions = 0;
        return baseInterval * (float)Math.pow(0.95, numFactions);
    }
    
    public static void modifyAlertLevel(MarketAPI market, float amount)
    {
        SectorAPI sector = Global.getSector();
        CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "exerelin_security_alert");
        if (eventSuper == null) 
            eventSuper = sector.getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_security_alert", null);
        SecurityAlertEvent event = (SecurityAlertEvent)eventSuper;
        
        event.increaseAlertLevel(amount);
    }
    
    public static float getAlertLevel(MarketAPI market)
    {
        if (market.getId().equals("fake_market")) return 0;
        SectorAPI sector = Global.getSector();
        CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "exerelin_security_alert");
        if (eventSuper == null) 
            return 0;
        SecurityAlertEvent event = (SecurityAlertEvent)eventSuper;
        return event.getAlertLevel();
    }
        
    public static CovertActionResult agentRaiseRelations(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Agent trying to raise relations");
        return new RaiseRelations(market, agentFaction, targetFaction, playerInvolved, null).execute();
    }
    
    public static CovertActionResult agentLowerRelations(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, FactionAPI thirdFaction, boolean playerInvolved)
    {
        log.info("Agent trying to lower relations");
        Map<String, Object> params = new HashMap<>();
        params.put("thirdFaction", thirdFaction);
        return new LowerRelations(market, agentFaction, targetFaction, playerInvolved, params).execute();
    }
    
    public static CovertActionResult agentDestabilizeMarket(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Agent trying to destablize market");
        return new DestabilizeMarket(market, agentFaction, targetFaction, playerInvolved, null).execute();
    }
    
    public static CovertActionResult saboteurSabotageReserve(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Saboteur attacking reserve fleet");
        return new SabotageReserve(market, agentFaction, targetFaction, playerInvolved, null).execute();
    }    
    
    public static CovertActionResult saboteurDestroyFood(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Saboteur destroying food");
        return new DestroyFood(market, agentFaction, targetFaction, playerInvolved, null).execute();
    }
    
    public static CovertActionResult instigateRebellion(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Instigating rebellion");
        return new InstigateRebellion(market, agentFaction, targetFaction, playerInvolved, null).execute();
    }
    
    // TODO
    public static void checkForWarmongerPenalty()
    {
        
    }
    
    public static CovertOpsManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        covertWarfareManager = (CovertOpsManager)data.get(MANAGER_MAP_KEY);
        if (covertWarfareManager != null)
            return covertWarfareManager;
        
        covertWarfareManager = new CovertOpsManager();
        data.put(MANAGER_MAP_KEY, covertWarfareManager);
        return covertWarfareManager;
    }
    
    public static enum CovertActionResult
    {
        SUCCESS, SUCCESS_DETECTED, FAILURE, FAILURE_DETECTED;
        
        public boolean isSucessful()
        {
            return this == SUCCESS || this == SUCCESS_DETECTED;
        }
        
        public boolean isDetected()
        {
            return this == SUCCESS_DETECTED || this == FAILURE_DETECTED;
        }
    }
    
}
