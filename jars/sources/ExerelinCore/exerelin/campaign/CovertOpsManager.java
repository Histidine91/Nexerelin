package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
import exerelin.campaign.events.AgentDestabilizeMarketEventForCondition;
import exerelin.campaign.events.SecurityAlertEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.world.ResponseFleetManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
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
    }
    
    public static Logger log = Global.getLogger(CovertOpsManager.class);
    private static CovertOpsManager covertWarfareManager;
    
    private static final String MANAGER_MAP_KEY = "exerelin_covertWarfareManager";
    private static final String CONFIG_FILE = "data/config/exerelin/agentConfig.json";
    protected static final float NPC_EFFECT_MULT = 1.5f;
	protected static final ExerelinReputationAdjustmentResult NO_EFFECT = new ExerelinReputationAdjustmentResult(0);
    private static Map<String, Object> config;
    
    private static final List<String> disallowedFactions;
    
    private static float baseInterval = 45f;
    private float interval = baseInterval;
    private final IntervalUtil intervalUtil;
    
    static {
        String[] factions = {Factions.NEUTRAL, Factions.PLAYER, Factions.INDEPENDENT};    //{"templars", "independent"};
        disallowedFactions = Arrays.asList(factions);
        
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
        
        CovertActionType actionType = actionPicker.pick();
        
        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            String factionId = faction.getId();
            if (disallowedFactions.contains(factionId)) continue;
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
            if (disallowedFactions.contains(faction.getId())) continue;
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
            if(market.getFaction() == targetFaction)
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
    }

    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        this.intervalUtil.advance(days);
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
    
    public static CovertActionResult covertActionRoll(double sChance, double sDetectChance, double fDetectChance, boolean playerInvolved)
    {
        return covertActionRoll(sChance, sDetectChance, fDetectChance, false, null, playerInvolved);
    }
    
    public static CovertActionResult covertActionRoll(double sChance, double sDetectChance, double fDetectChance, boolean useAlertLevel, MarketAPI market, boolean playerInvolved)
    {
        CovertActionResult result = null;
        
        if (useAlertLevel)
        {
            sChance = sChance * (1 - getAlertLevel(market));
        }
        
        if (playerInvolved)
        {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (!playerFleet.isTransponderOn())
            {
                sDetectChance *= 0.5f;
                fDetectChance *= 0.75f;
            }
        }
            
        if (Math.random() < sChance)
        {
            result = CovertActionResult.SUCCESS;
            if (Math.random() < sDetectChance) result = CovertActionResult.SUCCESS_DETECTED;
        }
        else
        {
            result = CovertActionResult.FAILURE;
            if (Math.random() < fDetectChance) result = CovertActionResult.FAILURE_DETECTED;
        }
        return result;
    }
    
    public static Map<String, Object> makeEventParams(FactionAPI agentFaction, CovertActionResult result, ExerelinReputationAdjustmentResult repResult, boolean playerInvolved)
    {
        HashMap<String, Object> params = new HashMap<>();
        params.put("agentFaction", agentFaction);
		params.put("result", result);
        params.put("playerInvolved", playerInvolved);
        params.put("repEffect", repResult.delta);
		params.put("repResult", repResult);
        return params;
    }
    
    public static CovertActionResult agentRaiseRelations(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Agent trying to raise relations");
        CovertActionResult result = covertActionRoll((double)config.get("agentRaiseRelationsSuccessChance"), 0, (double)config.get("agentRaiseRelationsDetectionChanceFail"), playerInvolved);
        if (result.isSucessful())
        {
            float effectMin = (float)(double)config.get("agentRaiseRelationsEffectMin");
            float effectMax = (float)(double)config.get("agentRaiseRelationsEffectMax");
            float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
            if (!playerInvolved) effect *= NPC_EFFECT_MULT;
            ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, effect, null, null, null);
            
            if (Math.abs(repResult.delta) >= 0.01f || playerInvolved)
            {
                Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_raise_relations", params);
            }
        }
        else
        {
            if (result.isDetected())
            {
                // cover blown, piss them off
                float effectMin = (float)(double)config.get("agentRaiseRelationsRepLossOnDetectionMin");
                float effectMax = (float)(double)config.get("agentRaiseRelationsRepLossOnDetectionMax");
                float effect = -MathUtils.getRandomNumberInRange(effectMin, effectMax);
                if (!playerInvolved) effect *= NPC_EFFECT_MULT;
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, effect, RepLevel.FAVORABLE, null, RepLevel.INHOSPITABLE);
                
                if (Math.abs(repResult.delta) >= 0.01f || playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_raise_relations", params);
                }
            }
            else    // failed but undetected
            {
                if (playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_raise_relations", params);
                }
            }
        }
        return result;
    }
    
    public static CovertActionResult agentLowerRelations(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, FactionAPI thirdFaction, boolean playerInvolved)
    {
        log.info("Agent trying to lower relations");
        CovertActionResult result = covertActionRoll((double)config.get("agentLowerRelationsSuccessChance"), 0, (double)config.get("agentLowerRelationsDetectionChanceFail"), playerInvolved);
        if (result.isSucessful())
        {
            float effectMin = (float)(double)config.get("agentLowerRelationsEffectMin");
            float effectMax = (float)(double)config.get("agentLowerRelationsEffectMax");
            float effect = -MathUtils.getRandomNumberInRange(effectMin, effectMax);
            if (!playerInvolved) effect *= NPC_EFFECT_MULT;
            ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(thirdFaction, targetFaction, effect, null, null, RepLevel.HOSTILE);
            
            if (Math.abs(repResult.delta) >= 0.01f || playerInvolved)
            {
                Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                params.put("thirdFaction", thirdFaction);
                params.put("repEffect2", effect);
                Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_lower_relations", params);
            }
        }
        else
        {
            if (result.isDetected())
            {
                float effectMin = (float)(double)config.get("agentLowerRelationsRepLossOnDetectionMin");
                float effectMax = (float)(double)config.get("agentLowerRelationsRepLossOnDetectionMax");
                float effect = -MathUtils.getRandomNumberInRange(effectMin, effectMax);
                if (!playerInvolved) effect *= NPC_EFFECT_MULT;
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, effect, RepLevel.NEUTRAL, null, RepLevel.HOSTILE);
                ExerelinReputationAdjustmentResult repResult2 = DiplomacyManager.adjustRelations(agentFaction, thirdFaction, effect, RepLevel.NEUTRAL, null, RepLevel.HOSTILE);
                
                if (Math.abs(repResult.delta) >= 0.01f || Math.abs(repResult2.delta) >= 0.01f || playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                    params.put("thirdFaction", thirdFaction);
					params.put("repResult2", repResult2);
                    params.put("repEffect2", repResult2.delta);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_lower_relations", params);
                }
            }
            else    // failed but undetected
            {
                if (playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
                    params.put("thirdFaction", thirdFaction);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_lower_relations", params);
                }
            }
        }
        return result;
    }
    
    public static CovertActionResult agentDestabilizeMarket(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Agent trying to destablize market");
        CovertActionResult result = covertActionRoll((double)config.get("agentDestabilizeSuccessChance"), (double)config.get("agentDestabilizeDetectionChance"),
                (double)config.get("agentDestabilizeDetectionChanceFail"), true, market, playerInvolved);
        
        if (result.isSucessful())
        {
            SectorAPI sector = Global.getSector();
            CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market_for_condition");
            if (eventSuper == null) 
                eventSuper = sector.getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market_for_condition", null);
            AgentDestabilizeMarketEventForCondition event = (AgentDestabilizeMarketEventForCondition)eventSuper;

            int currentPenalty = event.getStabilityPenalty();
            int delta = 1;
            if (currentPenalty < 2) delta = 2;
            if (!playerInvolved) delta = Math.round(delta * NPC_EFFECT_MULT);
            event.increaseStabilityPenalty(delta);
            
            Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
            params.put("stabilityPenalty", delta);
            
            // detected after successful attack?
            if (result.isDetected())
            {
                float repMin = (float)(double)config.get("agentDestabilizeRepLossOnDetectionMin");
                float repMax = (float)(double)config.get("agentDestabilizeRepLossOnDetectionMax");
                float rep = -MathUtils.getRandomNumberInRange(repMin, repMax);
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, rep, RepLevel.INHOSPITABLE, null, null);
				params.put("repResult", repResult);
                params.put("repEffect", repResult.delta);
                params.put("stage", result);
            }
            
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market", params);
        }
        else
        {
            if (result.isDetected())
            {
                float repMin = (float)(double)config.get("agentDestabilizeRepLossOnDetectionMin");
                float repMax = (float)(double)config.get("agentDestabilizeRepLossOnDetectionMax");
                float rep = -MathUtils.getRandomNumberInRange(repMin, repMax);
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, rep, RepLevel.INHOSPITABLE, null, RepLevel.HOSTILE);
                if (Math.abs(repResult.delta) >= 0.01f || playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market", params);
                }
            }
            else    // failed but undetected
            {
                if (playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market", params);
                }
            }
        }
        modifyAlertLevel(market, (float)(double)config.get("agentDestabilizeSecurityLevelRise"));
        return result;
    }
    
    public static CovertActionResult saboteurSabotageReserve(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Saboteur attacking reserve fleet");
        CovertActionResult result = covertActionRoll((double)config.get("sabotageReserveSuccessChance"), (double)config.get("sabotageReserveDetectionChance"),
                (double)config.get("sabotageReserveDetectionChanceFail"), true, market, playerInvolved);
        if (result.isSucessful())
        {
            SectorAPI sector = Global.getSector();
            float effectMin = (float)(double)config.get("sabotageReserveEffectMin");
            float effectMax = (float)(double)config.get("sabotageReserveEffectMax");
            float effect = -MathUtils.getRandomNumberInRange(effectMin, effectMax);
            if (!playerInvolved) effect *= NPC_EFFECT_MULT;
            
            float delta = ResponseFleetManager.modifyReserveSize(market, effect);
            
            Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
            params.put("reserveDamage", -delta);
            
            // detected after successful attack?
            if (Math.random() <= (double)config.get("sabotageReserveDetectionChance") )
            {
                float repMin = (float)(double)config.get("sabotageReserveRepLossOnDetectionMin");
                float repMax = (float)(double)config.get("sabotageReserveRepLossOnDetectionMax");
                float rep = -MathUtils.getRandomNumberInRange(repMin, repMax);
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, rep, RepLevel.INHOSPITABLE, null, null);
				params.put("repResult", repResult);
                params.put("repEffect", repResult.delta);
                params.put("stage", result);
            }
            
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_saboteur_sabotage_reserve", params);
        }
        else
        {
            if (result.isDetected())
            {
                float repMin = (float)(double)config.get("sabotageReserveRepLossOnDetectionMin");
                float repMax = (float)(double)config.get("sabotageReserveRepLossOnDetectionMax");
                float rep = -MathUtils.getRandomNumberInRange(repMin, repMax);
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, rep, RepLevel.INHOSPITABLE, null, RepLevel.HOSTILE);
                if (Math.abs(repResult.delta) >= 0.01f || playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_saboteur_sabotage_reserve", params);
                }
            }
            else    // failed but undetected
            {
                if (playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_saboteur_sabotage_reserve", params);
                }
            }
        }
        modifyAlertLevel(market, (float)(double)config.get("sabotageReserveSecurityLevelRise"));
        return result;
    }    
    
    public static CovertActionResult saboteurDestroyFood(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved)
    {
        log.info("Saboteur destroying food");
        CovertActionResult result = covertActionRoll((double)config.get("sabotageDestroyFoodSuccessChance"), (double)config.get("sabotageDestroyFoodDetectionChance"),
                (double)config.get("sabotageDestroyFoodDetectionChanceFail"), true, market, playerInvolved);
        if (result.isSucessful())
        {
            float effectMin = (float)(double)config.get("sabotageDestroyFoodEffectMin");
            float effectMax = (float)(double)config.get("sabotageDestroyFoodEffectMax");
            float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
            if (!playerInvolved) effect *= NPC_EFFECT_MULT;
            
            float foodDestroyed = (float)Math.pow(market.getSize(), 3) * effect;
            
            CommodityOnMarketAPI food = market.getCommodityData(Commodities.FOOD);
            float before = food.getStockpile();
            food.removeFromAverageStockpile(foodDestroyed);
            food.removeFromStockpile(foodDestroyed);
            float after = food.getStockpile();
            log.info("Remaining food: " + food.getStockpile() + ", " + food.getAverageStockpile());
            
            Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
            params.put("foodDestroyed", before - after);
            
            // detected after successful attack?
            if (result.isDetected())
            {
                float repMin = (float)(double)config.get("sabotageDestroyFoodRepLossOnDetectionMin");
                float repMax = (float)(double)config.get("sabotageDestroyFoodRepLossOnDetectionMax");
                float rep = -MathUtils.getRandomNumberInRange(repMin, repMax);
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, rep, RepLevel.INHOSPITABLE, null, null);
				params.put("repResult", repResult);
                params.put("repEffect", repResult.delta);
                params.put("stage", result);
            }
            
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_saboteur_destroy_food", params);
        }
        else
        {
            if (result.isDetected())
            {
                float repMin = (float)(double)config.get("sabotageDestroyFoodRepLossOnDetectionMin");
                float repMax = (float)(double)config.get("sabotageDestroyFoodRepLossOnDetectionMax");
                float rep = -MathUtils.getRandomNumberInRange(repMin, repMax);
                ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(agentFaction, targetFaction, rep, RepLevel.INHOSPITABLE, null, RepLevel.HOSTILE);
                if (Math.abs(repResult.delta) >= 0.01f || playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, repResult, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_saboteur_destroy_food", params);
                }
            }
            else    // failed but undetected
            {
                if (playerInvolved)
                {
                    Map<String, Object> params = makeEventParams(agentFaction, result, NO_EFFECT, playerInvolved);
                    Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_saboteur_destroy_food", params);
                }
            }
        }
        modifyAlertLevel(market, (float)(double)config.get("sabotageDestroyFoodSecurityLevelRise"));
        return result;
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
