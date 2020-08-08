package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySourceType;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import com.fs.starfarer.api.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.console.Console;

public class EconomyInfoHelper implements EconomyTickListener {
	
	public static Logger log = Global.getLogger(EconomyInfoHelper.class);
	public static boolean loggingMode = false;
	
	protected static EconomyInfoHelper currInstance;
	
	protected Set<String> haveHeavyIndustry = new HashSet<>();
	
	// for each faction, store map of commodity ID and its best output
	protected Map<String, Map<String, Integer>> factionProductionByFaction = new HashMap<>();
	
	protected Map<String, List<ProducerEntry>> producersByFaction = new HashMap<>();
	protected Map<String, List<ProducerEntry>> producersByCommodity = new HashMap<>();
	protected Map<String, Map<FactionAPI, Integer>> marketSharesByCommodity = new HashMap<>();
	protected Map<MarketAPI, Float> aiCoreUsers = new HashMap<>();
	protected Map<String, Integer> empireSizeCache = new HashMap<>();
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.createInstance()
	/**
	 * Creates and stores an instance of the economy info helper. Should be called on every game load.
	 */
	public static void createInstance() {
		if (currInstance != null) {
			Global.getSector().getListenerManager().removeListener(currInstance);
		}
		currInstance = new EconomyInfoHelper();
		Global.getSector().getListenerManager().addListener(currInstance, true);
		
		currInstance.collectEconomicData(true);
	}
	
	public static EconomyInfoHelper getInstance() {
		return currInstance;
	}
	
	public static void logInfo(String str) {
		if (!loggingMode) return;
		log.info(str);
		Console.showMessage(str);
	}
	
	public static void setLoggingMode(boolean mode) {
		loggingMode = mode;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().collectEconomicData()
	public void collectEconomicData(boolean firstRun) 
	{
		haveHeavyIndustry.clear();
		factionProductionByFaction.clear();
		producersByFaction.clear();
		producersByCommodity.clear();
		marketSharesByCommodity.clear();
		aiCoreUsers.clear();
		empireSizeCache.clear();
		
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsInGroup(null);
		if (markets.isEmpty())
			return;
		
		// list all producers of all commodities
		MarketAPI test = markets.get(0);
		for (CommodityOnMarketAPI com : test.getAllCommodities()) 
		{
			if (com.isNonEcon() || com.isPersonnel())
				continue;
			
			String commodityId = com.getId();
			producersByCommodity.put(commodityId, new ArrayList<ProducerEntry>());
			
			CommodityMarketDataAPI data = com.getCommodityMarketData();
			marketSharesByCommodity.put(commodityId, data.getMarketSharePercentPerFaction());
			
			for (MarketAPI producer : data.getMarkets()) 
			{
				if (producer.isHidden()) continue;
				
				// don't count illegal production
				if (producer.getCommodityData(commodityId).isIllegal())
					continue;
				
				// make sure supply is local
				CommoditySourceType source = data.getMarketShareData(producer).getSource();
				if (source != CommoditySourceType.LOCAL)
					continue;
				
				String factionId = producer.getFactionId();
				
				// instantiate faction-keyed maps
				if (!producersByFaction.containsKey(factionId))
				{
					producersByFaction.put(factionId, new ArrayList<ProducerEntry>());
				}
				if (!factionProductionByFaction.containsKey(factionId))
				{
					factionProductionByFaction.put(factionId, new HashMap<String, Integer>());
				}
				
				int output = producer.getCommodityData(commodityId).getAvailable();
				
				// create and store producer entry
				ProducerEntry entry = new ProducerEntry(commodityId, factionId, producer, output);
				producersByCommodity.get(commodityId).add(entry);
				producersByFaction.get(factionId).add(entry);
				
				int factionsBest = 0;
				if (factionProductionByFaction.get(factionId).containsKey(commodityId)) {
					factionsBest = factionProductionByFaction.get(factionId).get(commodityId);
				}
				if (output > factionsBest) {
					factionProductionByFaction.get(factionId).put(commodityId, output);
				}
			}
		}
		
		// determine which factions have heavy industry
		if (firstRun) {
			// iterate over all markets
			// we normally do it by checking whether they produce ships,
			// but this doesn't seem to pick up all the heavy industries on first run
			for (MarketAPI market : markets)
			{
				if (market.isHidden()) continue;
				if (haveHeavyIndustry.contains(market.getFactionId())) 
					continue;	// already checked this faction
				for (Industry ind : market.getIndustries()) 
				{
					if (!ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY))
						continue;
					if (ind.getDisruptedDays() > 15) continue;
					haveHeavyIndustry.add(market.getFactionId());
					break;
				}
			}
		} else {
			// check whether they produce ships
			for (FactionAPI faction : Global.getSector().getAllFactions())
			{
				String factionId = faction.getId();
				if (!factionProductionByFaction.containsKey(factionId)) {
					continue;
				}
				if (!factionProductionByFaction.get(factionId).containsKey(Commodities.SHIPS)) {
					continue;
				}
				//log.info(factionId + " has heavy industry");
				haveHeavyIndustry.add(factionId);
			}
		}
		
		for (MarketAPI market : markets)
		{
			float aiScore = HegemonyInspectionManager.getAICoreUseValue(market);
			if (aiScore > 0) {
				aiCoreUsers.put(market, aiScore);
			}
		}
	}
	
	protected void incrementEmpireSize(String factionId, MarketAPI market) {
		int size = 0;
		if (empireSizeCache.containsKey(factionId))
			size = empireSizeCache.get(factionId);
		
		size += market.getSize();
		empireSizeCache.put(factionId, size);
	}
	
	public int getCachedEmpireSize(String factionId) {
		if (empireSizeCache.containsKey(factionId))
			return empireSizeCache.get(factionId);
		
		return 0;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getCommoditiesProducedByFaction("hegemony");
	/**
	 * Gets a map of all commodities the specified faction produces 
	 * and their highest output in that faction.
	 * @param factionId
	 * @return
	 */
	public Map<String, Integer> getCommoditiesProducedByFaction(String factionId) {
		if (!factionProductionByFaction.containsKey(factionId))
			return new HashMap<>();
		
		Map<String, Integer> result = factionProductionByFaction.get(factionId);
		
		if (loggingMode) {
			logInfo("Faction " + factionId + " produces the following commodities:");
			for (Map.Entry<String, Integer> tmp : result.entrySet()) {
				String comId = tmp.getKey();
				int amount = tmp.getValue();
				logInfo("  " + amount + " " + comId);
			}
		}
		
		return result;
	}
	
	public int getFactionCommodityProduction(String factionId, String commodityId) {
		Map<String, Integer> ourProduction = getCommoditiesProducedByFaction(factionId);
		if (ourProduction == null) return 0;
		if (!ourProduction.containsKey(commodityId))
			return 0;
		return ourProduction.get(commodityId);
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getProducers("hegemony", "metals", 0);
	/**
	 * Gets a list of all markets of the specified faction producing the specified commodity.
	 * @param factionId
	 * @param commodityId
	 * @param min Minimum amount of production to be considered.
	 * @return
	 */
	public List<ProducerEntry> getProducers(String factionId, String commodityId, int min) {
		List<ProducerEntry> results = new ArrayList<>();
		logInfo(factionId + " producers of " + commodityId + ":");
		if (!producersByCommodity.containsKey(commodityId)) return results;
		for (ProducerEntry entry : producersByCommodity.get(commodityId)) {
			if (!entry.factionId.equals(factionId)) continue;
			if (entry.output < min) continue;
			results.add(entry);
			logInfo(String.format("  %s (%s): %d", entry.market.getName(), entry.factionId, entry.output));
		}
		
		return results;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getCompetingProducers("hegemony", "metals", 0);

	/**
	 * Gets a list of all markets NOT of the specified faction producing the specified commodity.
	 * @param factionId
	 * @param commodityId
	 * @param min Minimum amount of production to be considered.
	 * @return
	 */
	public List<ProducerEntry> getCompetingProducers(String factionId, String commodityId, int min) {
		List<ProducerEntry> results = new ArrayList<>();
		logInfo("Competitors with " + factionId + " for " + commodityId + ":");
		if (!producersByCommodity.containsKey(commodityId)) return results;
		for (ProducerEntry entry : producersByCommodity.get(commodityId)) {
			if (entry.factionId.equals(factionId)) continue;
			if (entry.output < min) continue;
			results.add(entry);
			logInfo(String.format("  %s (%s): %d", entry.market.getName(), entry.factionId, entry.output));
		}
		
		return results;
	}
	
	/**
	 * Gets a list of all producers which compete with the specified faction 
	 * in any commodity the faction supplies.
	 * @param factionId
	 * @param min Minimum amount of production to be considered. 
	 * Applies both to the list of commodities to be checked, 
	 * and the producers to be returned.
	 * @return
	 */
	public List<ProducerEntry> getCompetingProducers(String factionId, int min) {
		List<ProducerEntry> results = new ArrayList<>();
		Map<String, Integer> ourProduction = factionProductionByFaction.get(factionId);
		if (ourProduction == null) return results;
		Set<String> commodities = ourProduction.keySet();
		for (String commodityId : commodities) {
			if (ourProduction.get(commodityId) < min)
				continue;
			results.addAll(getCompetingProducers(factionId, commodityId, min));
		}
		
		return results;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getCompetingProducerFactions("hegemony", "metals");
	/**
	 * Gets all factions that compete with the specified faction in the specified commodity, 
	 * and their market share of that commodity.
	 * @param factionId
	 * @param commodityId
	 * @return
	 */
	public List<Pair<FactionAPI, Integer>> getCompetingProducerFactions(String factionId, String commodityId) {
		List<Pair<FactionAPI, Integer>> results = new ArrayList<>();
		logInfo("Competitors with " + factionId + " for " + commodityId + ":");
		for (Map.Entry<FactionAPI, Integer> tmp : marketSharesByCommodity.get(commodityId).entrySet()) {
			FactionAPI faction = tmp.getKey();
			if (faction.getId().equals(factionId)) continue;
			int share = tmp.getValue();
			results.add(new Pair<>(faction, share));
			logInfo("  " + faction.getDisplayName() + ": " + share + "%");
		}
		
		return results;
	}
	
	/**
	 * Gets an integer representing the degree of competition between the two factions in commodity production.
	 * The return value is equal to (faction 2 share) - (faction 1 share/2), for each commodity that faction 1 produces.
	 * Only counts commodities where faction 1's share is at least 10% of total.
	 * @param factionId1
	 * @param factionId2
	 * @return
	 */
	public int getCompetitionFactor(String factionId1, String factionId2) {
		float factor = 0;		
		Map<String, Integer> myCommodities = getCommoditiesProducedByFaction(factionId1);
		if (myCommodities == null) return 0;
		if (getCommoditiesProducedByFaction(factionId2) == null) return 0;
		
		FactionAPI faction = Global.getSector().getFaction(factionId1);
		FactionAPI otherFaction = Global.getSector().getFaction(factionId2);
		
		for (Map.Entry<String, Integer> tmp : myCommodities.entrySet()) {
			String comId = tmp.getKey();
			CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(comId);
			if (spec.isPersonnel()) continue;
			
			if (!marketSharesByCommodity.containsKey(comId))
				continue;
			
			Integer amount1 = marketSharesByCommodity.get(comId).get(faction);
			if (amount1 == null || amount1 < 10) continue;
			Integer amount2 = marketSharesByCommodity.get(comId).get(otherFaction);
			if (amount2 == null) continue;
			
			float amount = amount2 - (amount1/2);
			if (amount < 0) continue;
			factor += amount;
		}
		return Math.round(factor);
	}
	
	public List<ProducerEntry> getProducersByCommodity(String commodityId) {
		return producersByCommodity.get(commodityId);
	}	
	
	public boolean hasHeavyIndustry(String factionId) {
		return haveHeavyIndustry.contains(factionId);
	}
	
	public Map<MarketAPI, Float> getAICoreUsers() {
		return aiCoreUsers;
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		collectEconomicData(false);
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
	
	public static class ProducerEntry {
		public String commodityId;
		public String factionId;
		public MarketAPI market;
		public int output;
		
		public ProducerEntry(String commodityId, String factionId, MarketAPI market, int output)
		{
			this.commodityId = commodityId;
			this.factionId = factionId;
			this.market = market;
			this.output = output;
			
			logInfo(String.format("%s of %s produces %d of %s", market.getName(), factionId, output, commodityId));
		}
	}
}
