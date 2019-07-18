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
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles some agent-related stuff
 */
public class CovertOpsManager extends BaseCampaignEventListener implements EveryFrameScript {
    
    public static class CovertActionType {
        public static final String TRAVEL = "travel";
        public static final String RAISE_RELATIONS = "raiseRelations";
        public static final String LOWER_RELATIONS = "lowerRelations";
        public static final String DESTABILIZE_MARKET = "destabilizeMarket";
        public static final String SABOTAGE_INDUSTRY = "sabotageIndustry";
        public static final String DESTROY_COMMODITY_STOCKS = "destroyCommodities";
        public static final String INSTIGATE_REBELLION = "instigateRebellion";
        public static final String INFILTRATE_CELL = "infiltrateCell";
    }
    
    public static Logger log = Global.getLogger(CovertOpsManager.class);
    
    public static final String MANAGER_MAP_KEY = "exerelin_covertWarfareManager";
    public static final String CONFIG_FILE = "data/config/exerelin/agentConfig.json";
    public static final boolean DEBUG_MODE = false;
    
    public static final float NPC_EFFECT_MULT = 1f;
    public static final List<String> DISALLOWED_FACTIONS;
     
    public static final List<CovertActionDef> actionDefs = new ArrayList<>();
    public static final Map<String, CovertActionDef> actionDefsById = new HashMap<>();
    public static final Map<String, Float> industrySuccessMods = new HashMap<>();
    public static final Map<String, Float> industryDetectionMods = new HashMap<>();
    
    protected Set<AgentIntel> agents = new HashSet<>();
    
    protected static float baseInterval = 45f;
    protected float interval = baseInterval;
    protected final IntervalUtil intervalUtil;
    protected Random random = new Random();
    
    protected Map<MarketAPI, MutableStat> marketSuccessMods = new HashMap<>();
    protected Map<MarketAPI, MutableStat> marketDetectionMods = new HashMap<>();
    
    static {
        String[] factions = {Factions.NEUTRAL, Factions.PLAYER, Factions.INDEPENDENT};    //{"templars", "independent"};
        DISALLOWED_FACTIONS = Arrays.asList(factions);
        
        try {
            loadSettings();
        } catch (IOException | JSONException | NullPointerException ex) {
            //log.error(ex);
            throw new RuntimeException(ex);
        }
    }
    
    private static void loadSettings() throws IOException, JSONException {
        JSONObject configJson = Global.getSettings().getMergedJSONForMod(CONFIG_FILE, ExerelinConstants.MOD_ID);
                
        //config = ExerelinUtils.jsonToMap(configJson);
        baseInterval = (float)configJson.optDouble("eventFrequency", 45f);
		JSONObject actionsJson = configJson.getJSONObject("actions");
		Iterator<String> keys = actionsJson.sortedKeys();
		while (keys.hasNext()) {
			String id = keys.next();
			JSONObject defJson = actionsJson.getJSONObject(id);
			
			CovertActionDef def = new CovertActionDef();
			def.id = id;
			def.name = defJson.getString("name");
			def.successChance = (float)defJson.optDouble("successChance", 0);
			def.detectionChance = (float)defJson.optDouble("detectionChance", 0);
			def.detectionChanceFail = (float)defJson.optDouble("detectionChanceFail", 0);
			def.useAlertLevel = defJson.optBoolean("useAlertLevel", true);
			def.useIndustrySecurity = defJson.optBoolean("useIndustrySecurity", true);
			def.repLossOnDetect = new Pair<>((float)defJson.optDouble("repLossOnDetectionMin", 0), 
					(float)defJson.optDouble("repLossOnDetectionMax", 0));
			def.effect = new Pair<>((float)defJson.optDouble("effectMin", 0), 
					(float)defJson.optDouble("effectMax", 0));
			def.baseCost = defJson.optInt("baseCost");
			def.costScaling = defJson.optBoolean("costScaling", true);
			def.time = (float)defJson.optDouble("time", 0);
			def.xp = defJson.optInt("xp");
			def.alertLevelIncrease = (float)defJson.optDouble("alertLevelIncrease", 0);
			
			actionDefs.add(def);
			actionDefsById.put(id, def);
		}
		
		JSONObject indSuccess = configJson.getJSONObject("industrySuccessMods");
		keys = indSuccess.sortedKeys();
		while (keys.hasNext()) {
			String industryId = keys.next();
			industrySuccessMods.put(industryId, (float)indSuccess.getDouble(industryId));
		}
		
		JSONObject indDetect = configJson.getJSONObject("industryDetectionMods");
		keys = indDetect.sortedKeys();
		while (keys.hasNext()) {
			String industryId = keys.next();
			industryDetectionMods.put(industryId, (float)indDetect.getDouble(industryId));
		}
    }
	
	public static CovertActionDef getDef(String id) {
		return actionDefsById.get(id);
	}
	
	public static float getIndustryMult(Industry ind, Map<String, Float> map) {
		if (!ind.isFunctional()) return 1;
		
		String id = ind.getId();
		if (!map.containsKey(id))
			return 1;
		
		float mod = map.get(id);
		//log.info("Getting mod for industry " + id + ": " + mod);
		for (MutableCommodityQuantity commodity : ind.getAllDemand()) {
			CommodityOnMarketAPI data = ind.getMarket().getCommodityData(commodity.getCommodityId());
			float met = (float)data.getAvailable()/(float)data.getMaxDemand();
			if (met > 1) met = 1;
			mod *= met;
			//log.info("Modifiying by demand met: " + data.getId() + ", " + met + ", " + mod);
		}
		
		return 1 + mod;
	}
	
	public static float getIndustrySuccessMult(Industry ind) {
		return getIndustryMult(ind, industrySuccessMods);
	}
	
	public static float getIndustryDetectionMult(Industry ind) {
		return getIndustryMult(ind, industryDetectionMods);
	}
	
	protected Object readResolve() {
		if (random == null)
			random = new Random();
		
		if (agents == null) {
			agents = new HashSet<>();
			for (AgentIntel agent : getAgentsStatic()) {
				addAgent(agent);
			}
		}
		updateBaseMaxAgents();
		return this;
	}
	
	public void updateBaseMaxAgents() {
		Global.getSector().getPlayerStats().getDynamic().getStat("nex_max_agents").modifyFlat("base", ExerelinConfig.maxAgents - 1);
	}

	public CovertOpsManager()
	{
		super(true);
		intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
		updateBaseMaxAgents();
	}
	
	public MutableStat getMaxAgents() {
		return Global.getSector().getPlayerStats().getDynamic().getStat("nex_max_agents");
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
            if (market.getFaction() != targetFaction)
                continue;
            if (market.isHidden()) continue;
            
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
				intel = new RaiseRelations(null, market, agentFaction, targetFaction, agentFaction, false, null);
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
				if (commodityId != null)
					intel = new DestroyCommodityStocks(null, market, commodityId, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.INSTIGATE_REBELLION:
				intel = new InstigateRebellion(null, market, agentFaction, targetFaction, false, null);
				break;
			default:
				break;
		}
		if (intel != null) {
			intel.activate();
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
			if (commodity.isPersonnel()) continue;
			if (commodity.getAvailable() < 2) continue;
			picker.add(commodity.getId(), commodity.getAvailable());
		}
		return picker.pick();
	}
	
	public static Random getRandom(MarketAPI market) {
		if (true) return new Random();
		return getManager().random;
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
		if (DEBUG_MODE) interval = 2;
        intervalUtil.setInterval(interval * 0.75f, interval * 1.25f);
    }
	
	public MutableStat getSuccessMod(MarketAPI market) {
		if (!marketSuccessMods.containsKey(market))
			marketSuccessMods.put(market, new MutableStat(0));
		return marketSuccessMods.get(market);
	}
	
	public MutableStat getDetectionMod(MarketAPI market) {
		if (!marketDetectionMods.containsKey(market))
			marketDetectionMods.put(market, new MutableStat(0));
		return marketDetectionMods.get(market);
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
	
	// Agent salaries
	@Override
	public void reportEconomyTick(int iterIndex) {
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float f = 1f / numIter;
		
		//CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
		marketsNode.name = StringHelper.getString("colonies", true);
		marketsNode.custom = MonthlyReport.OUTPOSTS;
		marketsNode.tooltipCreator = report.getMonthlyReportTooltip();
		
		FDNode agentsNode = report.getNode(marketsNode, "nex_node_id_agents");
		agentsNode.name = StringHelper.getString("nex_agents", "agents", true);
		agentsNode.custom = "node_id_nex_agents";
		agentsNode.icon = Global.getSettings().getSpriteName("income_report", "officers");
		//agentsNode.tooltipCreator = report.getMonthlyReportTooltip();	// TODO get own tooltip creator
		
		for (AgentIntel agent : getAgents()) {
			int salary = AgentIntel.getSalary(agent.getLevel());
			if (salary <= 0) continue;
			
			FDNode aNode = report.getNode(agentsNode, agent.getAgent().getId()); 
			aNode.name = agent.getAgent().getName().getFullName();
			aNode.upkeep += salary * f;
			aNode.custom = agent;
			aNode.icon = agent.getAgent().getPortraitSprite();
		}
	}
    
    public float getCovertWarfareInterval()
    {
        int numFactions = SectorManager.getLiveFactionIdsCopy().size() - 2;
        if (numFactions < 0) numFactions = 0;
        return baseInterval * (float)Math.pow(0.95, numFactions);
    }
    
    public static void modifyAlertLevel(MarketAPI market, float amount)
    {
        if (amount == 0) return;
        
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
	
	public static List<AgentIntel> getAgentsStatic() {
		List<AgentIntel> agents = new ArrayList<>();
		for (IntelInfoPlugin iip : Global.getSector().getIntelManager().getIntel(AgentIntel.class)) {
			AgentIntel intel = (AgentIntel)iip;
			if (intel.isDeadOrDismissed()) continue;
			agents.add(intel);
		}
		return agents;
	}
	
	public List<AgentIntel> getAgents() {
		return new ArrayList<>(agents);
	}
	
	public void addAgent(AgentIntel intel) {
		agents.add(intel);
	}
	
	public void removeAgent(AgentIntel intel) {
		agents.remove(intel);
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
		public boolean useAlertLevel;
		public boolean useIndustrySecurity;
		public int baseCost;
		public boolean costScaling;
		public float time;
		public int xp;
		public float alertLevelIncrease;
		public Pair<Float, Float> effect;
		public Pair<Float, Float> repLossOnDetect;
	}
    
}
