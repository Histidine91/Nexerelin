package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.HydroponicsLab;
import exerelin.campaign.econ.RecyclingPlant;
import exerelin.campaign.econ.SupplyWorkshop;
import exerelin.ExerelinConstants;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
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
	
	public static void destroyCommodityStocks(MarketAPI market, CommodityOnMarketAPI commodity, float mult, float variance)
	{
		float current = commodity.getStockpile();
		if (variance != 0)
			mult = mult * MathUtils.getRandomNumberInRange(1 - variance, 1 + variance);
		commodity.removeFromStockpile(current * mult);
		Global.getLogger(ExerelinUtilsMarket.class).info("Destroyed " + String.format("%.1f", current * mult) + " of " + commodity.getId() 
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
	
	// do we really want any of these?
	public static float getFarmingFoodSupply(MarketAPI market, boolean applyMultMod)
	{
		float pop = ExerelinUtilsMarket.getPopulation(market.getSize());
		float food = 0;
		
		// planet food
		if (market.hasCondition(Conditions.TERRAN))
			food += ConditionData.WORLD_TERRAN_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.ARID))
			food += ConditionData.WORLD_ARID_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.WATER))
		{
			float thisFood = ConditionData.WORLD_WATER_FARMING_MULT * pop;
			if (thisFood > ConditionData.WORLD_WATER_MAX_FOOD)
				thisFood = ConditionData.WORLD_WATER_MAX_FOOD;
			food += thisFood;
		}
		else if (market.hasCondition(Conditions.DESERT))
			food += ConditionData.WORLD_DESERT_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.JUNGLE))
			food += ConditionData.WORLD_JUNGLE_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.ICE))
			food += ConditionData.WORLD_ICE_FARMING_MULT * pop;
		else if (market.hasCondition("barren_marginal"))
			food += ConditionData.WORLD_BARREN_MARGINAL_FARMING_MULT * pop;
		else if (market.hasCondition("twilight"))
			food += ConditionData.WORLD_TWILIGHT_FARMING_MULT * pop;
		else if (market.hasCondition("tundra"))
			food += ConditionData.WORLD_TUNDRA_FARMING_MULT * pop;
		
		if (applyMultMod)
			food *= market.getCommodityData(Commodities.FOOD).getSupply().computeMultMod();
		
		return food;
	}
	
	public static float getMarketBaseFoodSupply(MarketAPI market, boolean applyMultMod)
	{
		float pop = ExerelinUtilsMarket.getPopulation(market.getSize());
		float food = 0;
		
		// planet food
		food += getFarmingFoodSupply(market, false);
		
		// market conditions
		int hydroponicsCount = ExerelinUtilsMarket.countMarketConditions(market, "exerelin_hydroponics");
		int hydroponicsVanillaCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.HYDROPONICS_COMPLEX);
		int aquacultureCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.AQUACULTURE);
		food += hydroponicsCount * HydroponicsLab.HYDROPONICS_FOOD_POP_MULT * pop;
		food += aquacultureCount * ConditionData.AQUACULTURE_FOOD_MULT * pop;
		food += hydroponicsVanillaCount * ConditionData.HYDROPONICS_COMPLEX_FOOD;
		
		if (applyMultMod)
			food *= market.getCommodityData(Commodities.FOOD).getSupply().computeMultMod();
		
		return food;
	}
	
	public static float getMarketBaseFuelDemand(MarketAPI market, float base)
	{
		float pop = ExerelinUtilsMarket.getPopulation(market.getSize());
		float fuel = 0;
		
		int spaceportCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.SPACEPORT) + ExerelinUtilsMarket.countMarketConditions(market, Conditions.ORBITAL_STATION);
		int militaryBaseCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.MILITARY_BASE);
		int outpostCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.OUTPOST);
		
		fuel += spaceportCount * 0.6f * Math.min(ConditionData.ORBITAL_STATION_FUEL_BASE + pop * ConditionData.ORBITAL_STATION_FUEL_MULT, ConditionData.ORBITAL_STATION_FUEL_MAX);
		fuel += militaryBaseCount * 0.6f * ConditionData.MILITARY_BASE_FUEL;
		fuel += outpostCount * 0.8f * ConditionData.OUTPOST_FUEL;
		fuel *= market.getCommodityData(Commodities.FUEL).getDemand().getDemand().computeMultMod();
		
		fuel += base;	// even if no fuel is actually consumed
		
		return fuel;
	}
	
	public static float getMarketBaseMachineryDemand(MarketAPI market, float base)
	{
		float pop = ExerelinUtilsMarket.getPopulation(market.getSize());
		float machinery = 0;
		
		for (MarketConditionAPI conditionAPI : market.getConditions())
		{
			String cond = conditionAPI.getId();
			switch (cond)
			{
				case Conditions.ARID:
					machinery += ConditionData.WORLD_ARID_MACHINERY_MULT * pop;
					break;
				case Conditions.DESERT:
					machinery += ConditionData.WORLD_DESERT_MACHINERY_MULT * pop;
					break;
				case Conditions.ICE:
					machinery += ConditionData.WORLD_ICE_MACHINERY_MULT * pop;
					break;
				case Conditions.JUNGLE:
					machinery += ConditionData.WORLD_JUNGLE_MACHINERY_MULT * pop;
					break;
				case Conditions.TERRAN:
					machinery += ConditionData.WORLD_TERRAN_MACHINERY_MULT * pop;
					break;
				case Conditions.WATER:
					machinery += ConditionData.WORLD_WATER_MACHINERY_MULT * pop;
					break;
				case "barren_marginal":
					machinery += ConditionData.WORLD_BARREN_MARGINAL_MACHINERY_MULT * pop;
					break;
				case "twilight":
					machinery += ConditionData.WORLD_TWILIGHT_MACHINERY_MULT * pop;
					break;
				case "tundra":
					machinery += ConditionData.WORLD_TUNDRA_MACHINERY_MULT * pop;
					break;
					
				case Conditions.ANTIMATTER_FUEL_PRODUCTION:
					machinery += ConditionData.FUEL_PRODUCTION_MACHINERY;
					break;
				case Conditions.AQUACULTURE:
					machinery += ConditionData.AQUACULTURE_MACHINERY_MULT * pop;
					break;
				case Conditions.AUTOFAC_HEAVY_INDUSTRY:
					machinery += ConditionData.AUTOFAC_HEAVY_MACHINERY_DEMAND;
					break;
				case Conditions.LIGHT_INDUSTRIAL_COMPLEX:
					machinery += ConditionData.LIGHT_INDUSTRY_MACHINERY;
					break;
				case Conditions.MILITARY_BASE:
					machinery += ConditionData.MILITARY_BASE_MACHINERY;
					break;
				case Conditions.ORE_COMPLEX:
					machinery += ConditionData.ORE_MINING_MACHINERY;
					break;
				case Conditions.ORE_REFINING_COMPLEX:
					machinery += ConditionData.ORE_REFINING_MACHINERY;
					break;
				case Conditions.ORGANICS_COMPLEX:
					machinery += ConditionData.ORGANICS_MINING_MACHINERY;
					break;
				case Conditions.VOLATILES_COMPLEX:
					machinery += ConditionData.VOLATILES_MINING_MACHINERY;
					break;
				case "exerelin_recycling_plant":
					machinery += RecyclingPlant.RECYCLING_HEAVY_MACHINERY_DEMAND;
					break;
				case "exerelin_hydroponics":
					machinery += HydroponicsLab.HYDROPONICS_HEAVY_MACHINERY_POP_MULT * pop;
					break;
				case "exerelin_supply_workshop":
					machinery += SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY_DEMAND;
					break;
			}
		}
		
		machinery += base;	// even if no machinery is actually consumed
		
		return machinery;
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
		
		return true;
	}
	
	/**
	 * Can this market be invaded, by player or by NPCs?
	 * @param market
	 * @return
	 */
	public static boolean canBeInvaded(MarketAPI market)
	{
		if (market.hasCondition(Conditions.ABANDONED_STATION)) return false;		
		if (market.getPrimaryEntity() instanceof CampaignFleetAPI) return false;
		
		FactionAPI marketFaction = market.getFaction();
		if (marketFaction.isNeutralFaction()) return false;
		
		if (market.getPrimaryEntity().hasTag(ExerelinConstants.TAG_UNINVADABLE))
			return false;
		if (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_UNINVADABLE))
			return false;
		
		return true;
	}
}
