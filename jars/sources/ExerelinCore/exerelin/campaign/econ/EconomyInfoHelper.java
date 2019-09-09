package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySourceType;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
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
	
	Set<String> haveHeavyIndustry = new HashSet<>();
	
	// for each faction, store map of commodity ID and its best output
	Map<String, Map<String, Integer>> factionProductionByFaction = new HashMap<>();
	
	Map<String, List<ProducerEntry>> producersByFaction = new HashMap<>();
	Map<String, List<ProducerEntry>> producersByCommodity = new HashMap<>();
	Map<String, Map<FactionAPI, Integer>> marketSharesByCommodity = new HashMap<>();
	
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
		currInstance.collectEconomicData();
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
	
	public void collectEconomicData() 
	{
		haveHeavyIndustry.clear();
		factionProductionByFaction.clear();
		producersByFaction.clear();
		producersByCommodity.clear();
		marketSharesByCommodity.clear();
		
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
			
			for (MarketAPI producer : data.getMarkets()) {
				if (producer.getCommodityData(commodityId).isIllegal())
					continue;
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
		
		// determine which factions have heavy industry by whether they produce ships
		for (FactionAPI faction : Global.getSector().getAllFactions())
		{
			String factionId = faction.getId();
			if (!factionProductionByFaction.containsKey(factionId)) {
				continue;
			}
			if (!factionProductionByFaction.get(factionId).containsKey(Commodities.SHIPS)) {
				continue;
			}
			haveHeavyIndustry.add(factionId);
		}
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getCommoditiesProducedByFaction("hegemony");
	public Map<String, Integer> getCommoditiesProducedByFaction(String factionId) {
		if (!factionProductionByFaction.containsKey(factionId))
			return null;
		
		logInfo("Faction " + factionId + " produces the following commodities:");
		Map<String, Integer> result = factionProductionByFaction.get(factionId);
		for (Map.Entry<String, Integer> tmp : result.entrySet()) {
			String comId = tmp.getKey();
			int amount = tmp.getValue();
			logInfo("  " + amount + " " + comId);
		}
		
		return result;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getProducers("hegemony", "metals", 0);
	public List<ProducerEntry> getProducers(String factionId, String commodityId, int min) {
		List<ProducerEntry> results = new ArrayList<>();
		logInfo(factionId + " producers of " + commodityId + ":");
		for (ProducerEntry entry : producersByCommodity.get(commodityId)) {
			if (!entry.factionId.equals(factionId)) continue;
			if (entry.output < min) continue;
			results.add(entry);
			logInfo(String.format("  %s (%s): %d", entry.market.getName(), entry.factionId, entry.output));
		}
		
		return results;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getCompetingProducers("hegemony", "metals", 0);
	public List<ProducerEntry> getCompetingProducers(String factionId, String commodityId, int min) {
		List<ProducerEntry> results = new ArrayList<>();
		logInfo("Competitors with " + factionId + " for " + commodityId + ":");
		for (ProducerEntry entry : producersByCommodity.get(commodityId)) {
			if (entry.factionId.equals(factionId)) continue;
			if (entry.output < min) continue;
			results.add(entry);
			logInfo(String.format("  %s (%s): %d", entry.market.getName(), entry.factionId, entry.output));
		}
		
		return results;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().getCompetingProducerFactions("hegemony", "metals");
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
	 * The return value is equal to the sum of market shares of both factions, for each commodity that faction 1 produces.
	 * @param factionId1
	 * @param factionId2
	 * @return
	 */
	public int getCompetitionFactor(String factionId1, String factionId2) {
		int factor = 0;		
		Map<String, Integer> myCommodities = getCommoditiesProducedByFaction(factionId1);
		FactionAPI otherFaction = Global.getSector().getFaction(factionId2);
		
		for (Map.Entry<String, Integer> tmp : myCommodities.entrySet()) {
			String comId = tmp.getKey();
			CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(comId);
			if (spec.isPersonnel()) continue;
			
			int amount1 = tmp.getValue();
			int amount2 = marketSharesByCommodity.get(comId).get(otherFaction);
			factor += amount1 + amount2;
		}
		return factor;
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		collectEconomicData();
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
	
	public static class ProducerEntry {
		String commodityId;
		String factionId;
		MarketAPI market;
		int output;
		
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
