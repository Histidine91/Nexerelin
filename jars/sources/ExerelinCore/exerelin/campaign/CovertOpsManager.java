package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.magiclib.util.MagicSettings;
import exerelin.ExerelinConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.econ.EconomyInfoHelper.ProducerEntry;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.agents.*;
import exerelin.campaign.intel.agents.AgentIntel.Specialization;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

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
        public static final String PROCURE_SHIP = "procureShip";
        public static final String FIND_PIRATE_BASE = "findPirateBase";
		public static final String RECRUIT_AGENT = "recruitAgent";
    }
    
    public static Logger log = Global.getLogger(CovertOpsManager.class);
    
    public static final String MANAGER_MAP_KEY = "exerelin_covertWarfareManager";
    public static final String CONFIG_FILE = "data/config/exerelin/agentConfig.json";
    public static final String CONFIG_FILE_STEALSHIP = "data/config/exerelin/agent_steal_ship_config.csv";
    public static final boolean DEBUG_MODE = false;
    
    public static final float NPC_EFFECT_MULT = 1f;
    public static final Set<String> DISALLOWED_FACTIONS;
     
    public static final List<CovertActionDef> actionDefs = new ArrayList<>();
    public static final Map<String, CovertActionDef> actionDefsById = new HashMap<>();
    public static final Map<String, Float> industrySuccessMods = new HashMap<>();
    public static final Map<String, Float> industryDetectionMods = new HashMap<>();
    public static final Map<String, Float> stealShipCostMults = new HashMap<>();
    
    protected static float baseInjuryChance, baseInjuryChanceFailed;
    
    protected Set<AgentIntel> agents = new HashSet<>();
    
    protected static float baseInterval;
    protected float interval;
    protected final IntervalUtil intervalUtil;
    protected Random random = new Random();
    
    protected Map<MarketAPI, MutableStat> marketSuccessMods = new HashMap<>();
    protected Map<MarketAPI, MutableStat> marketDetectionMods = new HashMap<>();
    protected Set<CovertActionIntel> ongoingActions = new LinkedHashSet<>();
    
    static {
        String[] factions = {Factions.NEUTRAL, Factions.PLAYER, Factions.INDEPENDENT};    //{"templars", "independent"};
        DISALLOWED_FACTIONS = new HashSet<>(Arrays.asList(factions));
        
        for (FactionAPI faction: Global.getSector().getAllFactions())
        {
            String factionId = faction.getId();
            NexFactionConfig factionConf = NexConfig.getFactionConfig(factionId);
            if (!factionConf.allowAgentActions) 
                DISALLOWED_FACTIONS.add(factionId);
            if (NexUtilsFaction.isPirateFaction(factionId)) // pirates aren't targeted for covert warfare
                DISALLOWED_FACTIONS.add(factionId);
        }
        
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
		baseInjuryChance = (float)configJson.optDouble("baseInjuryChance", 0);
		baseInjuryChanceFailed = (float)configJson.optDouble("baseInjuryChanceDetected", 0);
		
		JSONObject actionsJson = configJson.getJSONObject("actions");
		Iterator<String> keys = actionsJson.keys();
		while (keys.hasNext()) {
			String id = keys.next();
			JSONObject defJson = actionsJson.getJSONObject(id);
			
			CovertActionDef def = new CovertActionDef();
			def.id = id;
			def.name = defJson.getString("name");
			def.nameForSub = defJson.optString("nameForSub", def.name);
			def.className = defJson.getString("className");
			def.successChance = (float)defJson.optDouble("successChance", 0);
			def.detectionChance = (float)defJson.optDouble("detectionChance", 0);
			def.detectionChanceFail = (float)defJson.optDouble("detectionChanceFail", 0);
			def.injuryChanceMult = (float)defJson.optDouble("injuryChanceMult", 1);
			def.useAlertLevel = defJson.optBoolean("useAlertLevel", true);
			def.useIndustrySecurity = defJson.optBoolean("useIndustrySecurity", true);
			def.repLossOnDetect = new Pair<>((float)defJson.optDouble("repLossOnDetectionMin", 0), 
					(float)defJson.optDouble("repLossOnDetectionMax", 0));
			def.effect = new Pair<>((float)defJson.optDouble("effectMin", 0), 
					(float)defJson.optDouble("effectMax", 0));
			def.baseCost = (float)defJson.optDouble("baseCost");
			def.costScaling = defJson.optBoolean("costScaling", true);
			def.time = (float)defJson.optDouble("time", 0);
			def.xp = defJson.optInt("xp");
			def.alertLevelIncrease = (float)defJson.optDouble("alertLevelIncrease", 0);
			def.listInIntel = defJson.optBoolean("listInIntel", true);
			def.sortOrder = (float)defJson.optDouble("sortOrder", 1000);
			if (defJson.has("specializations")) {
				def.addSpecializations(NexUtils.JSONArrayToArrayList(defJson.getJSONArray("specializations")));
			}			
			
			actionDefs.add(def);
			actionDefsById.put(id, def);
		}

		Collections.sort(actionDefs);
		
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
		
		// load ship stealing config
		// legacy CSV method
		JSONArray stealShipJson = Global.getSettings().getMergedSpreadsheetDataForMod("hull id", 
				CONFIG_FILE_STEALSHIP, ExerelinConstants.MOD_ID);
		for (int i=0; i<stealShipJson.length(); i++) {
			JSONObject row = stealShipJson.getJSONObject(i);
			String hullId = row.getString("hull id");
			if (hullId == null || hullId.isEmpty()) continue;
			float costMult = (float)row.getDouble("cost mult");
			stealShipCostMults.put(hullId, costMult);
		}
		// modconfig method
		Map<String, Float> stealMults = MagicSettings.getFloatMap(ExerelinConstants.MOD_ID, "agent_steal_ship_mults");
		for (String hullId : stealMults.keySet()) {
			float mult = stealMults.get(hullId);
			stealMults.put(hullId, mult);
		}
    }
	
	public static CovertActionDef getDef(String id) {
		return actionDefsById.get(id);
	}
	
	public static List<CovertActionDef> getAllowedActionsForSpecialization(Specialization spec)
	{
		List<CovertActionDef> results = new ArrayList<>();
		for (CovertActionDef action : actionDefs) {
			if (action.isCompatibleWithSpecialization(spec)) {
				//log.info(spec + " allows action " + action.name);
				results.add(action);
			}
				
		}
		return results;
	}
	
	public static List<CovertActionDef> getAllowedActionsForAgent(AgentIntel agent)
	{
		List<CovertActionDef> results = new ArrayList<>();
		for (CovertActionDef action : actionDefs) {
			if (action.canAgentExecute(agent))
				results.add(action);
		}
		return results;
	}
	
	/**
	 * Gets the multiplier for the specified industry in the specified map.
	 * @param ind
	 * @param map Map of multipliers by industry. It can contain success multipliers,
	 * detection chance multipliers, etc.
	 * @return
	 */
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
	
	public static float getBaseInjuryChance(boolean success) {
		if (success) return baseInjuryChance;
		return baseInjuryChanceFailed;
	}
	
	public static float getStealShipCostMult(String hullId) {
		Float mult = stealShipCostMults.get(hullId);
		if (mult == null) return 1f;
		return mult;
	}
	
	public static boolean isDebugMode() {
		return DEBUG_MODE;
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
		if (this.ongoingActions == null) {
			ongoingActions = new LinkedHashSet<>();
		}

		updateBaseMaxAgents();
		return this;
	}
	
	public void updateBaseMaxAgents() {
		Global.getSector().getPlayerStats().getDynamic().getStat("nex_max_agents").modifyFlat("base", NexConfig.maxAgents - 1);
	}

	public CovertOpsManager()
	{
		super(true);
		interval = getCovertWarfareInterval();
		intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
		updateBaseMaxAgents();
	}
	
	public MutableStat getMaxAgents() {
		return Global.getSector().getPlayerStats().getDynamic().getStat("nex_max_agents");
	}
	
	/**
	 * Returns a {@code WeightedRandomPicker} of potential target factions for
	 * the specified agent faction and action type. 
	 * @param actionType
	 * @param agentFaction
	 * @param factions Potential factions to be added to the picker. Which factions
	 * actually go in (and with what weight) depend on their relationship 
	 * with the agent faction vs. the action type.
	 * @return
	 */
	public WeightedRandomPicker<FactionAPI> generateTargetFactionPicker(String actionType, 
			FactionAPI agentFaction, List<FactionAPI> factions) 
	{
		WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(random);
		int factionCount = 0;
		String agentFactionId = agentFaction.getId();
		DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(agentFactionId);
				
		for (FactionAPI faction: factions)
		{
			String factionId = faction.getId();
			if (DISALLOWED_FACTIONS.contains(factionId)) continue;
			if (AllianceManager.areFactionsAllied(agentFactionId, factionId))
				continue;			
			
			RepLevel repLevel = faction.getRelationshipLevel(agentFaction);
			float dominance = DiplomacyManager.getDominanceFactor(faction.getId());
			float disposition = brain.getDisposition(factionId).disposition.getModifiedValue();
			
			float weight = 1f;
			switch (actionType) {
				case CovertActionType.RAISE_RELATIONS:
					// Use if we want to get closer to this faction
					if (disposition <= DiplomacyBrain.DISLIKE_THRESHOLD)
						continue;
					else if (disposition >= DiplomacyBrain.LIKE_THRESHOLD)
						weight *= 2;
					
					switch (repLevel) {
						case FAVORABLE:
						case WELCOMING:
							weight *= 1f;
							break;
						case NEUTRAL:
							weight *= 1.5f;
							break;
						case SUSPICIOUS:
							weight *= 2f;
							break;
						case INHOSPITABLE:
							weight *= 3f;
							break;
						default:
							continue;
					}
					
					weight *= (1.25f - dominance);
					break;

				case CovertActionType.LOWER_RELATIONS:
					switch (repLevel) {
						case FAVORABLE:
							weight = 0.25f;
							break;
						case NEUTRAL:
							weight = 1f;
							break;
						case SUSPICIOUS:
							weight = 1.5f;
							break;
						case INHOSPITABLE:
							weight = 2f;
							break;
						case HOSTILE:
							weight = 2.5f;
							break;
						case VENGEFUL:
							weight = 3f;
							break;
						default:
							continue;
					}
					// this is a bit of a hostile act, try not to do it to people we like
					if (disposition > DiplomacyBrain.LIKE_THRESHOLD)
						weight *= 0.5f;
					
					weight *= (1 + dominance*2);
					break;
				
				case CovertActionType.DESTROY_COMMODITY_STOCKS:
				case CovertActionType.SABOTAGE_INDUSTRY:
					// we shouldn't be here; these have their own handling
					break;

				case CovertActionType.DESTABILIZE_MARKET:
					switch (repLevel) {
						case INHOSPITABLE:
							weight = 1f;
							break;
						case HOSTILE:
							weight = 3f;
							break;
						case VENGEFUL:
							weight = 5f;
							break;
						default:
							continue;
					}
					weight *= (1 + dominance);
					break;

				case CovertActionType.INSTIGATE_REBELLION:
					switch (repLevel) {
						case INHOSPITABLE:
							weight = 1;
							break;
						case HOSTILE:
							weight = 3;
							break;
						case VENGEFUL:
							weight = 5;
							break;
						default:
							continue;
					}
					weight *= (1 + dominance);
					break;

				default:
					break;
			}
			if (weight <= 0) continue;
			
			// economic weighting
			if (actionType.equals(CovertActionType.DESTROY_COMMODITY_STOCKS) 
					|| actionType.equals(CovertActionType.SABOTAGE_INDUSTRY))
			{
				weight *= EconomyInfoHelper.getInstance().getCompetitionFactor(agentFaction.getId(), factionId);
			}
			
			if (NexUtilsFaction.isPirateFaction(factionId))
				weight *= 0.25f;	// reduces factions constantly targeting pirates for covert action
			
			if (weight <= 0) continue;
			picker.add(faction, weight);
			factionCount++;
		}
		
		log.info("\tNumber of target factions: " + factionCount);
		return picker;
	}
	
	// runcode exerelin.campaign.CovertOpsManager.getManager().handleNpcCovertActions();
	/**
	 * Picks a random action, agent faction and target faction(s) to execute. 
	 * Called at periodic intervals.
	 */
	public void handleNpcCovertActions()
    {
        if (Global.getSector().isInNewGameAdvance())
            return;
        
        log.info("Starting covert warfare event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> agentFactionPicker = new WeightedRandomPicker(random);
        WeightedRandomPicker<String> actionPicker = new WeightedRandomPicker(random);
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy()) {
			factions.add(sector.getFaction(factionId));
		}
            

        actionPicker.add(CovertActionType.RAISE_RELATIONS, 1.2f);
        actionPicker.add(CovertActionType.LOWER_RELATIONS, 1.5f);
        actionPicker.add(CovertActionType.DESTABILIZE_MARKET, 1.25f);
        actionPicker.add(CovertActionType.SABOTAGE_INDUSTRY, 1.25f);
        actionPicker.add(CovertActionType.DESTROY_COMMODITY_STOCKS, 1.25f);
        if (RebellionCreator.ENABLE_REBELLIONS)
            actionPicker.add(CovertActionType.INSTIGATE_REBELLION, 0.25f);
        
        String actionType = actionPicker.pick();
        
        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            String factionId = faction.getId();
            if (StrategicAI.getAI(factionId) != null) continue;	// handled there

            if (DISALLOWED_FACTIONS.contains(factionId)) continue;
            if (NexUtilsFaction.isPirateFaction(factionId)) continue;  // pirates don't do covert warfare
            if (!NexConfig.followersAgents && Nex_IsFactionRuler.isRuler(faction.getId()) ) continue;
            // don't use player faction as potential source if player is affiliated with another faction
            if (faction.getId().equals(Factions.PLAYER) && 
                    !faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            NexFactionConfig factionConf = NexConfig.getFactionConfig(factionId);
            if (factionConf != null && !factionConf.allowAgentActions) continue;
			
			float chance = 1;
			if (DiplomacyTraits.hasTrait(factionId, TraitIds.DEVIOUS))
				chance = 1.5f;
            
            agentFactionPicker.add(faction, chance);
            factionCount++;
        }
        if (factionCount < 2) return;
        
        FactionAPI agentFaction = agentFactionPicker.pick();
        log.info("\tAgent faction: " + agentFaction.getDisplayName());
        log.info("\tTrying action: " + actionType);
        
        Map<String, Object> targetData = pickTarget(agentFaction, factions, actionType);
        if (targetData == null) {
            log.info("\tFailed to find target data for covert action");
            return;
        }
        
        FactionAPI targetFaction = (FactionAPI)targetData.get("targetFaction");
        MarketAPI market = (MarketAPI)targetData.get("market");
        if (targetFaction == null || market == null) 
            return;
        
        // do stuff
		CovertActionIntel intel = null;
		switch (actionType) {
			case CovertActionType.RAISE_RELATIONS:
				intel = new RaiseRelations(null, market, agentFaction, targetFaction, agentFaction, false, null);
				break;
			case CovertActionType.LOWER_RELATIONS:
				FactionAPI thirdFaction = (FactionAPI)targetData.get("thirdFaction");
				if (thirdFaction == null) {
					log.info("\tNo third faction to lower relations with");
					return;
				}
				intel = new LowerRelations(null, market, agentFaction, targetFaction, thirdFaction, false, null);
				break;
			case CovertActionType.DESTABILIZE_MARKET:
				intel = new DestabilizeMarket(null, market, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.SABOTAGE_INDUSTRY:
				Industry target = (Industry)targetData.get("industry");
				if (target == null) {
					log.info("\tNo industry to sabotage");
					return;
				}
				intel = new SabotageIndustry(null, market, target, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.DESTROY_COMMODITY_STOCKS:
				String commodityId = (String)targetData.get("commodity");
				if (commodityId == null) {
					log.info("\tNo commodity to destroy");
					return;
				}
				intel = new DestroyCommodityStocks(null, market, commodityId, agentFaction, targetFaction, false, null);
				break;
			case CovertActionType.INSTIGATE_REBELLION:
				intel = new InstigateRebellion(null, market, agentFaction, targetFaction, false, null);
				break;
			default:
				break;
		}
		if (intel != null) {
			log.info("\tLaunching covert action: " + actionType);
			intel.activate();
			intel.execute();
		}
	}
	
	/**
	 * Picks a military structure/industry to sabotage, if we have one or more ongoing invasions or 
	 * raids against the specified faction.
	 * @param agentFaction
	 * @return
	 */
	public Industry pickMilitarySabotageTarget(FactionAPI agentFaction) 
	{
		WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>(random); 
		List<OffensiveFleetIntel> offenses = InvasionFleetManager.getManager().getActiveIntelCopy();
		
		for (OffensiveFleetIntel off : offenses)
		{
			if (off instanceof DefenseFleetIntel || off instanceof ColonyExpeditionIntel)
				continue;
			
			//if (off.getFaction() != agentFaction) continue;
			
			if (off.getTarget() == null) continue;
			FactionAPI targetFaction = off.getTarget().getFaction();
			if (!targetFaction.isHostileTo(agentFaction))
				continue;
			if (DISALLOWED_FACTIONS.contains(targetFaction.getId())) continue;
			
			float weight = off.getTarget().getSize();
			if (off instanceof RaidIntel) weight *= 0.5f;
			
			for (Industry ind : off.getTarget().getIndustries()) {
				String id = ind.getId();
				IndustrySpecAPI spec = ind.getSpec();
				if (spec.hasTag(Industries.TAG_GROUNDDEFENSES)
						//|| id.equals(Industries.PATROLHQ)
						|| spec.hasTag(Industries.TAG_MILITARY)
						|| spec.hasTag(Industries.TAG_COMMAND)
						//|| spec.hasTag(Industries.TAG_SPACEPORT)
				)
					picker.add(ind, weight);
			}
		}
		
		picker.add(null, 5);
		return picker.pick();
	}

	public ProducerEntry pickEconomicSabotageTarget(FactionAPI agentFaction) {
		return pickEconomicSabotageTarget(agentFaction, null);
	}
	
	public ProducerEntry pickEconomicSabotageTarget(FactionAPI agentFaction, @Nullable List<FactionAPI> validTargetFactions) {
		WeightedRandomPicker<ProducerEntry> picker = new WeightedRandomPicker<>(random);
		Map<String, Integer> commoditiesWeProduce = EconomyInfoHelper.getInstance()
				.getCommoditiesProducedByFaction(agentFaction.getId());
		
		boolean monopolist = DiplomacyTraits.hasTrait(agentFaction.getId(), TraitIds.MONOPOLIST);
		for (ProducerEntry prod : EconomyInfoHelper.getInstance().getCompetingProducers(agentFaction.getId(), 4, true))
		{
			if (DISALLOWED_FACTIONS.contains(prod.factionId)) continue;
			FactionAPI targetFaction = Global.getSector().getFaction(prod.factionId);
			if (validTargetFactions != null && !validTargetFactions.contains(targetFaction)) continue;
			
			float weight = 1;
			RepLevel repLevel = agentFaction.getRelationshipLevel(prod.factionId);
			switch (repLevel) {
				case NEUTRAL:
					if (monopolist) weight = 0.5f;
					else weight = 0.25f;
					break;
				case SUSPICIOUS:
					weight = 1f;
					break;
				case INHOSPITABLE:
					weight = 2f;
					break;
				case HOSTILE:
					weight = 3f;
					break;
				case VENGEFUL:
					weight = 5f;
					break;
				case FAVORABLE:
				case WELCOMING:
					if (monopolist) {
						weight = 0.25f;
						break;
					}
					else continue;
				default:
					continue;
			}
			
			weight *= prod.output;
			if (commoditiesWeProduce.containsKey(prod.commodityId))
				weight += commoditiesWeProduce.get(prod.commodityId);
			
			picker.add(prod, weight);
		}
		return picker.pick();
	}
	
	public Map<String, Object> pickTarget(FactionAPI agentFaction, List<FactionAPI> factions, 
			String actionType) {
		return pickTarget(agentFaction, factions, actionType, this.random);
	}
	
	/**
	 * Picks an appropriate target for our agent action.
	 * @param agentFaction
	 * @param factions List of valid factions to target (optional)
	 * @param actionType
	 * @param random
	 * @return A {@code HashMap} containing targeting details (faction, market, industry, etc.) as appropriate
	 */
	public Map<String, Object> pickTarget(FactionAPI agentFaction, List<FactionAPI> factions, 
			String actionType, Random random) 
	{
		Map<String, Object> result = new HashMap<>();
		boolean isDestroyStocks = actionType.equals(CovertActionType.DESTROY_COMMODITY_STOCKS);
		boolean isSabotage = actionType.equals(CovertActionType.SABOTAGE_INDUSTRY);
		
		// Sabotage industry: Try to destroy military targets to clear way for any invasions/raids we have ongoing
		if (isSabotage) {
			Industry ind = pickMilitarySabotageTarget(agentFaction);
			if (ind != null) {
				log.info("\tFound military target: " + ind.getCurrentName() 
						+ " on " + ind.getMarket().getName());
				result.put("industry", ind);
				result.put("market", ind.getMarket());
				result.put("targetFaction", ind.getMarket().getFaction());
				return result;
			}
		}
		
		// Sabotage industry or destroy commodity stocks: Attack competitors
		if (isDestroyStocks || isSabotage)
		{
			ProducerEntry target = pickEconomicSabotageTarget(agentFaction, factions);
			if (target != null) 
			{
				if (isDestroyStocks) {
					log.info("\tFound commodity to destroy: " + target.commodityId 
									+ " on " + target.market.getName());
					result.put("commodity", target.commodityId);
					result.put("market", target.market);
					result.put("targetFaction", target.market.getFaction());
					return result;
				}
				else if (isSabotage) 
				{
					return this.pickSabotageIndustryTargetFromProducerEntry(target);
				}
			}
		}
		
		// Other action: do whatever seems useful
		else
		{
			FactionAPI targetFaction = null, thirdFaction = null;
			int factionCount = 0;
			MarketAPI market;


			if (!actionType.equals(CovertActionType.INSTIGATE_REBELLION)) {
				WeightedRandomPicker<FactionAPI> targetFactionPicker = generateTargetFactionPicker(actionType, agentFaction, factions);
				factionCount = targetFactionPicker.getItems().size();
				if (factionCount < 1 || (actionType.equals(CovertActionType.LOWER_RELATIONS) && factionCount < 2))
				{
					log.info("\tFailed to find target faction(s)");
					return null;
				}

				targetFaction = targetFactionPicker.pickAndRemove();
				result.put("targetFaction", targetFaction);
				if (factionCount >= 2) {
					thirdFaction = targetFactionPicker.pickAndRemove();
					result.put("thirdFaction", thirdFaction);
				}

				log.info("\tTarget faction: " + targetFaction.getDisplayName());
				market = pickTargetMarket(agentFaction, targetFaction, actionType, random);
			}
			// don't pick a target faction for Instigate Rebellion, just iterate over all valid targets
			else {
				List<MarketAPI> validTargets = new ArrayList<>();
				for (MarketAPI candidate : Global.getSector().getEconomy().getMarketsCopy()) {
					if (factions != null && !factions.contains(candidate.getFaction())) continue;
					validTargets.add(candidate);
				}
				market = pickTargetMarket(agentFaction, null, actionType, validTargets, random);
			}

			if (market == null) {
				log.warn("\tFailed to find target market");
			}
			result.put("market", market);
			if (targetFaction == null && market != null) {
				result.put("targetFaction", market.getFaction());
			}
		}
		
		return result;
	}

	/**
	 * Picks an industry on the market specified by the {@code ProducerEntry} that we should sabotage.
	 * @param target
	 * @return
	 */
	public Map<String, Object> pickSabotageIndustryTargetFromProducerEntry(ProducerEntry target)
	{
		Map<String, Object> result = new HashMap<>();

		// kill the spaceport?
		if (NexUtilsMarket.hasWorkingSpaceport(target.market) && random.nextFloat() < 0.35f)
		{
			Industry ind = target.market.getIndustry(Industries.MEGAPORT);
			if (ind == null) ind = target.market.getIndustry(Industries.SPACEPORT);
			if (ind != null) {
				log.info("\tSabotaging spaceport on " + target.market.getName());
				result.put("industry", ind);
			}
		}
		// or find the industry producing this stuff
		else for (Industry ind : target.market.getIndustries())
		{
			if (ind.getId().equals(Industries.POPULATION))
				continue;

			MutableCommodityQuantity supply = ind.getSupply(target.commodityId);
			if (supply != null && supply.getQuantity().getModifiedInt() >= target.output)
			{
				log.info("\tFound economic target: " + ind.getCurrentName()
						+ " on " + ind.getMarket().getName());
				result.put("industry", ind);
				break;
			}
		}
		result.put("market", target.market);
		result.put("targetFaction", target.market.getFaction());
		return result;
	}

	public MarketAPI pickTargetMarket(FactionAPI agentFaction, FactionAPI targetFaction, String actionType, Random random) {
		return pickTargetMarket(agentFaction, targetFaction, actionType, null, random);
	}

	public MarketAPI pickTargetMarket(FactionAPI agentFaction, FactionAPI targetFaction, String actionType, List<MarketAPI> markets, Random random) {
		if (markets == null) markets = Global.getSector().getEconomy().getMarketsCopy();
		WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker(random);
		for (MarketAPI market: markets)
		{
			if (targetFaction != null && market.getFaction() != targetFaction)
				continue;
			if (market.isHidden()) continue;

			float weight = 1;

			// rebellion special handling
			if (actionType.equals(CovertActionType.INSTIGATE_REBELLION))
			{
				if (!canInstigateRebellion(market)) continue;
				// check relationship
				if (agentFaction.getRelationshipLevel(market.getFaction()).isAtWorst(RepLevel.NEUTRAL)) continue;

				if (NexUtilsMarket.wasOriginalOwner(market, agentFaction.getId()))
					weight *= 4;
			}
			marketPicker.add(market, weight);
		}
		return marketPicker.pick();
	}

	public void addOngoingCovertAction(CovertActionIntel intel) {
		ongoingActions.add(intel);
	}

	public List<CovertActionIntel> getOngoingCovertActionsOfType(Class toSearch) {
		List<CovertActionIntel> results = new ArrayList<>();
		for (CovertActionIntel intel : ongoingActions) {
			if (toSearch.isInstance(intel)) {
				results.add(intel);
			}
		}
		return results;
	}
	
	public static Random getRandom(MarketAPI market) {
		if (true) return new Random();
		return getManager().random;
	}

    @Override
    public void advance(float amount)
    {
		if (TutorialMissionIntel.isTutorialInProgress()) 
			return;
		
        NexUtils.advanceIntervalDays(intervalUtil, amount);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        handleNpcCovertActions();
        
        interval = getCovertWarfareInterval();
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
		if (TutorialMissionIntel.isTutorialInProgress()) return;
		
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
		agentsNode.custom = "nex_node_id_agents";
		agentsNode.icon = Global.getSettings().getSpriteName("income_report", "officers");
		agentsNode.tooltipCreator = AGENT_NODE_TOOLTIP;
		
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
        if (DEBUG_MODE) return 2;
        int numFactions = SectorManager.getLiveFactionIdsCopy().size() - 2;
        if (numFactions < 0) numFactions = 0;
        return baseInterval * (float)Math.pow(0.95, numFactions);
    }

    public static boolean canInstigateRebellion(MarketAPI market) {
		if (!NexUtilsMarket.canBeInvaded(market, false))
			return false;
		if (RebellionCreator.getInstance().getRebellionPoints(market) < 50)
			return false;
		if (market.getStabilityValue() > InstigateRebellion.MAX_STABILITY)
			return false;
		if (RebellionIntel.isOngoing(market))
			return false;
		return true;
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
	
	public static void reportAgentAction(CovertActionIntel intel)
	{
		getManager().ongoingActions.remove(intel);
		for (AgentActionListener x : Global.getSector().getListenerManager().getListeners(AgentActionListener.class)) {
			x.reportAgentAction(intel);
		}
	}
	
	public static final TooltipCreator AGENT_NODE_TOOLTIP = new TooltipCreator() {
		public boolean isTooltipExpandable(Object tooltipParam) {
			return false;
		}
		public float getTooltipWidth(Object tooltipParam) {
			return 450;
		}
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			tooltip.addPara(StringHelper.getString("nex_agents", "tooltipSalary"), 0,
				Misc.getHighlightColor(), Misc.getWithDGS(NexConfig.agentBaseSalary), 
				Misc.getWithDGS(NexConfig.agentSalaryPerLevel));
		}
	};
    
    public static enum CovertActionResult
    {
        SUCCESS, SUCCESS_DETECTED, FAILURE, FAILURE_DETECTED;
        
        public boolean isSuccessful() {
            return this == SUCCESS || this == SUCCESS_DETECTED;
        }
        
        public boolean isDetected() {
            return this == SUCCESS_DETECTED || this == FAILURE_DETECTED;
        }
    }
	
	public static class CovertActionDef implements Comparable<CovertActionDef> {
		public String id;
		public String name;
		public String nameForSub;
		public String className;
		public float successChance;
		public float detectionChance;
		public float detectionChanceFail;
		public float injuryChanceMult;
		public boolean useAlertLevel;
		public boolean useIndustrySecurity;
		public float baseCost;
		public boolean costScaling;
		public float time;
		public int xp;
		public float alertLevelIncrease;
		public Pair<Float, Float> effect;
		public Pair<Float, Float> repLossOnDetect;
		public Set<Specialization> specializations = new HashSet<>();
		public boolean listInIntel;
		public float sortOrder;
		
		public void addSpecializations(Collection<String> specs) {
			for (String spec : specs) {
				try {
					Specialization spec2 = Specialization.valueOf(spec.toUpperCase(Locale.ENGLISH));
					log.info("Adding specialization for " + this.name + ": " + spec2.toString());
					specializations.add(spec2);
				} catch (Exception ex) {
					throw new RuntimeException("Failed to load agent action specializations", ex);
				}
			}
		}
		
		public boolean canAgentExecute(AgentIntel agent) {
			if (agent.getSpecializationsCopy().isEmpty()) return true;
			for (Specialization spec : agent.getSpecializationsCopy()) {
				if (isCompatibleWithSpecialization(spec))
					return true;
			}
			return false;
		}
		
		public boolean isCompatibleWithSpecialization(Specialization spec) {
			if (specializations.isEmpty()) return true;
			return specializations.contains(spec);
		}

		@Override
		public int compareTo(@NotNull CovertOpsManager.CovertActionDef o) {
			return Float.compare(this.sortOrder, o.sortOrder);
		}
	}
	
}
