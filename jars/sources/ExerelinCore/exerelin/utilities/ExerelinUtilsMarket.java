package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class ExerelinUtilsMarket {
	
	// use the memory key instead of this wherever possible
	public static final List<String> NO_INVADE_MARKETS = Arrays.asList(new String[]{"SCY_prismFreeport", "prismFreeport", "prismFreeport_market"});
	
	/**
	 * Gets demand for a particular commodity on a given market. Should be safe to use in most instances.
	 * @param market
	 * @param commodity
	 * @param consumingOnly if true, subtract non-consuming demand from total demand
	 * @return
	 */
	public static float getCommodityDemand(MarketAPI market, String commodity, boolean consumingOnly)
	{
		float demand = market.getCommodityData(commodity).getDemand().getDemand().modified;
		if (consumingOnly) demand -= market.getCommodityData(commodity).getDemand().getNonConsumingDemand().modified;
		return demand;
	}
	
	public static float getCommodityDemand(MarketAPI market, String commodity)
	{
		return getCommodityDemand(market, commodity, true);
	}
	
	/**
	 * Gets supply for a particular commodity on a given market.
	 * Because many market conditions scale their output based on the demand met of input commodities (and almost all use crew),
	 * this is not recommended for processed and finished items (metals, machinery, etc.)
	 * In fact, you better not use it at all prior to the economy stabilization phase
	 * @param market
	 * @param commodity
	 * @return
	 */
	public static float getCommoditySupply(MarketAPI market, String commodity)
	{
		return market.getCommodityData(commodity).getSupply().modified;
	}
	
	public static float getCommoditySupplyMult(MarketAPI market, String commodity)
	{
		return market.getCommodityData(commodity).getSupply().computeMultMod();
	}
	
	public static float getCommodityDemandMult(MarketAPI market, String commodity)
	{
		return market.getCommodityData(commodity).getDemand().getDemand().computeMultMod();
	}
	
	public static float getCommodityDemandFractionMet(MarketAPI market, String commodity, boolean clamp)
	{
		if (clamp) return market.getCommodityData(commodity).getDemand().getClampedFractionMet();
		else return market.getCommodityData(commodity).getDemand().getFractionMet();
	}
	
	public static int countMarketConditions(MarketAPI market, String marketCondition)
	{
		int count = 0;
		List<MarketConditionAPI> conditions = market.getConditions();
		for (MarketConditionAPI condition : conditions)
		{
			if (condition.getId().equals(marketCondition)) count++;
		}
		return count;
	}
	
	public static float getPopulation(MarketAPI market)
	{
		return getPopulation(market.getSize());
	}
	
	public static float getPopulation(int size)
	{
		//return (int)(Math.pow(10, size));
		if (size <= 1) return 0.125f;
		if (size == 2) return 0.25f;
		if (size == 3) return 0.5f;
		
		return (float) Math.pow(2, size - 4);
	}
	
	public static void removeOneMarketCondition(MarketAPI market, String conditionId)
	{
		int count = countMarketConditions(market, conditionId);
		if (count == 0)
			Global.getLogger(ExerelinUtilsMarket.class).warn("Tried to remove nonexistent market condition " + conditionId + " from " + market.getId());
		market.removeCondition(conditionId);	// removes all
		for (int i=0; i<count - 1; i++)
			market.addCondition(conditionId);	// add back all but one
	}
	
	public static float getHyperspaceDistance(MarketAPI market1, MarketAPI market2)
	{
		SectorEntityToken primary1 = market1.getPrimaryEntity();
		SectorEntityToken primary2 = market2.getPrimaryEntity();
		if (primary1.getContainingLocation() == primary2.getContainingLocation())
			return 0;
		
		return Misc.getDistance(primary1.getLocationInHyperspace(), primary2.getLocationInHyperspace());
	}
	
	// the fancy bits are adapted from LazyWizard's Console Commands
	@Deprecated
	public static void refreshMarket(MarketAPI market, boolean force)
	{
		if (market.getFactionId().equals("templars"))	// doesn't work on Templars, sorry
			return;
		
		boolean canUpdate = true;
		final Field sinceLastCargoUpdate, minCargoUpdateInterval;
		if (force) {
			try
			{
				sinceLastCargoUpdate = BaseSubmarketPlugin.class.getDeclaredField("sinceLastCargoUpdate");
				sinceLastCargoUpdate.setAccessible(true);
				minCargoUpdateInterval = BaseSubmarketPlugin.class.getDeclaredField("minCargoUpdateInterval");
				minCargoUpdateInterval.setAccessible(true);
			}
			catch (Exception ex)	// bah
			{
				Global.getLogger(ExerelinUtilsMarket.class).error(ex);
				return;
			}
		}
		else {
			sinceLastCargoUpdate = null;
			minCargoUpdateInterval = null;
		}
		if (!canUpdate) return;
		
		
		for (SubmarketAPI submarket : market.getSubmarketsCopy())
		{
			// Ignore storage tabs
			if (Submarkets.SUBMARKET_STORAGE.equals(submarket.getSpec().getId()))
				continue;
			
			if (submarket.getPlugin() instanceof BaseSubmarketPlugin)
			{
				final BaseSubmarketPlugin plugin = (BaseSubmarketPlugin) submarket.getPlugin();
				
				if (force)
				{ 
					try
					{
						float lastUpdate = sinceLastCargoUpdate.getFloat(plugin);
						float minUpdateInterval = minCargoUpdateInterval.getFloat(plugin);
						if (lastUpdate > minUpdateInterval)
						sinceLastCargoUpdate.setFloat(plugin, minUpdateInterval + 1);
					}
					catch (Exception ex)	// meh
					{
						Global.getLogger(ExerelinUtilsMarket.class).error(ex);
						continue;
					}
				}
				
				plugin.updateCargoPrePlayerInteraction();
			}
		}
	}
	
	/**
	 * Gets a float representing a portion of the stockpile for the specified commodity on the market.
	 * @param market
	 * @param commodity
	 * @param mult
	 * @param variance Output amount is multiplied by a random number from (1 - variance) to (1 + variance)
	 * @return
	 */
	public static float getCommodityPartialStocks(MarketAPI market, CommodityOnMarketAPI commodity, float mult, float variance)
	{
		float current = commodity.getStockpile();
		if (variance != 0)
			mult = mult * MathUtils.getRandomNumberInRange(1 - variance, 1 + variance);
		if (mult < 0) mult = 0;
		else if (mult > 1) mult = 1;
		return current * mult;
	}
	
	/**
	 * Gets a float representing a portion of the stockpile for the specified commodity on the market.
	 * @param market
	 * @param commodityId
	 * @param mult
	 * @param variance Output amount is multiplied by a random number from (1 - variance) to (1 + variance)
	 * @return
	 */
	public static float getCommodityPartialStocks(MarketAPI market, String commodityId, float mult, float variance)
	{
		CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
		return getCommodityPartialStocks(market, commodity, mult, variance);
	}
	
	/**
	 * Gets a float representing a portion of the stockpile for each commodity on the market, 
	 * with variance for each commodity.
	 * @param market
	 * @param mult
	 * @param variance Output amount is multiplied by a random number from (1 - variance) to (1 + variance)
	 * @param ignoreNonEcon Ignore non-econ commodities
	 * @param ignorePersonnel Ignore crew/marines
	 * @return
	 */
	public static Map<String, Float> getAllCommodityPartialStocks(MarketAPI market, 
			float mult, float variance, boolean ignoreNonEcon, boolean ignorePersonnel) {
		Map<String, Float> results = new HashMap<>();
		for (CommodityOnMarketAPI commodity: market.getAllCommodities()) 
		{
			if (ignoreNonEcon && commodity.isNonEcon()) continue;
			if (ignorePersonnel && commodity.isPersonnel()) continue;
			float amount = getCommodityPartialStocks(market, commodity, mult, variance);
			results.put(commodity.getId(), amount);
		}
		return results;
	}
	
	public static void destroyCommodityStocks(MarketAPI market, CommodityOnMarketAPI commodity, float mult, float variance)
	{
		float amount = getCommodityPartialStocks(market, commodity, mult, variance);
		commodity.removeFromStockpile(amount);
		Global.getLogger(ExerelinUtilsMarket.class).info("Destroyed " + String.format("%.1f", amount) + " of " + commodity.getId() 
				+ " on " + market.getName() + " (mult " + String.format("%.2f", mult) + ")");
	}
	
	public static void destroyCommodityStocks(MarketAPI market, String commodityId, float mult, float variance)
	{
		CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
		destroyCommodityStocks(market, commodity, mult, variance);
	}
	
	public static void destroyAllCommodityStocks(MarketAPI market, float mult, float variance) {
		for (CommodityOnMarketAPI commodity: market.getAllCommodities()) 
		{
			if (commodity.isNonEcon()) continue;
			destroyCommodityStocks(market, commodity, mult, variance);
		}
	}
	
	public static void setTariffs(MarketAPI market)
	{
		String factionId = market.getFactionId();
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		market.getTariff().modifyMult("nexerelinMult", ExerelinConfig.baseTariffMult);
		market.getTariff().modifyMult("nexerelinFactionMult", conf.tariffMult);
		if (market.hasCondition(Conditions.FREE_PORT))
		{
			market.getTariff().modifyMult("nexerelin_freeMarket", ExerelinConfig.freeMarketTariffMult);
		}
		else
		{
			market.getTariff().unmodify("nexerelin_freeMarket");
		}
	}
	
	public static boolean isMarketBeingInvaded(MarketAPI market)
	{
		return market.getMemoryWithoutUpdate().getBoolean("$beingInvaded")	// NPC fleet
				|| market.getId().equals(Global.getSector().getCharacterData().getMemoryWithoutUpdate().getString("$invasionTarget"));	// player
	}
	
	/**
	 * Did this market originally belong to the specified faction?
	 * @param market
	 * @param factionId
	 * @return
	 */
	public static boolean wasOriginalOwner(MarketAPI market, String factionId)
	{
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains("$startingFactionId"))
			return mem.getString("$startingFactionId").equals(factionId);
		
		return false;
	}
	
	/**
	 * Is this market still owned by its original owner?
	 * @param market
	 * @return
	 */
	public static boolean isWithOriginalOwner(MarketAPI market)
	{
		return wasOriginalOwner(market, market.getFactionId());
	}
	
	/**
	 * Can factions launch invasion fleets at <code>market</code>?
	 * Player may still be able to invade even if this returns false
	 * @param market
	 * @param minSize Minimum market size to consider for invasions
	 * @return
	 */
	public static boolean shouldTargetForInvasions(MarketAPI market, int minSize)
	{
		if (market.getSize() < minSize) return false;
		FactionAPI marketFaction = market.getFaction();
		
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(marketFaction.getId());
		if (config != null && !config.playableFaction)
			return false;
		boolean allowPirates = ExerelinConfig.allowPirateInvasions;
		if (!allowPirates && ExerelinUtilsFaction.isPirateFaction(marketFaction.getId()))
			return false;
		
		return canBeInvaded(market, false);
	}
	
	/**
	 * Can this market be invaded, by player or by NPCs?
	 * @param market
	 * @param isPlayer Is the would-be invader the player?
	 * @return
	 */
	public static boolean canBeInvaded(MarketAPI market, boolean isPlayer)
	{
		if (market.hasCondition(Conditions.ABANDONED_STATION)) return false;		
		if (market.getPrimaryEntity() instanceof CampaignFleetAPI) return false;
		
		FactionAPI marketFaction = market.getFaction();
		if (isPlayer)
		{
			String factionId = marketFaction.getId();
			if (factionId.equals(PlayerFactionStore.getPlayerFactionId()) || factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
				return false;
		}
		if (marketFaction.isNeutralFaction()) return false;
		if (!market.isInEconomy()) return false;
		
		if (market.getPrimaryEntity().hasTag(ExerelinConstants.TAG_UNINVADABLE))
			return false;
		if (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_UNINVADABLE))
			return false;
		
		return true;
	}
	
	public static boolean canTradeWithMarket(MarketAPI market)
	{
		FactionAPI faction = market.getFaction();
		if (faction.isAtWorst(Factions.PLAYER, RepLevel.SUSPICIOUS))
			return true;
		if (market.hasCondition(Conditions.FREE_PORT))
			return true;
		if (faction.getCustomBoolean(Factions.CUSTOM_ALLOWS_TRANSPONDER_OFF_TRADE))
			return true;
		
		return false;
	}
}
