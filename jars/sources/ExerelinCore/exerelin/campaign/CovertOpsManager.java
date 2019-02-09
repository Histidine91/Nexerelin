package exerelin.campaign;

import exerelin.campaign.intel.agents.LowerRelations;
import exerelin.campaign.intel.agents.InstigateRebellion;
import exerelin.campaign.intel.agents.SabotageIndustry;
import exerelin.campaign.intel.agents.DestabilizeMarket;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.campaign.intel.agents.DestroyCommodityStocks;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class CovertOpsManager extends BaseCampaignEventListener implements EveryFrameScript {
    
    public static class CovertActionType {
        public static final String RAISE_RELATIONS = "raiseRelations";
        public static final String LOWER_RELATIONS = "lowerRelations";
        public static final String DESTABILIZE_MARKET = "destabilizeMarket";
        public static final String SABOTAGE_INDUSTRY = "sabotageIndustry";
        public static final String DESTROY_COMMODITY_STOCKS = "destroyStocks";
        public static final String INSTIGATE_REBELLION = "instigateRebellion";
    }
    
    public static Logger log = Global.getLogger(CovertOpsManager.class);
    
    private static final String MANAGER_MAP_KEY = "exerelin_covertWarfareManager";
    private static final String CONFIG_FILE = "data/config/exerelin/agentConfig.json";
    @Deprecated public static final float NPC_EFFECT_MULT = 1f;
    public static final List<String> DISALLOWED_FACTIONS;
     
    public static final List<CovertActionDef> actionDefs = new ArrayList<>();
	public static final Map<String, CovertActionDef> actionDefsById = new HashMap<>();
      
    protected static float baseInterval = 45f;
    protected float interval = baseInterval;
    protected final IntervalUtil intervalUtil;
    
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
        JSONObject configJson = Global.getSettings().getMergedJSONForMod(CONFIG_FILE, ExerelinConstants.MOD_ID);
                
        //config = ExerelinUtils.jsonToMap(configJson);
        //baseInterval = (float)(double)config.get("eventFrequency");   // ClassCastException
        baseInterval = 1;	//(float)configJson.optDouble("eventFrequency", 15f);
		JSONObject actionsJson = configJson.getJSONObject("actions");
		Iterator<String> keys = actionsJson.sortedKeys();
		while (keys.hasNext()) {
			String id = keys.next();
			JSONObject defJson = actionsJson.getJSONObject(id);
			
			CovertActionDef def = new CovertActionDef();
			def.id = id;
			def.name = defJson.getString("name");
			def.successChance = (float)defJson.optDouble("successChance");
			def.detectionChance = (float)defJson.optDouble("detectionChance");
			def.detectionChanceFail = (float)defJson.optDouble("detectionChanceFail");
			def.repLossOnDetect = new Pair<>((float)defJson.optDouble("repLossOnDetectionMin"), 
					(float)defJson.optDouble("repLossOnDetectionMax"));
			def.effect = new Pair<>((float)defJson.optDouble("effectMin"), 
					(float)defJson.optDouble("effectMax"));
			def.baseCost = (float)defJson.optDouble("baseCost");
			def.costScaling = defJson.optBoolean("costScaling", true);
			def.xp = defJson.optInt("xp");
			def.alertLevelIncrease = (float)defJson.optDouble("alertLevelIncrease");
			
			actionDefs.add(def);
			actionDefsById.put(id, def);
		}
    }
    
	@Deprecated
    public static Map<String, Object> getConfig() {
        return null;
    }
	
	public static CovertActionDef getDef(String id) {
		return actionDefsById.get(id);
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
        WeightedRandomPicker<String> actionPicker = new WeightedRandomPicker();
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy())
            factions.add(sector.getFaction(factionId));
        
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();

        actionPicker.add(CovertActionType.RAISE_RELATIONS, 1.2f);
        actionPicker.add(CovertActionType.LOWER_RELATIONS, 1.5f);
        actionPicker.add(CovertActionType.DESTABILIZE_MARKET, 1.25f);
        actionPicker.add(CovertActionType.SABOTAGE_INDUSTRY, 1.25f);
        actionPicker.add(CovertActionType.DESTROY_COMMODITY_STOCKS, 1.25f);
        //actionPicker.add(CovertActionType.INSTIGATE_REBELLION, 0.25f);
        
        String actionType = actionPicker.pick();
        
        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            String factionId = faction.getId();
            if (DISALLOWED_FACTIONS.contains(factionId)) continue;
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;  // pirates don't do covert warfare
            if (!ExerelinConfig.followersAgents && Nex_IsFactionRuler.isRuler(faction.getId()) ) continue;
            // don't use player faction as potential source if player is affiliated with another faction
            if (faction.getId().equals(Factions.PLAYER) && 
                    !faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(factionId);
            if (factionConf != null && !factionConf.allowAgentActions) continue;
            
            agentFactionPicker.add(faction);
            factionCount++;
        }
        if (factionCount < 2) return;
        
        FactionAPI agentFaction = agentFactionPicker.pick();
        log.info("\tTrying action: " + actionType);
        
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
            if (actionType.equals(CovertActionType.RAISE_RELATIONS))
            {
                if (repLevel == RepLevel.FAVORABLE || repLevel == RepLevel.WELCOMING) weight = 1f;
                else if (repLevel == RepLevel.NEUTRAL) weight = 1.5f;
                else if (repLevel == RepLevel.SUSPICIOUS) weight = 2f;
                else if (repLevel == RepLevel.INHOSPITABLE) weight = 3f;
                else continue;
                
                weight *= (1.25f - dominance);
            }
            else if (actionType.equals(CovertActionType.LOWER_RELATIONS))
            {
                if (repLevel == RepLevel.FAVORABLE) weight = 0.25f;
                else if (repLevel == RepLevel.NEUTRAL) weight = 1f;
                else if (repLevel == RepLevel.SUSPICIOUS) weight = 1.5f;
                else if (repLevel == RepLevel.INHOSPITABLE) weight = 2f;
                else if (repLevel == RepLevel.HOSTILE) weight = 2.5f;
                else if (repLevel == RepLevel.VENGEFUL) weight = 3f;
                else continue;
                
                weight *= (1 + dominance*2);
            }
            else if (actionType.equals(CovertActionType.DESTABILIZE_MARKET)
                    || actionType.equals(CovertActionType.DESTROY_COMMODITY_STOCKS)
                    || actionType.equals(CovertActionType.SABOTAGE_INDUSTRY))
            {
                if (repLevel == RepLevel.INHOSPITABLE) weight = 1f;
                else if (repLevel == RepLevel.HOSTILE) weight = 3f;
                else if (repLevel == RepLevel.VENGEFUL) weight = 5f;
                else continue;
                
                weight *= (1 + dominance);
            }
            else if (actionType.equals(CovertActionType.INSTIGATE_REBELLION))
            {
                if (repLevel == RepLevel.INHOSPITABLE) weight = 1;
                else if (repLevel == RepLevel.HOSTILE) weight = 3;
                else if (repLevel == RepLevel.VENGEFUL) weight = 5;
                else continue;
                
                weight *= (1 + dominance);
            }
            if (ExerelinUtilsFaction.isPirateFaction(factionId))
                weight *= 0.25f;    // reduces factions constantly targeting pirates for covert action
            
            if (weight <= 0) continue;
            targetFactionPicker.add(faction, weight);
            factionCount++;
        }
        
        log.info("\tNumber of target factions: " + factionCount);
        if (factionCount < 1 || (actionType.equals(CovertActionType.LOWER_RELATIONS) && factionCount < 2)) 
            return;
        
        FactionAPI targetFaction = targetFactionPicker.pickAndRemove();
        FactionAPI thirdFaction = null;
        if (factionCount >= 2)
            thirdFaction = targetFactionPicker.pickAndRemove();

        log.info("\tTarget faction: " + targetFaction.getDisplayName());
        
        for (MarketAPI market:markets)
        {
            if (market.getFaction() == targetFaction)
            {
                float weight = 1;
                // rebellion special handling
                if (actionType.equals(CovertActionType.INSTIGATE_REBELLION))
                {
                    if (RebellionEventCreator.getRebellionPointsStatic(market) < 50)
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
            log.info("\tNo market available");
            return;
        }
        
        // do stuff
		log.info("\tLaunching covert action: " + actionType);
		CovertActionIntel intel = null;
		switch (actionType) {
			case CovertActionType.RAISE_RELATIONS:
				intel = new RaiseRelations(null, market, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.LOWER_RELATIONS:
				Map<String, Object> params = new HashMap<>();
				//params.put("thirdFaction", thirdFaction);
				intel = new LowerRelations(null, market, agentFaction, targetFaction, thirdFaction, false, params);
				break;
			case CovertActionType.DESTABILIZE_MARKET:
				intel = new DestabilizeMarket(null, market, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				Industry target = pickIndustryToSabotage(market);
				if (target != null)
					intel = new SabotageIndustry(null, market, target, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				String commodityId = pickCommodityToDestroy(market);
				intel = new DestroyCommodityStocks(null, market, commodityId, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.INSTIGATE_REBELLION:
				intel = new InstigateRebellion(null, market, agentFaction, targetFaction, false, null);
				break;
			default:
				break;
		}
		if (intel != null) {
			intel.init();
			intel.execute();
		}
    }
	
	public static Industry pickIndustryToSabotage(MarketAPI market) {
		WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
		for (Industry ind : market.getIndustries()) {
			if (!ind.canBeDisrupted()) continue;
			if (ind.getSpec().hasTag(Industries.TAG_STATION))
				continue;
			picker.add(ind, 1);
		}
		return picker.pick();
	}
	
	public static String pickCommodityToDestroy(MarketAPI market) {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (CommodityOnMarketAPI commodity : market.getCommoditiesCopy()) {
			if (commodity.isNonEcon() || commodity.isIllegal()) continue;
			picker.add(commodity.getId(), commodity.getAvailable());
		}
		return picker.pick();
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
        CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "nex_security_alert");
        if (eventSuper == null) 
            eventSuper = sector.getEventManager().startEvent(new CampaignEventTarget(market), "nex_security_alert", null);
        SecurityAlertEvent event = (SecurityAlertEvent)eventSuper;
        
        event.increaseAlertLevel(amount);
    }
    
    public static float getAlertLevel(MarketAPI market)
    {
        if (market.getId().equals("fake_market")) return 0;
        SectorAPI sector = Global.getSector();
        CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "nex_security_alert");
        if (eventSuper == null) 
            return 0;
        SecurityAlertEvent event = (SecurityAlertEvent)eventSuper;
        return event.getAlertLevel();
    }
    
    // TODO
    public static void checkForWarmongerPenalty()
    {
        
    }
    
    public static CovertOpsManager getManager()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        CovertOpsManager manager = (CovertOpsManager)data.get(MANAGER_MAP_KEY);
        return manager;
    }
	
	public static CovertOpsManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        CovertOpsManager manager = new CovertOpsManager();
		data.put(MANAGER_MAP_KEY, manager);
        return manager;
    }
    
    public static enum CovertActionResult
    {
        SUCCESS, SUCCESS_DETECTED, FAILURE, FAILURE_DETECTED;
        
        public boolean isSucessful() {
            return this == SUCCESS || this == SUCCESS_DETECTED;
        }
        
        public boolean isDetected() {
            return this == SUCCESS_DETECTED || this == FAILURE_DETECTED;
        }
    }
	
	public static class CovertActionDef {
		public String id;
		public String name;
		public float successChance;
		public float detectionChance;
		public float detectionChanceFail;
		public float baseCost;
		public boolean costScaling;
		public int xp;
		public float alertLevelIncrease;
		public Pair<Float, Float> effect;
		public Pair<Float, Float> repLossOnDetect;
	}
    
}
