package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.missions.DisruptMissionIntel.TargetReason;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;
import org.json.JSONObject;

@Deprecated
public class DisruptMissionManager extends BaseEventManager {

	public static final String KEY = "nex_DisruptMissionManager";
	public static final int MIN_PLAYER_LEVEL = 15;
	public static final int MAX_REWARD = 1200*1000;
	public static Logger log = Global.getLogger(DisruptMissionManager.class);
	
	public static DisruptMissionManager getInstance() {
		Object test = Global.getSector().getPersistentData().get(KEY);
		return (DisruptMissionManager) test; 
	}
	
	public DisruptMissionManager() {
		super();
		Global.getSector().getPersistentData().put(KEY, this);
	}
	
	@Override
	protected int getMinConcurrent() {
		return 0;
	}
	
	@Override
	protected int getMaxConcurrent() {
		return 3;
	}

	@Override
	protected float getIntervalRateMult() {
		return Global.getSettings().getFloat("nex_disruptMissionIntervalRateMult");
	}
	
	// runcode exerelin.campaign.intel.missions.DisruptMissionManager.getInstance().advance(99999);
	@Override
	protected EveryFrameScript createEvent() 
	{
		if (true) return null;
		
		if (Global.getSector().getPlayerStats().getLevel() < MIN_PLAYER_LEVEL)
			return null;
		if ((float) Math.random() < 0.5f) return null;
		return createEventStatic(false);
	}
	
	// runcode exerelin.campaign.intel.missions.DisruptMissionManager.createEventStatic(true);
	public static EveryFrameScript createEventStatic(boolean isExternal) 
	{
		log.info("Attempting to create disruption mission event");
		
		FactionAPI faction = pickSourceFaction();
		if (faction == null) {
			log.info("Failed to pick source faction");
			return null;
		}
		
		TargetEntry target = getTarget(faction, 4, 999);
		if (target == null) {
			log.info("Failed to pick target");
			return null;
		}
		
		float duration = 60 + target.market.getSize() * 5;
		
		DisruptMissionIntel intel = new DisruptMissionIntel(target, faction, duration);
		intel.init();
		if (intel.isDone()) intel = null;
		else if (isExternal)
			Global.getSector().addScript(intel);
		
		log.info("Intel successfully created");
		return intel;
	} 
		
	public void cleanup() {
		active.clear();
	}
	
	protected static FactionAPI pickSourceFaction() {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) 
		{
			if (factionId.equals(Factions.PLAYER)) continue;
			if (!NexConfig.allowPirateInvasions && NexUtilsFaction.isPirateFaction(factionId))
				continue;
			picker.add(factionId);
		}
		
		String factionId = picker.pick();
		if (factionId == null) return null;
		return Global.getSector().getFaction(factionId);
	}
	
	protected static boolean isRepLowEnough(FactionAPI faction, FactionAPI target, TargetReason reason)
	{
		if (faction == target) return false;
		
		RepLevel required = DisruptMissionIntel.MAX_REP_LEVEL;
		
		boolean monopolist = DiplomacyTraits.hasTrait(faction.getId(), DiplomacyTraits.TraitIds.MONOPOLIST);
		if (reason == TargetReason.MILITARY || monopolist) required = RepLevel.COOPERATIVE;
		return faction.getRelationshipLevel(target).isAtBest(required);
	}
	
	public static TargetEntry getTarget(FactionAPI faction, int minAmount, int maxAmount) {
		return getTarget(faction, minAmount, maxAmount, new Random());
	}
	
	public static TargetEntry getTarget(FactionAPI faction, int minAmount, int maxAmount, Random random) 
	{
		String factionId = faction.getId();
		WeightedRandomPicker<TargetEntry> picker = new WeightedRandomPicker<>(random);
		Map<String, Integer> commodities = EconomyInfoHelper.getInstance().getCommoditiesProducedByFaction(factionId);
		Map<String, Integer> importantCommodities = new HashMap<>();
		//List<ProducerEntry> competitors = EconomyInfoHelper.getInstance().getCompetingProducers(faction.getId(), 3);
		boolean vsFreePort = false, vsCompetitors = false;
		
		JSONObject json = faction.getCustom().optJSONObject(Factions.CUSTOM_PUNITIVE_EXPEDITION_DATA);
		if (json != null)
		{
			//vsFreePort = json.optBoolean("vsFreePort", false);
			vsCompetitors = json.optBoolean("vsCompetitors", false);
		}
		
		for (String commodityId : commodities.keySet()) {
			int amount = commodities.get(commodityId);
			if (amount < 4) continue;
			importantCommodities.put(commodityId, amount);
		}
		FactionAPI commission = Misc.getCommissionFaction();
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			if (market.isHidden()) continue;
			
			if (AllianceManager.areFactionsAllied(factionId, market.getFactionId()) || market.getFaction().isPlayerFaction()
					|| market.getFaction() == commission)
				continue;
			if (!NexConfig.allowPirateInvasions && 
					NexUtilsFaction.isPirateFaction(market.getFactionId()))
				continue;
			
			boolean hostile = false;	//market.getFaction().isHostileTo(faction);
			boolean canEconomicAttack = isRepLowEnough(faction, market.getFaction(), 
					TargetReason.ECONOMIC_COMPETITION);
			boolean canFreePortAttack = isRepLowEnough(faction, market.getFaction(), 
					TargetReason.FREE_PORT);
			
			boolean freePort = market.isFreePort();
			int size = market.getSize();
			
			for (Industry ind : market.getIndustries()) 
			{
				if (ind.getId().equals(Industries.POPULATION))
					continue;
				if (!ind.canBeDisrupted())
					continue;
				if (ind.isDisrupted())
					continue;
				
				if (DisruptMissionIntel.calculateBaseReward(market, ind) > MAX_REWARD)
					continue;
				
				IndustrySpecAPI spec = ind.getSpec();
				
				// spaceport
				if (spec.hasTag(Industries.TAG_SPACEPORT)) {
					if (freePort && vsFreePort && canFreePortAttack) {
						picker.add(new TargetEntry(market, ind, null, TargetReason.FREE_PORT, 5), 
								size * 2);
					}
					if (hostile) {
						picker.add(new TargetEntry(market, ind, null, TargetReason.MILITARY, 8), 
								size * 1.5f);
					}
					continue;
				}
				
				// other stuff
				if (vsCompetitors && canEconomicAttack) {
					for (String commodityId : importantCommodities.keySet()) 
					{
						int ours = importantCommodities.get(commodityId);
						MutableCommodityQuantity quant = ind.getSupply(commodityId);
						if (quant == null) continue;
						int theirs = quant.getQuantity().getModifiedInt();
						if (theirs < minAmount) continue;
						if (theirs > maxAmount) continue;
						if (theirs < ours - 1) continue;
						
						picker.add(new TargetEntry(market, ind, commodityId, 
								TargetReason.ECONOMIC_COMPETITION, 
								theirs), ours + theirs);
					}
				}
				
				if (hostile) {
					// heavy industry
					if (spec.hasTag(Industries.TAG_HEAVYINDUSTRY)) {
						picker.add(new TargetEntry(market, ind, null, TargetReason.MILITARY, 15),
								size * 5);
					}
					// military base/high command
					if (spec.hasTag(Industries.TAG_MILITARY) || spec.hasTag(Industries.TAG_COMMAND)) {
						picker.add(new TargetEntry(market, ind, null, TargetReason.MILITARY, 8),
								size * 3);
					}
				}
			}
		}
		
		return picker.pick();
	}
	
	public static class TargetEntry {
		public MarketAPI market;
		public Industry industry;
		public TargetReason reason;
		public String commodityId;
		public float value;
		
		
		public TargetEntry(MarketAPI market, Industry industry, String commodityId,
				TargetReason reason, float value) {
			this.market = market;
			this.industry = industry;
			this.commodityId = commodityId;
			this.reason = reason;
			this.value = value;
		}
	}
}
