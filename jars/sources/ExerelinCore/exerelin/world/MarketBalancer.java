package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.econ.SupplyWorkshop;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;

public class MarketBalancer 
{	
	public static final float ASSUMED_STABILITY_MULT = 1.5f * 0.75f;	// assumes stability == 5
	public static final float MAX_OVERSUPPLY = 1.4f;
	public static final float MAX_OVERDEMAND = 1f;
	protected static final Map<String, Float> FARMING_MULTS = new HashMap<>();
	
	protected static Logger log = Global.getLogger(MarketBalancer.class);
	
	protected Map<String, Map<MarketAPI, MutableStat>> demandByCommodity = new HashMap<>();
	protected Map<String, Map<MarketAPI, MutableStat>> supplyByCommodity = new HashMap<>();
	protected ExerelinMarketBuilder builder;
	protected Random random;
	
	static {
		FARMING_MULTS.put(Conditions.TERRAN, 2f);
		
		FARMING_MULTS.put("twilight", 1f);
		
		FARMING_MULTS.put(Conditions.ARID, 0.5f);
		FARMING_MULTS.put(Conditions.WATER, 0.5f);
		FARMING_MULTS.put(Conditions.JUNGLE, 0.5f);
		FARMING_MULTS.put("tundra", 0.5f);
		
		FARMING_MULTS.put("barren_marginal", 0.2f);
		FARMING_MULTS.put(Conditions.DESERT, 0.2f);
		FARMING_MULTS.put(Conditions.ICE, 0.2f);
	}
	
	public MarketBalancer(ExerelinMarketBuilder builder)
	{
		this.builder = builder;
		this.random = builder.random;
	}
	
	/**
	 * Gets a map of markets and their demand MutableStats for the specified commodity ID
	 * @param commodityId
	 * @return
	 */
	public Map<MarketAPI, MutableStat> getDemandEntries(String commodityId)
	{
		if (!demandByCommodity.containsKey(commodityId))
			demandByCommodity.put(commodityId, new HashMap<MarketAPI, MutableStat>());
		return demandByCommodity.get(commodityId);
	}
	
	/**
	 * Gets a map of markets and their supply MutableStats for the specified commodity ID
	 * @param commodityId
	 * @return
	 */
	public Map<MarketAPI, MutableStat> getSupplyEntries(String commodityId)
	{
		if (!supplyByCommodity.containsKey(commodityId))
			supplyByCommodity.put(commodityId, new HashMap<MarketAPI, MutableStat>());
		return supplyByCommodity.get(commodityId);
	}
	
	/**
	 * Returns the modified demand for the specified commodity across all markets
	 * @param commodityId
	 * @return
	 */
	public float getDemand(String commodityId)
	{
		float amount = 0;
		Map<MarketAPI, MutableStat> entries = getDemandEntries(commodityId);
		for (Map.Entry<MarketAPI, MutableStat> marketEntry : entries.entrySet())
		{
			amount += marketEntry.getValue().getModifiedValue();
		}
		return amount;
	}
	
	/**
	 * Returns the modified supply for the specified commodity across all markets
	 * @param commodityId
	 * @return
	 */
	public float getSupply(String commodityId)
	{
		float amount = 0;
		Map<MarketAPI, MutableStat> entries = getSupplyEntries(commodityId);
		for (Map.Entry<MarketAPI, MutableStat> marketEntry : entries.entrySet())
		{
			amount += marketEntry.getValue().getModifiedValue();
		}
		return amount;
	}
	
	/**
	 * Returns the demand MutableStat for the specified market and condition
	 * @param market
	 * @param commodityId
	 * @return
	 */
	public MutableStat getDemandForMarket(MarketAPI market, String commodityId)
	{
		Map<MarketAPI, MutableStat> entries = getDemandEntries(commodityId);
		if (!entries.containsKey(market))
			entries.put(market, new MutableStat(0));
		return entries.get(market);
	}
	
	/**
	 * Returns the supply MutableStat for the specified market and condition
	 * @param market
	 * @param commodityId
	 * @return
	 */
	public MutableStat getSupplyForMarket(MarketAPI market, String commodityId)
	{
		Map<MarketAPI, MutableStat> entries = getSupplyEntries(commodityId);
		if (!entries.containsKey(market))
			entries.put(market, new MutableStat(0));
		return entries.get(market);
	}
	
	/**
	 * Multiplies the supply of all commodities with one or more of the specified tags on a given market
	 * @param condId
	 * @param market
	 * @param tags
	 * @param mult
	 */
	public void modifySupplyWithTags(String condId, MarketAPI market, Set<String> tags, float mult)
	{
		for (String commodityId : Global.getSector().getEconomy().getAllCommodityIds())
		{
			if (market.isIllegal(commodityId)) continue;
			CommoditySpecAPI spec = Global.getSector().getEconomy().getCommoditySpec(commodityId);
			if (!spec.isPrimary()) continue;
			for (String tag : tags)
			{
				if (spec.hasTag(tag))
				{
					getSupplyForMarket(market, commodityId).modifyMult(condId, mult);
					break;
				}
			}
		}
	}
	
	/**
	 * Updates the market's supply/demand for affected commodities in adding the specified condition 
	 * @param market
	 * @param cond
	 */
	public void onAddMarketCondition(MarketAPI market, MarketConditionAPI cond)
	{
		String id = cond.getIdForPluginModifications();
		float sizeMult = ((BaseMarketConditionPlugin)cond.getPlugin()).getBaseSizeMult();
		float pop = ((BaseMarketConditionPlugin)cond.getPlugin()).getPopulation(market);
		float fuel = 0;
		Set<String> tags = null;
		
		if (FARMING_MULTS.containsKey(cond.getId()))
		{
			getSupplyForMarket(market, Commodities.FOOD).modifyFlat(id, getFarmingFood(cond.getId(), sizeMult));
		}
		else if (cond.getId().startsWith("population_"))
		{
			getDemandForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.POPULATION_SUPPLIES * sizeMult);
			getDemandForMarket(market, Commodities.FUEL).modifyFlat(id, ConditionData.POPULATION_FUEL * sizeMult);
			if (market.getSize() >= 4)
			{
				getDemandForMarket(market, Commodities.FOOD).modifyFlat(id, ConditionData.POPULATION_FOOD * sizeMult);
				getDemandForMarket(market, Commodities.DOMESTIC_GOODS).modifyFlat(id, ConditionData.POPULATION_DOMESTIC_GOODS * sizeMult);
				getDemandForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.POPULATION_WEAPONS * sizeMult);
				getDemandForMarket(market, Commodities.DRUGS).modifyFlat(id, ConditionData.POPULATION_DRUGS * sizeMult);

				getSupplyForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.POPULATION_GREEN_CREW * sizeMult);
			}
			if (market.getSize() >= 5)
			{
				getDemandForMarket(market, Commodities.LUXURY_GOODS).modifyFlat(id, ConditionData.POPULATION_LUXURY_GOODS * sizeMult);
				getDemandForMarket(market, Commodities.ORGANS).modifyFlat(id, ConditionData.POPULATION_ORGANS * sizeMult);
			}
		}
		else
		{
			switch (cond.getId())
			{
				case Conditions.ANTIMATTER_FUEL_PRODUCTION:
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.FUEL_PRODUCTION_ORGANICS * sizeMult);
					getDemandForMarket(market, Commodities.VOLATILES).modifyFlat(id, ConditionData.FUEL_PRODUCTION_VOLATILES * sizeMult);
					getDemandForMarket(market, Commodities.RARE_METALS).modifyFlat(id, ConditionData.FUEL_PRODUCTION_RARE_METALS * sizeMult);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.FUEL_PRODUCTION_MACHINERY * sizeMult);

					getSupplyForMarket(market, Commodities.FUEL).modifyFlat(id, ConditionData.FUEL_PRODUCTION_FUEL * sizeMult);
					break;
				case Conditions.AUTOFAC_HEAVY_INDUSTRY:
					getDemandForMarket(market, Commodities.METALS).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_METALS * sizeMult);
					getDemandForMarket(market, Commodities.RARE_METALS).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_RARE_METALS * sizeMult);
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_ORGANICS * sizeMult);
					getDemandForMarket(market, Commodities.VOLATILES).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_VOLATILES * sizeMult);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_MACHINERY_DEMAND * sizeMult);

					getSupplyForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_SUPPLIES * sizeMult);
					getSupplyForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_MACHINERY * sizeMult);
					getSupplyForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.AUTOFAC_HEAVY_HAND_WEAPONS * sizeMult);
					break;
				case Conditions.COTTAGE_INDUSTRY:
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.COTTAGE_INDUSTRY_ORGANICS * sizeMult);
					getSupplyForMarket(market, Commodities.DOMESTIC_GOODS).modifyFlat(id, ConditionData.COTTAGE_INDUSTRY_DOMESTIC_GOODS * sizeMult);
					break;
				case Conditions.CRYOSANCTUM:
					getDemandForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.CRYOSANCTUM_CREW_MULT * pop 
							* ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.CRYOSANCTUM_ORGANICS_MULT * pop);
					getDemandForMarket(market, Commodities.VOLATILES).modifyFlat(id, ConditionData.CRYOSANCTUM_VOLATILES_MULT * pop);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.CRYOSANCTUM_MACHINERY_MULT * pop);

					getSupplyForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.CRYOSANCTUM_CREW);
					getSupplyForMarket(market, Commodities.ORGANS).modifyFlat(id, ConditionData.CRYOSANCTUM_ORGANS);
					break;
				case Conditions.DECIVILIZED:
					getDemandForMarket(market, Commodities.DOMESTIC_GOODS).modifyMult(id, ConditionData.DECIV_GOODS_PENALTY);
					getDemandForMarket(market, Commodities.LUXURY_GOODS).modifyMult(id, ConditionData.DECIV_GOODS_PENALTY);
					getDemandForMarket(market, Commodities.ORGANS).modifyMult(id, ConditionData.DECIV_GOODS_PENALTY);
					getDemandForMarket(market, Commodities.DRUGS).modifyMult(id, ConditionData.DECIV_DRUGS_MULT);

					getDemandForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.DECIV_WEAPONS_MULT * pop);
					getDemandForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.DECIV_SUPPLIES * pop);

					tags = new HashSet<>(Arrays.asList(new String [] {
						Commodities.TAG_HEAVY_INDUSTRY_IN,
						Commodities.TAG_HEAVY_INDUSTRY_OUT,
						Commodities.TAG_LIGHT_INDUSTRY_IN,
						Commodities.TAG_LIGHT_INDUSTRY_OUT,
						Commodities.TAG_REFINING_IN, 
						Commodities.TAG_REFINING_OUT, 
					}));
					modifySupplyWithTags(id, market, tags, ConditionData.DECIV_PRODUCTION_PENALTY);
					getSupplyForMarket(market, Commodities.FOOD).modifyMult(id, ConditionData.DECIV_FOOD_PENALTY);
					break;
				case Conditions.DISSIDENT:
					getDemandForMarket(market, Commodities.MARINES).modifyFlat(id, ConditionData.DISSIDENT_MARINES_MULT * pop);
					getDemandForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.DISSIDENT_WEAPONS_MULT * pop);
					getSupplyForMarket(market, Commodities.CREW).modifyMult(id, ConditionData.DISSIDENT_CREW_MULT);
					break;
				case Conditions.FREE_PORT:
					getDemandForMarket(market, Commodities.DRUGS).modifyFlat(id, ConditionData.FREE_PORT_DRUGS * sizeMult);
					break;
				case Conditions.FRONTIER:
					getDemandForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.FRONTIER_WEAPONS * pop);
					getDemandForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.FRONTIER_SUPPLIES * pop);
					getDemandForMarket(market, Commodities.LUXURY_GOODS).modifyMult(id, ConditionData.FRONTIER_LUXURY_PENALTY);
					break;
				case Conditions.HYDROPONICS_COMPLEX:
					getSupplyForMarket(market, Commodities.FOOD).modifyFlat(id, ConditionData.HYDROPONICS_COMPLEX_FOOD * sizeMult);
					break;
				case Conditions.LARGE_REFUGEE_POPULATION:
					getSupplyForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.REFUGEE_GREEN_CREW_MIN);
					getSupplyForMarket(market, Commodities.CREW).modifyMult(id, ConditionData.REFUGEE_GREEN_CREW_MULT);
					getSupplyForMarket(market, Commodities.ORGANS).modifyFlat(id, pop * 0.00001f);
					tags = new HashSet<>(Arrays.asList(new String [] {
						Commodities.TAG_FOOD,
						Commodities.TAG_LIGHT_INDUSTRY_OUT,
					}));
					modifySupplyWithTags(id, market, tags, ConditionData.REFUGEE_POPULATION_PENALTY);
					break;
				case Conditions.LIGHT_INDUSTRIAL_COMPLEX:
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.LIGHT_INDUSTRY_ORGANICS * sizeMult);
					getDemandForMarket(market, Commodities.VOLATILES).modifyFlat(id, ConditionData.LIGHT_INDUSTRY_VOLATILES * sizeMult);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.LIGHT_INDUSTRY_MACHINERY * sizeMult);
					getSupplyForMarket(market, Commodities.DOMESTIC_GOODS).modifyFlat(id, ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS * sizeMult);
					getSupplyForMarket(market, Commodities.LUXURY_GOODS).modifyFlat(id, ConditionData.LIGHT_INDUSTRY_LUXURY_GOODS * sizeMult);
					break;
				case Conditions.LUDDIC_MAJORITY:
					getDemandForMarket(market, Commodities.LUXURY_GOODS).modifyMult(id, ConditionData.LUDDIC_MAJORITY_LUXURY_MULT);
					break;
				case Conditions.MILITARY_BASE:
					getDemandForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.MILITARY_BASE_SUPPLIES * 0.5f);
					getDemandForMarket(market, Commodities.FUEL).modifyFlat(id, ConditionData.MILITARY_BASE_FUEL * 0.5f);
					getDemandForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.MILITARY_BASE_WEAPONS);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.MILITARY_BASE_MACHINERY);
					getDemandForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.MILITARY_BASE_CREW_DEMAND 
							* ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
					getDemandForMarket(market, Commodities.MARINES).modifyFlat(id, ConditionData.MILITARY_BASE_MARINES_DEMAND 
							* ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);

					getSupplyForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.MILITARY_BASE_CREW_SUPPLY);
					getSupplyForMarket(market, Commodities.MARINES).modifyFlat(id, ConditionData.MILITARY_BASE_MARINES_SUPPLY);
					break;
				case Conditions.ORBITAL_BURNS:
					getSupplyForMarket(market, Commodities.FOOD).modifyMult(id, ConditionData.ORBITAL_BURNS_FOOD_BONUS);
					break;
				case Conditions.ORBITAL_STATION:
					fuel = ConditionData.ORBITAL_STATION_FUEL_BASE + ConditionData.ORBITAL_STATION_FUEL_MULT * pop * 0.5f;
					fuel = Math.min(fuel, ConditionData.ORBITAL_STATION_FUEL_MAX);
					getDemandForMarket(market, Commodities.FUEL).modifyFlat(id, fuel);
					getDemandForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.ORBITAL_STATION_SUPPLIES * 0.5f);
					getDemandForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.ORBITAL_STATION_CREW 
							* ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
					break;
				case Conditions.ORE_COMPLEX:
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.ORE_MINING_MACHINERY * sizeMult);
					getSupplyForMarket(market, Commodities.ORE).modifyFlat(id, ConditionData.ORE_MINING_ORE * sizeMult);
					getSupplyForMarket(market, Commodities.RARE_ORE).modifyFlat(id, ConditionData.ORE_MINING_RARE_ORE * sizeMult);
					break;
				case Conditions.ORE_REFINING_COMPLEX:
					float ore = ConditionData.ORE_REFINING_ORE * sizeMult;
					float rareOre = ConditionData.ORE_REFINING_RARE_ORE * sizeMult;
					getDemandForMarket(market, Commodities.ORE).modifyFlat(id, ore);
					getDemandForMarket(market, Commodities.RARE_ORE).modifyFlat(id, rareOre);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.ORE_REFINING_MACHINERY * sizeMult);

					getSupplyForMarket(market, Commodities.METALS).modifyFlat(id, ore * ConditionData.ORE_REFINING_METAL_PER_ORE);
					getSupplyForMarket(market, Commodities.RARE_METALS).modifyFlat(id, rareOre * ConditionData.ORE_REFINING_METAL_PER_ORE);
					break;
				case Conditions.ORGANICS_COMPLEX:
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.ORGANICS_MINING_MACHINERY * sizeMult);
					getSupplyForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.ORGANICS_MINING_ORGANICS * sizeMult);
					break;
				case Conditions.ORGANIZED_CRIME:
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, ConditionData.ORGANIZED_CRIME_ORGANICS
							* sizeMult * ASSUMED_STABILITY_MULT);
					getDemandForMarket(market, Commodities.VOLATILES).modifyFlat(id, ConditionData.ORGANIZED_CRIME_VOLATILES 
							* sizeMult * ASSUMED_STABILITY_MULT);
					getDemandForMarket(market, Commodities.MARINES).modifyFlat(id, ConditionData.ORGANIZED_CRIME_MARINES);
					getDemandForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, ConditionData.ORGANIZED_CRIME_WEAPONS * sizeMult);

					getSupplyForMarket(market, Commodities.DRUGS).modifyFlat(id, ConditionData.ORGANIZED_CRIME_DRUGS * sizeMult);
					getSupplyForMarket(market, Commodities.DRUGS).modifyFlat(id, ConditionData.ORGANIZED_CRIME_ORGANS * sizeMult);
					break;
				case Conditions.RURAL_POLITY:
					getDemandForMarket(market, Commodities.DOMESTIC_GOODS).modifyMult(id, ConditionData.RURAL_POLITY_DEMAND_MULT);
					getDemandForMarket(market, Commodities.LUXURY_GOODS).modifyMult(id, ConditionData.RURAL_POLITY_DEMAND_MULT);
					getDemandForMarket(market, Commodities.DRUGS).modifyMult(id, ConditionData.RURAL_POLITY_DEMAND_MULT);
					getDemandForMarket(market, Commodities.ORGANS).modifyMult(id, ConditionData.RURAL_POLITY_DEMAND_MULT);

					getSupplyForMarket(market, Commodities.FOOD).modifyMult(id, ConditionData.RURAL_POLITY_FOOD_BONUS);
					break;
				case Conditions.SHIPBREAKING_CENTER:
					getSupplyForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.SHIPBREAKING_MACHINERY);
					getSupplyForMarket(market, Commodities.METALS).modifyFlat(id, ConditionData.SHIPBREAKING_METALS);
					getSupplyForMarket(market, Commodities.RARE_METALS).modifyFlat(id, ConditionData.SHIPBREAKING_RARE_METALS);
					getSupplyForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.SHIPBREAKING_SUPPLIES);
					break;
				case Conditions.SPACEPORT:
					fuel = ConditionData.SPACEPORT_FUEL_BASE + ConditionData.SPACEPORT_FUEL_MULT * pop;
					fuel = Math.min(fuel, ConditionData.SPACEPORT_FUEL_MAX);
					getDemandForMarket(market, Commodities.FUEL).modifyFlat(id, fuel);
					getDemandForMarket(market, Commodities.SUPPLIES).modifyFlat(id, ConditionData.SPACEPORT_SUPPLIES);
					getDemandForMarket(market, Commodities.CREW).modifyFlat(id, ConditionData.SPACEPORT_CREW
							* ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
					break;
				case Conditions.VOLATILES_COMPLEX:
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, ConditionData.VOLATILES_MINING_MACHINERY * sizeMult);
					getSupplyForMarket(market, Commodities.VOLATILES).modifyFlat(id, ConditionData.VOLATILES_MINING_VOLATILES * sizeMult);
					break;
				case Conditions.VOLTURNIAN_LOBSTER_PENS:
					getSupplyForMarket(market, Commodities.LUXURY_GOODS).modifyFlat(id, ConditionData.VOLTURNIAN_LOBSTER_PENS_LOBSTER * sizeMult);
					break;
				case "exerelin_supply_workshop":
					getDemandForMarket(market, Commodities.METALS).modifyFlat(id, SupplyWorkshop.WORKSHOP_METALS * sizeMult);
					getDemandForMarket(market, Commodities.RARE_METALS).modifyFlat(id, SupplyWorkshop.WORKSHOP_RARE_METALS * sizeMult);
					getDemandForMarket(market, Commodities.ORGANICS).modifyFlat(id, SupplyWorkshop.WORKSHOP_ORGANICS * sizeMult);
					getDemandForMarket(market, Commodities.VOLATILES).modifyFlat(id, SupplyWorkshop.WORKSHOP_VOLATILES * sizeMult);
					getDemandForMarket(market, Commodities.HEAVY_MACHINERY).modifyFlat(id, SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY_DEMAND * sizeMult);

					getSupplyForMarket(market, Commodities.SUPPLIES).modifyFlat(id, SupplyWorkshop.WORKSHOP_SUPPLIES * sizeMult);
					getSupplyForMarket(market, Commodities.HAND_WEAPONS).modifyFlat(id, SupplyWorkshop.WORKSHOP_HAND_WEAPONS * sizeMult);
					break;
			}
		}
	}
	
	public void addMarketCondition(ProcGenEntity entity, String conditionId)
	{
		String token = entity.market.addCondition(conditionId);
		onAddMarketCondition(entity.market, entity.market.getSpecificCondition(token));
	}
	
	/**
	 * Updates global supply/demand in removing the specified condition
	 * @param market
	 * @param cond
	 */
	public void onRemoveMarketCondition(MarketAPI market, MarketConditionAPI cond)
	{
		for (Map.Entry<String, Map<MarketAPI, MutableStat>> commodityEntry : demandByCommodity.entrySet())
		{
			for (Map.Entry<MarketAPI, MutableStat> marketEntry : commodityEntry.getValue().entrySet())
				marketEntry.getValue().unmodify(cond.getIdForPluginModifications());
		}
		for (Map.Entry<String, Map<MarketAPI, MutableStat>> commodityEntry : supplyByCommodity.entrySet())
		{
			for (Map.Entry<MarketAPI, MutableStat> marketEntry : commodityEntry.getValue().entrySet())
				marketEntry.getValue().unmodify(cond.getIdForPluginModifications());
		}
	}
	
	protected void removeMarketCondition(ProcGenEntity entity, String conditionId)
	{
		MarketConditionAPI cond = entity.market.getFirstCondition(conditionId);
		onRemoveMarketCondition(entity.market, cond);
		builder.removeMarketCondition(entity, cond);
	}
	
	/**
	 * Gets the base farming output for the specified market and planet type condition
	 * @param conditionId
	 * @param sizeMult
	 * @return
	 */
	public float getFarmingFood(String conditionId, float sizeMult)
	{
		float mult = 0;
		if (FARMING_MULTS.containsKey(conditionId))
			mult = FARMING_MULTS.get(conditionId);
		
		return 500 * 2 * mult * sizeMult;
	}
	
	// =========================================================================
	// =========================================================================
	// Balancer functions
	
	
	protected void balanceDomesticGoods(List<ProcGenEntity> candidateEntities)
	{	
		String comId = Commodities.DOMESTIC_GOODS;
		log.info("Pre-balance domestic goods supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		for (ProcGenEntity entity : candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			if (market.hasCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX)) 
			{
				// oversupply; remove this LIC and prioritise the market for any readding later
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, Conditions.LIGHT_INDUSTRIAL_COMPLEX);
					weight *= 25;
					log.info("Removed balancing Light Industrial Complex from " + market.getName() + " (size " + size + ")");
				}
			}
			if (market.hasCondition(Conditions.COTTAGE_INDUSTRY)) weight *= 0.25f;
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.LIGHT_INDUSTRIAL_COMPLEX, entity.archetype, 0.1f);
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
						
			ProcGenEntity entity = entityPicker.pickAndRemove();
			addMarketCondition(entity, Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			log.info("Added balancing Light Industrial Complex to " + entity.market.getName());
		}
		log.info("Final domestic goods supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceRareMetal(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.RARE_METALS;
		
		log.info("Pre-balance rare metal supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			if (size <= 4) continue;
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			
			if (market.hasCondition(Conditions.SHIPBREAKING_CENTER)) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition( entity, Conditions.SHIPBREAKING_CENTER);
					log.info("Removed balancing shipbreaking center from " + market.getName());
					weight *= 100;
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.SHIPBREAKING_CENTER, entity.archetype, 0.1f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			if (weight == 0) continue;
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * 1.2)
		{
			if (entityPicker.isEmpty())	break;
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			addMarketCondition(entity, Conditions.SHIPBREAKING_CENTER);
			log.info("Added balancing shipbreaking center to " + entity.market.getName());
		}
		log.info("Final rare metal supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceMachinery(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.HEAVY_MACHINERY;
		
		log.info("Pre-balance machinery supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			if (size <= 3) continue;
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			
			if (market.hasCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY)) 
			{
				// hax bounds a bit, to keep it from removing autofacs from everything
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY * 1.2f)	
				{
					removeMarketCondition(entity, Conditions.AUTOFAC_HEAVY_INDUSTRY);
					log.info("Removed balancing heavy autofactory from " + market.getName());
					weight *= 100;
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.AUTOFAC_HEAVY_INDUSTRY, entity.archetype, 0.1f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			if (weight == 0) continue;
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			addMarketCondition(entity, Conditions.AUTOFAC_HEAVY_INDUSTRY);
			log.info("Added balancing heavy autofac to " + market.getName());
		}
		
		log.info("Final machinery supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceSupplies(List<ProcGenEntity> candidateEntities)
	{		
		String comId = Commodities.SUPPLIES;
		
		log.info("Pre-balance supplies supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			
			if (market.hasCondition("exerelin_supply_workshop") && !entity.isHQ) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, "exerelin_supply_workshop");
					log.info("Removed balancing supply workshop from " + market.getName());
					weight *= 100;
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype("exerelin_supply_workshop", entity.archetype, 0.25f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			if (weight == 0) continue;
			if (!builder.isConditionAllowed("exerelin_supply_workshop", entity)) continue;
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			addMarketCondition(entity, "exerelin_supply_workshop");
			log.info("Added balancing supply workshop to " + entity.market.getName());
		}
		log.info("Final supplies supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}

	protected void balanceFood(List<ProcGenEntity> candidateEntities)
	{		
		String comId = Commodities.FOOD;
		
		log.info("Pre-balance food supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity : candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			
			if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
			{
				if (market.hasCondition(Conditions.RURAL_POLITY) && random.nextFloat() > 0.5)
				{
					removeMarketCondition(entity, Conditions.RURAL_POLITY);
					weight *= 25;
					log.info("Removed balancing Rural Polity from " + market.getName());
				}
				else if (market.hasCondition(Conditions.HYDROPONICS_COMPLEX))
				{
					removeMarketCondition(entity, Conditions.HYDROPONICS_COMPLEX);
					weight *= 25;
					log.info("Removed balancing Hydroponics Complex from " + market.getName());
				}
				else if (market.hasCondition(Conditions.ORBITAL_BURNS) && random.nextFloat() > 0.5)
				{
					removeMarketCondition(entity, Conditions.ORBITAL_BURNS);
					weight *= 25;
					log.info("Removed balancing Orbital Burns from " + market.getName());
				}
				else if (market.hasCondition(Conditions.AQUACULTURE))
				{
					removeMarketCondition(entity, Conditions.AQUACULTURE);
					weight *= 25;
					log.info("Removed balancing aquaculture from " + market.getName());
				}
			}
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.HYDROPONICS_COMPLEX, entity.archetype, 0.1f);
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			if (builder.isConditionAllowed(Conditions.ORBITAL_BURNS, entity))
			{
				addMarketCondition(entity, Conditions.ORBITAL_BURNS);
				log.info("Added balancing Orbital Burns to " + market.getName());
			}
			else if (builder.isConditionAllowed(Conditions.AQUACULTURE, entity))
			{
				addMarketCondition(entity, Conditions.AQUACULTURE);
				log.info("Added balancing Aquaculture to " + market.getName());
			}
			else if (builder.isConditionAllowed(Conditions.RURAL_POLITY, entity))
			{
				addMarketCondition(entity, Conditions.RURAL_POLITY);
				log.info("Added balancing Rural Polity to " + market.getName());
			}
			else
			{
				addMarketCondition(entity, Conditions.HYDROPONICS_COMPLEX);
				log.info("Added balancing Hydroponics Lab to " + market.getName());
			}
		}
		log.info("Final food supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceFuel(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.FUEL;
		
		log.info("Pre-balance fuel supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			if (market.hasCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION)) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, Conditions.ANTIMATTER_FUEL_PRODUCTION);
					weight *= 25;
					log.info("Removed balancing Antimatter Fuel Production from " + market.getName());
				}
			}
			
			if (size < 4) continue;
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.ANTIMATTER_FUEL_PRODUCTION, entity.archetype, 0.1f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			ProcGenEntity entity = entityPicker.pickAndRemove();			
			addMarketCondition(entity, Conditions.ANTIMATTER_FUEL_PRODUCTION);
			log.info("Added balancing Antimatter Fuel Production to " + entity.market.getName());
		}
		log.info("Final fuel supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceOrganics(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.ORGANICS;
		
		log.info("Pre-balance organics supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			if (market.hasCondition(Conditions.ORGANICS_COMPLEX)) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, Conditions.ORGANICS_COMPLEX);
					weight *= 25;
					log.info("Removed balancing Organics Complex from " + market.getName());
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.ORGANICS_COMPLEX, entity.archetype, 0.1f);
			weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			addMarketCondition(entity, Conditions.ORGANICS_COMPLEX);
			log.info("Added balancing Organics Complex to " + entity.market.getName());
		}
		log.info("Final organics supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceVolatiles(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.VOLATILES;
		
		log.info("Pre-balance volatiles supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			if (market.hasCondition(Conditions.VOLATILES_COMPLEX)) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, Conditions.VOLATILES_COMPLEX);
					weight *= 25;
					log.info("Removed balancing Volatiles Complex from " + market.getName());
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.VOLATILES_COMPLEX, entity.archetype, 0.1f);
			weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			addMarketCondition(entity, Conditions.VOLATILES_COMPLEX);
			log.info("Added balancing Volatiles Complex to " + entity.market.getName());
		}
		log.info("Final volatiles supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceMetal(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.METALS;
		
		log.info("Pre-balance metal supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			if (market.hasCondition(Conditions.ORE_REFINING_COMPLEX)) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, Conditions.ORE_REFINING_COMPLEX);
					weight *= 25;
					log.info("Removed balancing Ore Refining Complex from " + market.getName());
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.ORE_REFINING_COMPLEX, entity.archetype, 0.1f);
			//weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			addMarketCondition(entity, Conditions.ORE_REFINING_COMPLEX);
			log.info("Added balancing Ore Refining Complex to " + market.getName());
		}
		log.info("Final metal supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	protected void balanceOre(List<ProcGenEntity> candidateEntities)
	{
		String comId = Commodities.DOMESTIC_GOODS;
		
		log.info("Pre-balance ore supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 200);
			if (market.hasCondition(Conditions.ORE_COMPLEX)) 
			{
				if (getSupply(comId) > getDemand(comId) * MAX_OVERSUPPLY)
				{
					removeMarketCondition(entity, Conditions.ORE_COMPLEX);
					weight *= 25;
					log.info("Removed balancing Ore Complex from " + market.getName());
				}
			}
			
			weight *= ExerelinMarketBuilder.getConditionWeightForArchetype(Conditions.ORE_COMPLEX, entity.archetype, 0.1f);
			weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (getDemand(comId) > getSupply(comId) * MAX_OVERDEMAND)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			ProcGenEntity entity = entityPicker.pickAndRemove();			
			addMarketCondition(entity, Conditions.ORE_COMPLEX);
			log.info("Added balancing Ore Complex to " + entity.market.getName());
		}
		log.info("Final ore supply/demand: " + (int)getSupply(comId) + " / " + (int)getDemand(comId));
	}
	
	public void reportSupplyDemand()
	{
		String[] commodities = {Commodities.SUPPLIES, Commodities.FUEL, Commodities.DOMESTIC_GOODS, Commodities.FOOD, Commodities.HEAVY_MACHINERY, 
			Commodities.METALS,Commodities.RARE_METALS,	Commodities.ORE, Commodities.RARE_ORE, Commodities.ORGANICS, Commodities.VOLATILES};
		for (String commodity : commodities)
		{
			if (!demandByCommodity.containsKey(commodity) || !supplyByCommodity.containsKey(commodity))
				continue;
			float supply = getSupply(commodity);
			float demand = getDemand(commodity);
			log.info("\t" + commodity.toUpperCase() + " supply / demand: " + (int)supply + " / " + (int)demand);
		}
	}
}
