package exerelin.world;

import java.util.List;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.econ.ExerelinShipbreakingCenter;
import data.scripts.campaign.econ.Exerelin_Hydroponics;
import data.scripts.campaign.econ.Exerelin_RecyclingPlant;
import data.scripts.campaign.econ.Exerelin_SupplyWorkshop;
import exerelin.campaign.fleets.ExerelinLionsGuardFleetManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.world.ExerelinSectorGen.EntityData;
import exerelin.world.ExerelinSectorGen.EntityType;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

/**
 * There are several possible market archetypes: Agriculture, Ore, Organics, Volatiles, Manufacturing, Heavy Industry
 * Pick a market archetype for each market on planet creation
 * Archetypes are picked in a sequential rotation (so the number of markets with each archetype will be largely even)
 * Each archetype gets a certain selection of planet types to randomly pick from
 * Each market gets a number of points to spend on market condition based on market size
 * Each market condition has a cost and a number of weightings based on the market
 * For each market, randomly pick a valid condition and spend points on it; repeat till out of points
 * Available points by market size:
 *	Size 2: 200
 *	Size 3: 250
 *	Size 4: 350
 *	Size 5: 450
 *	Size 6: 650
 *	Size 7: 850
 * Also have chance for bonus points based on market size
 * Special conditions allowed:
 *	Size 2: random(0, 1)
 *	Size 3-4: random(0,1) + random(0,1)
 *	Size 5-6: 1 + random(0,1)
 *	Size 7: 1 + random(0,1) + random(0,1)
 * 
 * Once it's done assigning markets, it does a second pass
 * Add/remove market conditions to balance supply and demand of domestic goods, metal and supplies
*/

@SuppressWarnings("unchecked")
public class ExerelinMarketSetup
{
	public static Logger log = Global.getLogger(ExerelinMarketSetup.class);
	//private List possibleMarketConditions;
	
	protected static final String CONFIG_FILE = "data/config/exerelin/marketConfig.json";
	
	protected final Map<MarketArchetype, Map<String, Float>> planetsForArchetypes = new HashMap<>();
	protected final List<MarketConditionDef> conditions = new ArrayList<>();
	protected final Map<String, MarketConditionDef> conditionsByID = new HashMap<>();
	protected final List<MarketConditionDef> specialConditions = new ArrayList<>();
	
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MIN = 1.3f;
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MAX = 0.5f;	// lower than min so it can swap autofacs for shipbreakers if needed
	
	protected static final int[] PLANET_SIZE_ROTATION = new int[] {4, 5, 6, 5};
	protected static final int[] MOON_SIZE_ROTATION = new int[] {3, 4, 5};
	protected static final int[] STATION_SIZE_ROTATION = new int[] {3, 4, 5};
	
	protected Map<String, Float> commodityDemand = new HashMap<>();
	protected Map<String, Float> commoditySupply = new HashMap<>();
	
	protected Map<MarketArchetype, Integer> numMarketsByArchetype = new HashMap<>();
	protected WeightedRandomPicker<MarketArchetype> marketArchetypeQueue = new WeightedRandomPicker<>();
	protected int marketArchetypeQueueNum = 0;
	
	protected int numStations = 0;
	protected int numPlanets = 0;
	protected int numMoons = 0;
	
	protected final ExerelinSectorGen sectorGen;
	
	public ExerelinMarketSetup(ExerelinSectorGen gen)
	{
		sectorGen = gen;
		
		try {
			JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
			JSONObject planetsJson = config.getJSONObject("planetsForArchetypes");
			Iterator<?> keys = planetsJson.keys();
			while (keys.hasNext()) {
				String archetypeStr = (String)keys.next();
				MarketArchetype archetype = MarketArchetype.valueOf(archetypeStr.toUpperCase());
				
				Map<String, Float> planetChances = new HashMap<>();
				JSONObject planetChancesJson = planetsJson.getJSONObject(archetypeStr);
				Iterator<?> keys2 = planetChancesJson.keys();
				while (keys2.hasNext())
				{
					String planetType = (String)keys2.next();
					float chance = (float)planetChancesJson.getDouble(planetType);
					planetChances.put(planetType, chance);
				}
				planetsForArchetypes.put(archetype, planetChances);
			}
			
			JSONArray conditionsJson = config.getJSONArray("conditions");
			for(int i=0; i<conditionsJson.length(); i++)
			{
				JSONObject condJson = conditionsJson.getJSONObject(i);
				String name = condJson.getString("name");
				
				String requiredFaction = condJson.optString("requiredFaction","");
				if (!requiredFaction.isEmpty() && Global.getSector().getFaction(requiredFaction) == null)
					continue;
				
				MarketConditionDef cond = new MarketConditionDef(name);
				cond.cost = condJson.optInt("cost", 0);
				cond.special = condJson.optBoolean("special", false);
				cond.minSize = condJson.optInt("minSize", 0);
				cond.maxSize = condJson.optInt("maxSize", 99);
				cond.allowStations = condJson.optBoolean("allowStations", true);
				cond.allowDuplicates = condJson.optBoolean("allowDuplicates", true);
				cond.requiredFaction = requiredFaction;
				
				if (condJson.has("allowedPlanets"))
				{
					cond.allowedPlanets.addAll(ExerelinUtils.JSONArrayToArrayList(condJson.getJSONArray("allowedPlanets")));
				}
				if (condJson.has("disallowedPlanets"))
				{
					cond.allowedPlanets.addAll(ExerelinUtils.JSONArrayToArrayList(condJson.getJSONArray("disallowedPlanets")));
				}
				if (condJson.has("conflictsWith"))
				{
					cond.conflictsWith.addAll(ExerelinUtils.JSONArrayToArrayList(condJson.getJSONArray("conflictsWith")));
				}
				
				if (condJson.has("archetypes"))
				{
					JSONObject archetypesJson = condJson.getJSONObject("archetypes");
					float defaultWeight = (float)archetypesJson.optDouble("default", 0);
					for (MarketArchetype possibleArchetype : MarketArchetype.values())
					{
						float weight = (float)archetypesJson.optDouble(possibleArchetype.name().toLowerCase(), defaultWeight);
						cond.archetypes.put(possibleArchetype, weight);
					}
				}
				else
				{
					for (MarketArchetype possibleArchetype : MarketArchetype.values())
					{
						cond.archetypes.put(possibleArchetype, 1f);
					}
				}
				
				if (cond.special) specialConditions.add(cond);
				else {
					conditions.add(cond);
					conditionsByID.put(name, cond);
				}
				
			}
			
		} catch (IOException | JSONException ex) {
			log.error(ex);
		}
	}
	
	protected float getConditionWeightForArchetype(MarketConditionDef cond, MarketArchetype archetype, float defaultWeight)
	{
		float weight = cond.archetypes.get(archetype);
		if (weight <= 0) weight = defaultWeight;
		return weight;
	}
	
	protected float getConditionWeightForArchetype(String condID, MarketArchetype archetype, float defaultWeight)
	{
		if (!conditionsByID.containsKey(condID)) return defaultWeight;
		return getConditionWeightForArchetype(conditionsByID.get(condID), archetype, defaultWeight);
	}
	
	protected MarketConditionDef pickMarketCondition(MarketAPI market, List<MarketConditionDef> possibleConds, EntityData entityData, int budget, boolean isFirst)
	{
		WeightedRandomPicker<MarketConditionDef> picker = new WeightedRandomPicker<>();
		int numConds = 0;
		int size = market.getSize();
		String planetType = entityData.planetType;
		boolean isStation = entityData.type == EntityType.STATION;
		
		if (isFirst) budget += 100;	// make sure small economies can get at least one market condition
		
		for (MarketConditionDef possibleCond : possibleConds) 
		{
			if (possibleCond.cost > budget) continue;
			if (possibleCond.minSize > size || possibleCond.maxSize < size) continue;
			if (!possibleCond.allowStations && isStation) continue;
			if (!possibleCond.allowDuplicates && market.hasCondition(possibleCond.name)) continue;
			if (!possibleCond.allowedPlanets.isEmpty())
			{
				if (!possibleCond.allowedPlanets.contains(planetType))
					continue;
			}
			if (!possibleCond.disallowedPlanets.isEmpty())
			{
				if (possibleCond.disallowedPlanets.contains(planetType))
					continue;
			}
			for (String conflict : possibleCond.conflictsWith)
			{
				if (market.hasCondition(conflict)) continue;
			}
			float weight = getConditionWeightForArchetype(possibleCond, entityData.archetype, 0);
			if (weight <= 0) continue;
			
			picker.add(possibleCond, weight);
			numConds++;
		}
		
		if (numConds == 0)
			return null;	// out of possible conditions; nothing more to do
		
		return picker.pick();
	}
	
	public void addMarketCondition(MarketAPI market, EntityData entityData, MarketConditionDef cond)
	{
		market.addCondition(cond.name);
		entityData.marketPointsSpent += cond.cost;
	}
	
	public void addMarketCondition(MarketAPI market, EntityData entityData, String cond)
	{
		addMarketCondition(market, entityData, conditionsByID.get(cond));
	}
	
	public void removeMarketCondition(MarketAPI market, EntityData entityData, MarketConditionDef cond)
	{
		ExerelinUtilsMarket.removeOneMarketCondition(market, cond.name);
		entityData.marketPointsSpent -= cond.cost;
	}
	
	public void removeMarketCondition(MarketAPI market, EntityData entityData, String cond)
	{
		removeMarketCondition(market, entityData, conditionsByID.get(cond));
	}
	
	public void initMarketPointsAndAddRandomConditions(MarketAPI market, EntityData entityData)
	{
		log.info("Processing market conditions for " + market.getPrimaryEntity().getName() + " (" + market.getFaction().getDisplayName() + ")");
		
		int size = market.getSize();
		int points = 200;
		if (size == 3) points = 250;
		else if (size == 4) points = 350;
		else if (size == 5) points = 450;
		else if (size == 6) points = 650;
		else if (size >= 7) points = 850;
		
		int bonusPoints = 0;
		for (int i=0; i<size-1; i++)
		{
			if (Math.random() > 0.4) bonusPoints += 50;
		}
				
		entityData.bonusMarketPoints = bonusPoints;
		points += bonusPoints;
		entityData.marketPoints = points;
		
		while (points - entityData.marketPointsSpent > points/2)
		{
			MarketConditionDef cond = pickMarketCondition(market, conditions, entityData, (int)(points*0.7 - entityData.marketPointsSpent), false);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			addMarketCondition(market, entityData, cond);
		}
		
		int numSpecial = 0;
		if (size == 2 && Math.random() > 0.5) numSpecial = 1;
		else if (size <= 4) numSpecial = MathUtils.getRandomNumberInRange(0, 1) + MathUtils.getRandomNumberInRange(0, 1);
		else if (size <= 6) numSpecial = 1 + MathUtils.getRandomNumberInRange(0, 1);
		else if (size <= 8) numSpecial = 1 + MathUtils.getRandomNumberInRange(0, 1) + MathUtils.getRandomNumberInRange(0, 1);
		
		for (int i=0; i<numSpecial; i++)
		{
			MarketConditionDef cond = pickMarketCondition(market, specialConditions, entityData, 0, false);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			addMarketCondition(market, entityData, cond);
		}
	}
	
	// =========================================================================
	// archetype handling
	
	protected String pickPlanetTypeFromArchetype(MarketArchetype archetype, boolean isMoon)
	{
		Map<String, Float> types = planetsForArchetypes.get(archetype);
		log.info("Getting planet for archetype " + archetype.name());
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		Iterator<Map.Entry<String, Float>> iter = types.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<String, Float> tmp = iter.next();
			String type = tmp.getKey();
			//log.info("\tPossible planet: " +  type);
			if (isMoon && type.equals("arid") || type.equals("terran") || type.equals("terran-eccentric"))
				continue;
			
			float weight = tmp.getValue();
			picker.add(type, weight);
		}
		if (picker.isEmpty()) return "desert";
		return picker.pick();
	}
	
	protected int getNumMarketsOfArchetype(MarketArchetype type)
	{
		if (!numMarketsByArchetype.containsKey(type))
		{
			numMarketsByArchetype.put(type, 0);
			return 0;
		}
		return numMarketsByArchetype.get(type);
	}
	
	// Tetris type archetype rotation
	protected void queueMarketArchetypes()
	{
		marketArchetypeQueueNum++;
		
		// always have at least one of each in the queue (usually)
		if (marketArchetypeQueueNum % 5 != 4)	// skip every fifth agriculture market
			marketArchetypeQueue.add(MarketArchetype.AGRICULTURE);
		//if (marketArchetypeQueueNum % 5 == 1)	// add an extra agriculture market every fifth round
		//	marketArchetypeQueue.add(MarketArchetype.AGRICULTURE);
		
		if (marketArchetypeQueueNum % 4 != 3)	// skip every fourth ore market
			marketArchetypeQueue.add(MarketArchetype.ORE);
		
		if (marketArchetypeQueueNum % 3 != 2)	// skip every third organics market
			marketArchetypeQueue.add(MarketArchetype.ORGANICS);
		
		if (marketArchetypeQueueNum % 3 != 1)	// skip every third volatiles market
			marketArchetypeQueue.add(MarketArchetype.VOLATILES);
		
		marketArchetypeQueue.add(MarketArchetype.MANUFACTURING);
		
		if (marketArchetypeQueueNum % 4 != 2)	// skip every fourth heavy industry market
			marketArchetypeQueue.add(MarketArchetype.HEAVY_INDUSTRY);
		
		//marketArchetypeQueue.add(MarketArchetype.MIXED);
		
		// add up to three more distinct archetypes based on how many of each already exist
		/*
		int numAgriculture = getNumMarketsOfArchetype(MarketArchetype.AGRICULTURE) + 1;
		int numOre = getNumMarketsOfArchetype(MarketArchetype.ORE) + 1;
		int numOrganics = getNumMarketsOfArchetype(MarketArchetype.ORGANICS) + 1;
		int numVolatiles = getNumMarketsOfArchetype(MarketArchetype.VOLATILES) + 1;
		int numManufacturing = getNumMarketsOfArchetype(MarketArchetype.MANUFACTURING) + 1;
		int numHeavyIndustry = getNumMarketsOfArchetype(MarketArchetype.HEAVY_INDUSTRY) + 1;
		
		WeightedRandomPicker<MarketArchetype> picker = new WeightedRandomPicker<>();
		picker.add(MarketArchetype.AGRICULTURE, 10/numAgriculture);
		picker.add(MarketArchetype.ORE, 10/numOre);
		picker.add(MarketArchetype.ORGANICS, 8/numOrganics);
		picker.add(MarketArchetype.VOLATILES, 6/numVolatiles);
		picker.add(MarketArchetype.MANUFACTURING, 10/numManufacturing);
		picker.add(MarketArchetype.HEAVY_INDUSTRY, 8/numHeavyIndustry);
		
		for (int i=0; i<MathUtils.getRandomNumberInRange(2, 3); i++)
		{
			marketArchetypeQueue.add(picker.pickAndRemove());
		}
		*/
	}
	
	protected MarketArchetype pickMarketArchetype(boolean isStation)
	{
		int tries = 0;
		while (true)
		{
			tries++;
			if (marketArchetypeQueue.isEmpty())
				queueMarketArchetypes();
			MarketArchetype type = marketArchetypeQueue.pickAndRemove();
			if (tries < 5 && isStation && type == MarketArchetype.AGRICULTURE)
				continue;
			return type;
		}
	}
	
	protected int getSizeFromRotation(int[] array, int num)
	{
		return array[num % array.length];
	}
	
	// =========================================================================
	// main market adding method
	protected MarketAPI addMarketToEntity(SectorEntityToken entity, EntityData data, String factionId)
	{
		// don't make the markets too big; they'll screw up the economy big time
		int marketSize = 1;
		EntityType entityType = data.type;
		
		String planetType = data.planetType;
		boolean isStation = entityType == EntityType.STATION; 
		boolean isMoon = entityType == EntityType.MOON;
		
		if (data.isHQ) {
			marketSize = 7;
		}
		else if (data.isCapital) {
			marketSize = 6;
		}
		else {
			if (isStation) marketSize = getSizeFromRotation(STATION_SIZE_ROTATION, numStations);
			else if (isMoon) marketSize = getSizeFromRotation(MOON_SIZE_ROTATION, numMoons);
			else marketSize = getSizeFromRotation(PLANET_SIZE_ROTATION, numPlanets);
			
			if (isStation) numStations++;
			else if (isMoon) numMoons++;
			else numPlanets++;
		}
		
		if (ExerelinUtilsFaction.isPirateFaction(factionId)) {
			marketSize--;
			if (marketSize < 3) marketSize = 3;
		}
		
		if (data.forceMarketSize != -1) marketSize = data.forceMarketSize;
		
		MarketAPI newMarket = Global.getFactory().createMarket(entity.getId() /*+ "_market"*/, entity.getName(), marketSize);
		newMarket.setPrimaryEntity(entity);
		entity.setMarket(newMarket);
		
		newMarket.setFactionId(factionId);
		newMarket.setBaseSmugglingStabilityValue(0);
		
		if (data.isHQ)
		{
			newMarket.addCondition(Conditions.HEADQUARTERS);
			//newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);	// dependent on number of factions; bad idea
			newMarket.addCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			newMarket.addCondition("exerelin_recycling_plant");
			newMarket.addCondition("exerelin_recycling_plant");
			newMarket.addCondition("exerelin_supply_workshop");
			newMarket.addCondition("exerelin_hydroponics");
			if (data == sectorGen.homeworld) 
			{
				//newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
				newMarket.addCondition("exerelin_supply_workshop");
				//newMarket.addCondition(Conditions.SHIPBREAKING_CENTER);
				newMarket.addCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION);
			}
		}
		else if (data.isCapital)
		{
			newMarket.addCondition(Conditions.REGIONAL_CAPITAL);
			newMarket.addCondition("exerelin_recycling_plant");
			newMarket.addCondition("exerelin_supply_workshop");
			//newMarket.addCondition("exerelin_hydroponics");
		}
		
		//newMarket.setSize(marketSize);
		newMarket.addCondition("population_" + marketSize);
		
		int minSizeForMilitaryBase = 6;
		if (ExerelinUtilsFaction.isPirateFaction(factionId))
			minSizeForMilitaryBase = 5;
		if (isMoon) minSizeForMilitaryBase = 5;
		else if (isStation) minSizeForMilitaryBase = 5;
		
		if (marketSize >= minSizeForMilitaryBase)
		{
			newMarket.addCondition(Conditions.MILITARY_BASE);
		}
		
		// planet type conditions
		if (planetType != null && !planetType.isEmpty())
		{
			//log.info("Attempting to add planet type condition: " + planetType);
			switch (planetType) {
				case "frozen":
				case "rocky_ice":
					newMarket.addCondition(Conditions.ICE);
					break;
				case "barren":
				case "rocky_metallic":
				case "barren-bombarded":
					newMarket.addCondition(Conditions.UNINHABITABLE);
					break;
				case "barren-desert":
					newMarket.addCondition("barren_marginal");
					break;	
				case "terran-eccentric":
					newMarket.addCondition("twilight");
					// add mirror/shade
					LocationAPI system = entity.getContainingLocation();
					SectorEntityToken mirror = system.addCustomEntity(entity.getId() + "_mirror", "Stellar Mirror", "stellar_mirror", factionId);
					mirror.setCircularOrbitPointingDown(entity, data.startAngle, entity.getRadius() + 150, data.orbitPeriod);
					mirror.setCustomDescriptionId("stellar_mirror");
					SectorEntityToken shade = system.addCustomEntity(entity.getId() + "_shade", "Stellar Shade", "stellar_shade", factionId);
					shade.setCircularOrbitPointingDown(entity, data.startAngle + 180, entity.getRadius() + 150, data.orbitPeriod);		
					shade.setCustomDescriptionId("stellar_shade");
					break;	
				default:
					newMarket.addCondition(planetType);
			}
		}
				
		if(marketSize <= 4 && !isStation){
			newMarket.addCondition(Conditions.FRONTIER);
		}
		
		// add random market conditions
		initMarketPointsAndAddRandomConditions(newMarket, data);

		if (isStation && marketSize >= 3)
		{
			//newMarket.addCondition("exerelin_recycling_plant");
		}
				
		// add per-faction market conditions
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		
		newMarket.getTariff().modifyFlat("default_tariff", ExerelinConfig.baseTariff);
		if (config.freeMarket)
		{
			newMarket.addCondition(Conditions.FREE_PORT);
			newMarket.getTariff().modifyMult("isFreeMarket", ExerelinConfig.freeMarketTariffMult);
		}
		
		if (factionId.equals(Factions.LUDDIC_CHURCH)) {
			newMarket.addCondition(Conditions.LUDDIC_MAJORITY);
			//newMarket.addCondition("cottage_industry");
		}
		else if (factionId.equals("spire")) {
			newMarket.addCondition("aiw_inorganic_populace");
		}
		else if (factionId.equals("crystanite")) {
			//newMarket.addCondition("crys_population");
		}
		
		if (factionId.equals("templars"))
		{
			newMarket.addSubmarket("tem_templarmarket");
			newMarket.addCondition("exerelin_templar_control");
		}
		else
		{
			newMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
			newMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
		}
		newMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		//if (marketSize >= 4)
		//	ExerelinUtilsCargo.addCommodityStockpile(newMarket, "agent", marketSize);
		
		Global.getSector().getEconomy().addMarket(newMarket);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		if (data.isHQ && factionId.equals(Factions.DIKTAT))
		{
			ExerelinLionsGuardFleetManager script = new ExerelinLionsGuardFleetManager(newMarket);
			entity.addScript(script);
		}
		
		newMarket.reapplyConditions();	// this breaks demand getter?
		
		// count some demand/supply values for market balancing
		
		//domesticGoodsSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.DOMESTIC_GOODS);
		//metalSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.METALS);
		//suppliesSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.SUPPLIES);

		int autofacCount = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.AUTOFAC_HEAVY_INDUSTRY);
		int shipbreakingCount = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.SHIPBREAKING_CENTER);
		int fuelProdCount = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ANTIMATTER_FUEL_PRODUCTION);
		int recyclingCount = ExerelinUtilsMarket.countMarketConditions(newMarket, "exerelin_recycling_plant");
		int workshopCount = ExerelinUtilsMarket.countMarketConditions(newMarket, "exerelin_supply_workshop");
		int pop = ExerelinUtilsMarket.getPopulation(marketSize);
		
		// domestic goods
		float dgSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.LIGHT_INDUSTRIAL_COMPLEX) * ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS_MULT;
		dgSupply += ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.COTTAGE_INDUSTRY) * ConditionData.COTTAGE_INDUSTRY_DOMESTIC_GOODS_MULT;
		dgSupply *= pop * ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.DOMESTIC_GOODS);
		modifyCommoditySupply(Commodities.DOMESTIC_GOODS, dgSupply);
		modifyCommodityDemand(Commodities.DOMESTIC_GOODS, pop * ConditionData.POPULATION_DOMESTIC_MULT * ExerelinUtilsMarket.getCommodityDemandMult(newMarket, Commodities.DOMESTIC_GOODS));
		
		// metal
		float mSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_ORE;
		mSupply += shipbreakingCount * ConditionData.SHIPBREAKING_METALS * ExerelinShipbreakingCenter.EXTRA_METALS_MULT;
		mSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_METALS * Exerelin_RecyclingPlant.HAX_MULT_07_METALS;
		mSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.METALS);
		modifyCommoditySupply(Commodities.METALS, mSupply);
		float mDemand = autofacCount * ConditionData.AUTOFAC_HEAVY_METALS;
		mDemand += workshopCount * Exerelin_SupplyWorkshop.WORKSHOP_METALS;
		modifyCommodityDemand(Commodities.METALS, mDemand);
		//modifyCommodityDemand(Commodities.METALS, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.METALS) * 0.5f);	// hax
		
		// rare metal
		float rmSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_RARE_ORE;
		rmSupply += shipbreakingCount * ConditionData.SHIPBREAKING_RARE_METALS * ExerelinShipbreakingCenter.EXTRA_METALS_MULT;
		rmSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_RARE_METALS * Exerelin_RecyclingPlant.HAX_MULT_07_METALS;
		rmSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.RARE_METALS);
		ExerelinMarketSetup.this.modifyCommoditySupply(Commodities.RARE_METALS, rmSupply);
		float rmDemand = autofacCount * ConditionData.AUTOFAC_HEAVY_RARE_METALS;
		rmDemand += workshopCount * Exerelin_SupplyWorkshop.WORKSHOP_RARE_METALS;
		rmDemand += fuelProdCount * ConditionData.FUEL_PRODUCTION_RARE_METALS;
		modifyCommodityDemand(Commodities.RARE_METALS, rmDemand);
		
		// supplies
		float sSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_SUPPLIES; 
		sSupply += shipbreakingCount * ConditionData.SHIPBREAKING_SUPPLIES;
		sSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_SUPPLIES;
		sSupply += workshopCount * Exerelin_SupplyWorkshop.WORKSHOP_SUPPLIES;
		sSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.SUPPLIES);
		modifyCommoditySupply(Commodities.SUPPLIES, sSupply);
		float sDemand = ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.SUPPLIES);
		sDemand += 0.00085 * pop;	// fudge factor to account for crew and marines using supplies
		modifyCommodityDemand(Commodities.SUPPLIES, sDemand * 1.2f);	// hax
		
		// fuel
		float fSupply = fuelProdCount * ConditionData.FUEL_PRODUCTION_FUEL;
		fSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.FUEL);
		modifyCommoditySupply(Commodities.FUEL, fSupply);
		
		modifyCommodityDemand(Commodities.FUEL, ExerelinUtilsMarket.getMarketBaseFuelDemand(newMarket, 20*marketSize));
		//modifyCommodityDemand(Commodities.FUEL, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.FUEL) * 0.7f);
		
		// food
		modifyCommoditySupply(Commodities.FOOD, ExerelinUtilsMarket.getMarketBaseFoodSupply(newMarket, true));
		modifyCommodityDemand(Commodities.FOOD, ConditionData.POPULATION_FOOD_MULT * ExerelinUtilsMarket.getPopulation(marketSize));
		
		// guns (hand weapons)
		float gSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_HAND_WEAPONS;
		gSupply += workshopCount * Exerelin_SupplyWorkshop.WORKSHOP_HAND_WEAPONS;
		gSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.HAND_WEAPONS);
		modifyCommoditySupply(Commodities.HAND_WEAPONS, gSupply);
		modifyCommodityDemand(Commodities.HAND_WEAPONS, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.HAND_WEAPONS));
		
		// machinery
		float hmSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_MACHINERY;
		hmSupply += shipbreakingCount * ConditionData.SHIPBREAKING_MACHINERY * ExerelinShipbreakingCenter.EXTRA_MACHINERY_MULT;
		hmSupply += workshopCount * Exerelin_SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY;
		hmSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_HEAVY_MACHINERY;
		hmSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.HEAVY_MACHINERY);
		modifyCommoditySupply(Commodities.HEAVY_MACHINERY, hmSupply);
		//modifyCommodityDemand(Commodities.HEAVY_MACHINERY, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.HEAVY_MACHINERY) * 0.75f);	// hax
		modifyCommodityDemand(Commodities.HEAVY_MACHINERY, ExerelinUtilsMarket.getMarketBaseMachineryDemand(newMarket, 0));
		
		// organics
		float oSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORGANICS_COMPLEX) * ConditionData.ORGANICS_MINING_ORGANICS;
		oSupply += ExerelinUtilsMarket.getFarmingFoodSupply(newMarket, true) * ConditionData.FARMING_ORGANICS_FRACTION;
		oSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_ORGANICS * Exerelin_RecyclingPlant.HAX_MULT_07_OV;
		oSupply += ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.ORGANICS);
		modifyCommoditySupply(Commodities.ORGANICS, oSupply);
		modifyCommodityDemand(Commodities.ORGANICS, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.ORGANICS) * 0.95f);	// hax
		
		// volatiles
		float vSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.VOLATILES_COMPLEX) * ConditionData.VOLATILES_MINING_VOLATILES;
		vSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_VOLATILES * Exerelin_RecyclingPlant.HAX_MULT_07_OV;
		ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.VOLATILES);
		modifyCommoditySupply(Commodities.VOLATILES, vSupply);
		modifyCommodityDemand(Commodities.VOLATILES, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.VOLATILES) * 1.05f);	// hax
		
		// ore
		modifyCommoditySupply(Commodities.ORE, ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORE_COMPLEX) 
				* ConditionData.ORE_MINING_ORE * ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.ORE));
		float orDemand = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_ORE;
		if (newMarket.hasCondition("aiw_inorganic_populace"))
			orDemand += (pop * ConditionData.POPULATION_FOOD_MULT) * 0.95f * 0.02f;
		modifyCommodityDemand(Commodities.ORE, orDemand);
		//modifyCommodityDemand(Commodities.ORE, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.ORE));
		
		data.market = newMarket;
		return newMarket;
	}
	
	// =========================================================================	
	// Economy balancer functions
	
	protected void balanceDomesticGoods(List<EntityData> candidateEntities)
	{
		float domesticGoodsSupply = getCommoditySupply(Commodities.DOMESTIC_GOODS);
		float domesticGoodsDemand = getCommodityDemand(Commodities.DOMESTIC_GOODS);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		final int HALFPOW7 = (int)Math.pow(10, 7)/2;
		final int HALFPOW6 = (int)Math.pow(10, 6)/2;
		final int HALFPOW5 = (int)Math.pow(10, 5)/2;
		final int HALFPOW4 = (int)Math.pow(10, 4)/2;
		
		log.info("Pre-balance domestic goods supply/demand: " + (int)domesticGoodsSupply + " / " + (int)domesticGoodsDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			int pop = ExerelinUtilsMarket.getPopulation(size);
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX)) 
			{
				// oversupply; remove this LIC and prioritise the market for any readding later
				if (domesticGoodsSupply > domesticGoodsDemand * 1.2)
				{
					removeMarketCondition(market, entity, Conditions.LIGHT_INDUSTRIAL_COMPLEX);
					domesticGoodsSupply -= ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS_MULT * pop
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
					organicsDemand -= ConditionData.LIGHT_INDUSTRY_ORGANICS_MULT * pop;
					volatilesDemand -= ConditionData.LIGHT_INDUSTRY_VOLATILES_MULT * pop;
					machineryDemand -= ConditionData.LIGHT_INDUSTRY_MACHINERY_MULT * pop;
					weight *= 25;
					log.info("Removed balancing Light Industrial Complex from " + market.getName() + " (size " + size + ")");
				}
			}
			if (market.hasCondition(Conditions.COTTAGE_INDUSTRY)) weight *= 0.25f;
			
			weight *= getConditionWeightForArchetype(Conditions.LIGHT_INDUSTRIAL_COMPLEX, entity.archetype, 1);
			
			entityPicker.add(entity, weight);
		}
		
		while (domesticGoodsDemand > domesticGoodsSupply)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			int maxSize = 7;
			double shortfall = domesticGoodsDemand - domesticGoodsSupply;
			if (shortfall < HALFPOW4)
				maxSize = 5;
			if (shortfall < HALFPOW5)
				maxSize = 6;
			else if (shortfall < HALFPOW6)
				maxSize = 7;
			//else if (shortfall < HALFPOW7)
			//	maxSize = 7;
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			int size = market.getSize();
			if (size > maxSize) continue;
			if (size < maxSize - 2) continue;
			
			int pop = ExerelinUtilsMarket.getPopulation(size);
			
			addMarketCondition(market, entity, Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			domesticGoodsSupply += ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS_MULT * pop
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
			organicsDemand += ConditionData.LIGHT_INDUSTRY_ORGANICS_MULT * pop;
			volatilesDemand += ConditionData.LIGHT_INDUSTRY_VOLATILES_MULT * pop;
			machineryDemand += ConditionData.LIGHT_INDUSTRY_MACHINERY_MULT * pop;
			log.info("Added balancing Light Industrial Complex to " + market.getName() + " (size " + size + ")");
		}
		log.info("Final domestic goods supply/demand: " + (int)domesticGoodsSupply + " / " + (int)domesticGoodsDemand);
		
		setCommoditySupply(Commodities.DOMESTIC_GOODS, domesticGoodsSupply);
		setCommodityDemand(Commodities.ORGANICS, organicsDemand);
		setCommodityDemand(Commodities.VOLATILES, volatilesDemand);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	protected void balanceRareMetal(List<EntityData> candidateEntities)
	{
		float suppliesSupply = getCommoditySupply(Commodities.SUPPLIES);
		float rareMetalSupply = getCommoditySupply(Commodities.RARE_METALS);
		float rareMetalDemand = getCommodityDemand(Commodities.RARE_METALS);
		float metalSupply = getCommoditySupply(Commodities.METALS);
		float machinerySupply = getCommoditySupply(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance rare metal supply/demand: " + (int)rareMetalSupply + " / " + (int)rareMetalDemand);
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			if (size <= 4) continue;
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			
			if (market.hasCondition(Conditions.SHIPBREAKING_CENTER)) 
			{
				if (rareMetalSupply > rareMetalDemand * 1.2)
				{
					removeMarketCondition(market, entity, Conditions.SHIPBREAKING_CENTER);
					suppliesSupply -= ConditionData.SHIPBREAKING_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
					metalSupply -= ConditionData.SHIPBREAKING_METALS * ExerelinShipbreakingCenter.EXTRA_METALS_MULT
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
					rareMetalSupply -= ConditionData.SHIPBREAKING_RARE_METALS * ExerelinShipbreakingCenter.EXTRA_METALS_MULT
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.RARE_METALS);
					machinerySupply -= ConditionData.SHIPBREAKING_MACHINERY * ExerelinShipbreakingCenter.EXTRA_MACHINERY_MULT
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);

					log.info("Removed balancing shipbreaking center from " + market.getName());
					weight *= 100;
				}
			}
			
			weight *= getConditionWeightForArchetype(Conditions.SHIPBREAKING_CENTER, entity.archetype, 0.1f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			if (weight == 0) continue;
			entityPicker.add(entity, weight);
		}
		
		while (rareMetalDemand > rareMetalSupply * 1)
		{
			if (entityPicker.isEmpty())	break;
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			addMarketCondition(market, entity, Conditions.SHIPBREAKING_CENTER);
			suppliesSupply += ConditionData.SHIPBREAKING_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
			metalSupply += ConditionData.SHIPBREAKING_METALS * ExerelinShipbreakingCenter.EXTRA_METALS_MULT
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
			rareMetalSupply += ConditionData.SHIPBREAKING_RARE_METALS * ExerelinShipbreakingCenter.EXTRA_METALS_MULT
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.RARE_METALS);
			machinerySupply += ConditionData.SHIPBREAKING_MACHINERY * ExerelinShipbreakingCenter.EXTRA_MACHINERY_MULT
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
			log.info("Added balancing shipbreaking center to " + market.getName());
		}
		log.info("Final rare metal supply/demand: " + (int)rareMetalSupply + " / " + (int)rareMetalDemand);
		
		setCommoditySupply(Commodities.SUPPLIES, suppliesSupply);
		setCommoditySupply(Commodities.METALS, metalSupply);
		setCommoditySupply(Commodities.RARE_METALS, rareMetalSupply);
		setCommoditySupply(Commodities.HEAVY_MACHINERY, machinerySupply);
	}
	
	protected void balanceMachinery(List<EntityData> candidateEntities, boolean firstPass)
	{
		float suppliesSupply = getCommoditySupply(Commodities.SUPPLIES);
		float rareMetalDemand = getCommodityDemand(Commodities.RARE_METALS);
		float metalDemand = getCommodityDemand(Commodities.METALS);
		float machinerySupply = getCommoditySupply(Commodities.HEAVY_MACHINERY);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		float gunsSupply = getCommoditySupply(Commodities.HAND_WEAPONS);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		
		if (firstPass)
		{
			//machineryDemand += 2500;	// it's hax time!
		}
		
		log.info("Pre-balance machinery supply/demand: " + (int)machinerySupply + " / " + (int)machineryDemand);
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			if (size <= 3) continue;
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			
			if (market.hasCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY)) 
			{
				if (machinerySupply > machineryDemand * 1.15)
				{
					removeMarketCondition(market, entity, Conditions.AUTOFAC_HEAVY_INDUSTRY);
					suppliesSupply -= ConditionData.AUTOFAC_HEAVY_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
					machinerySupply -= ConditionData.AUTOFAC_HEAVY_MACHINERY * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
					machineryDemand -= ConditionData.AUTOFAC_HEAVY_MACHINERY_DEMAND;
					metalDemand -= ConditionData.AUTOFAC_HEAVY_METALS;
					rareMetalDemand -= ConditionData.AUTOFAC_HEAVY_RARE_METALS;
					gunsSupply -= ConditionData.AUTOFAC_HEAVY_HAND_WEAPONS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
					organicsDemand -= ConditionData.AUTOFAC_HEAVY_ORGANICS;
					volatilesDemand -= ConditionData.AUTOFAC_HEAVY_VOLATILES;
					log.info("Removed balancing heavy autofactory from " + market.getName());
					weight *= 100;
				}
			}
			
			weight *= getConditionWeightForArchetype(Conditions.AUTOFAC_HEAVY_INDUSTRY, entity.archetype, 0.1f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			if (weight == 0) continue;
			entityPicker.add(entity, weight);
		}
		
		while (machineryDemand > machinerySupply * 0.95)
		{
			if (entityPicker.isEmpty())	break;
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			addMarketCondition(market, entity, Conditions.AUTOFAC_HEAVY_INDUSTRY);
			suppliesSupply += ConditionData.AUTOFAC_HEAVY_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
			machinerySupply += ConditionData.AUTOFAC_HEAVY_MACHINERY * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
			machineryDemand += ConditionData.AUTOFAC_HEAVY_MACHINERY_DEMAND;
			metalDemand += ConditionData.AUTOFAC_HEAVY_METALS;
			rareMetalDemand += ConditionData.AUTOFAC_HEAVY_RARE_METALS;
			gunsSupply += ConditionData.AUTOFAC_HEAVY_HAND_WEAPONS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
			organicsDemand += ConditionData.AUTOFAC_HEAVY_ORGANICS;
			volatilesDemand += ConditionData.AUTOFAC_HEAVY_VOLATILES;
			log.info("Added balancing heavy autofac to " + market.getName());
		}
		
		log.info("Final machinery supply/demand: " + (int)machinerySupply + " / " + (int)machineryDemand);
		
		setCommoditySupply(Commodities.SUPPLIES, suppliesSupply);
		setCommoditySupply(Commodities.HAND_WEAPONS, gunsSupply);
		setCommoditySupply(Commodities.HEAVY_MACHINERY, machinerySupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
		setCommodityDemand(Commodities.METALS, metalDemand);
		setCommodityDemand(Commodities.RARE_METALS, rareMetalDemand);
		setCommodityDemand(Commodities.ORGANICS, organicsDemand);
		setCommodityDemand(Commodities.VOLATILES, volatilesDemand);
	}
	
	protected void balanceSupplies(List<EntityData> candidateEntities)
	{
		float suppliesSupply = getCommoditySupply(Commodities.SUPPLIES);
		float suppliesDemand = getCommodityDemand(Commodities.SUPPLIES);
		float rareMetalDemand = getCommodityDemand(Commodities.RARE_METALS);
		float metalDemand = getCommodityDemand(Commodities.METALS);
		float machinerySupply = getCommoditySupply(Commodities.HEAVY_MACHINERY);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		float gunsSupply = getCommoditySupply(Commodities.HAND_WEAPONS);
		float gunsDemand = getCommodityDemand(Commodities.HAND_WEAPONS);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		
		log.info("Pre-balance supplies supply/demand: " + (int)suppliesSupply + " / " + (int)suppliesDemand);
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			if (size <= 3) continue;
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			
			if (market.hasCondition("exerelin_supply_workshop") && !entity.isHQ) 
			{
				if (suppliesSupply > suppliesDemand * 1.25)
				{
					removeMarketCondition(market, entity, "exerelin_supply_workshop");
					suppliesSupply -= Exerelin_SupplyWorkshop.WORKSHOP_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
					machinerySupply -= Exerelin_SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
					machineryDemand -= Exerelin_SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY_DEMAND;
					metalDemand -= Exerelin_SupplyWorkshop.WORKSHOP_METALS;
					rareMetalDemand -= Exerelin_SupplyWorkshop.WORKSHOP_RARE_METALS;
					gunsSupply -= Exerelin_SupplyWorkshop.WORKSHOP_HAND_WEAPONS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
					organicsDemand -= Exerelin_SupplyWorkshop.WORKSHOP_ORGANICS;
					volatilesDemand -= Exerelin_SupplyWorkshop.WORKSHOP_VOLATILES;
					log.info("Removed balancing supply workshop from " + market.getName());
					weight *= 100;
				}
			}
			
			weight *= getConditionWeightForArchetype("exerelin_supply_workshop", entity.archetype, 0.25f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			if (weight == 0) continue;
			entityPicker.add(entity, weight);
		}
		
		while (suppliesDemand > suppliesSupply)
		{
			if (entityPicker.isEmpty())	break;
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			addMarketCondition(market, entity, "exerelin_supply_workshop");
			suppliesSupply += Exerelin_SupplyWorkshop.WORKSHOP_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
			machinerySupply += Exerelin_SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
			machineryDemand += Exerelin_SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY_DEMAND;
			metalDemand += Exerelin_SupplyWorkshop.WORKSHOP_METALS;
			rareMetalDemand += Exerelin_SupplyWorkshop.WORKSHOP_RARE_METALS;
			gunsSupply += Exerelin_SupplyWorkshop.WORKSHOP_HAND_WEAPONS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
			organicsDemand += Exerelin_SupplyWorkshop.WORKSHOP_ORGANICS;
			volatilesDemand += Exerelin_SupplyWorkshop.WORKSHOP_VOLATILES;
			log.info("Added balancing supply workshop to " + market.getName());
		}
		log.info("Final supplies supply/demand: " + (int)suppliesSupply + " / " + (int)suppliesDemand);
		
		setCommoditySupply(Commodities.SUPPLIES, suppliesSupply);
		setCommoditySupply(Commodities.HAND_WEAPONS, gunsSupply);
		setCommoditySupply(Commodities.HEAVY_MACHINERY, machinerySupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
		setCommodityDemand(Commodities.METALS, metalDemand);
		setCommodityDemand(Commodities.RARE_METALS, rareMetalDemand);
		setCommodityDemand(Commodities.ORGANICS, organicsDemand);
		setCommodityDemand(Commodities.VOLATILES, volatilesDemand);
	}

	protected void balanceFood(List<EntityData> candidateEntities)
	{
		final int HALFPOW5 = (int)Math.pow(10, 5)/2;
		final int HALFPOW4 = (int)Math.pow(10, 4)/2;
		final int HALFPOW3 = (int)Math.pow(10, 3)/2;
		
		float foodSupply = getCommoditySupply(Commodities.FOOD);
		float foodDemand = getCommodityDemand(Commodities.FOOD);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		float domesticGoodsDemand = getCommodityDemand(Commodities.DOMESTIC_GOODS);
		float organicsSupply = getCommoditySupply(Commodities.ORGANICS);
		
		log.info("Pre-balance food supply/demand: " + (int)foodSupply + " / " + (int)foodDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			int pop = ExerelinUtilsMarket.getPopulation(size);
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			double surplus = foodSupply - foodDemand;
			float baseFarming = ExerelinUtilsMarket.getFarmingFoodSupply(market, false);
			
			if (foodSupply > foodDemand)
			{
				if (market.hasCondition(Conditions.ORBITAL_BURNS) && surplus > baseFarming)
				{
					removeMarketCondition(market, entity, Conditions.ORBITAL_BURNS);
					foodSupply -= baseFarming;
					organicsSupply -= baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
					
					weight *= 25;
					log.info("Removed balancing Orbital Burns from " + market.getName() + " (size " + size + ")");
				}
				else if (market.hasCondition(Conditions.RURAL_POLITY) && Math.random() > 0.5 && surplus > baseFarming)
				{
					removeMarketCondition(market, entity, Conditions.RURAL_POLITY);
					foodSupply -= baseFarming;
					organicsSupply -= baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);;
					domesticGoodsDemand += ConditionData.POPULATION_DOMESTIC_MULT * pop * 0.5;
					
					weight *= 25;
					log.info("Removed balancing Rural Polity from " + market.getName() + " (size " + size + ")");
				}
				else if (market.hasCondition("exerelin_hydroponics"))
				{
					removeMarketCondition(market, entity, "exerelin_hydroponics");
					foodSupply -= Exerelin_Hydroponics.HYDROPONICS_FOOD_POP_MULT * pop
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FOOD);
					machineryDemand -= Exerelin_Hydroponics.HYDROPONICS_HEAVY_MACHINERY_POP_MULT * pop;
					weight *= 25;
					log.info("Removed balancing Hydroponics Lab from " + market.getName() + " (size " + size + ")");
				}
			}
			weight *= getConditionWeightForArchetype("exerelin_hydroponics", entity.archetype, 0.1f);
			
			entityPicker.add(entity, weight);
		}
		
		while (foodDemand > foodSupply * 1.2)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			int maxSize = 7;
			double shortfall = foodDemand - foodSupply;
			if (shortfall < HALFPOW3)
				maxSize = 4;
			else if (shortfall < HALFPOW4)
				maxSize = 5;
			else if (shortfall < HALFPOW5)
				maxSize = 6;
			//log.info("Shortfall: " + shortfall + ", max size: " + maxSize);
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			int size = market.getSize();
			if (size > maxSize) continue;
			int pop = ExerelinUtilsMarket.getPopulation(size);
			float baseFarming = ExerelinUtilsMarket.getFarmingFoodSupply(market, false);
			
			if (entity.planetType.equals("jungle") && entity.type != EntityType.STATION && !market.hasCondition(Conditions.ORBITAL_BURNS)
					&& shortfall > baseFarming)
			{
				addMarketCondition(market, entity, Conditions.ORBITAL_BURNS);
				foodSupply += baseFarming;
				organicsSupply += baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);;
				log.info("Added balancing Orbital Burns to " + market.getName() + " (size " + size + ")");
			}
			else if (entity.type != EntityType.STATION && getConditionWeightForArchetype(Conditions.RURAL_POLITY, entity.archetype, 0) > Math.random() 
					&& !market.hasCondition(Conditions.RURAL_POLITY) && !market.hasCondition(Conditions.URBANIZED_POLITY) && shortfall > baseFarming && size >= 4)
			{
				addMarketCondition(market, entity, Conditions.RURAL_POLITY);
				foodSupply += baseFarming;
				organicsSupply += baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
				domesticGoodsDemand -= ConditionData.POPULATION_DOMESTIC_MULT * pop * 0.5;
				log.info("Added balancing Rural Polity to " + market.getName() + " (size " + size + ")");
			}
			else
			{
				addMarketCondition(market, entity, "exerelin_hydroponics");
				foodSupply += Exerelin_Hydroponics.HYDROPONICS_FOOD_POP_MULT * ExerelinUtilsMarket.getPopulation(size) 
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FOOD);
				machineryDemand += Exerelin_Hydroponics.HYDROPONICS_HEAVY_MACHINERY_POP_MULT * pop;
				log.info("Added balancing Hydroponics Lab to " + market.getName() + " (size " + size + ")");
			}
		}
		log.info("Final food supply/demand: " + (int)foodSupply + " / " + (int)foodDemand);
		
		setCommoditySupply(Commodities.FOOD, foodSupply);
		setCommoditySupply(Commodities.ORGANICS, organicsSupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
		setCommodityDemand(Commodities.DOMESTIC_GOODS, domesticGoodsDemand);
	}
	
	protected void balanceFuel(List<EntityData> candidateEntities)
	{
		float fuelSupply = getCommoditySupply(Commodities.FUEL);
		float fuelDemand = getCommodityDemand(Commodities.FUEL);
		float rareMetalDemand = getCommodityDemand(Commodities.RARE_METALS);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance fuel supply/demand: " + (int)fuelSupply + " / " + (int)fuelDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION) && entity.market != sectorGen.homeworld.market) 
			{
				float fuelAmount = ConditionData.FUEL_PRODUCTION_FUEL * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FUEL);
				if (fuelSupply > fuelDemand + fuelAmount * 1.1f)
				{
					removeMarketCondition(market, entity, Conditions.ANTIMATTER_FUEL_PRODUCTION);
					fuelSupply -= fuelAmount;
					volatilesDemand -= ConditionData.FUEL_PRODUCTION_VOLATILES;
					organicsDemand -= ConditionData.FUEL_PRODUCTION_ORGANICS;
					rareMetalDemand -= ConditionData.FUEL_PRODUCTION_RARE_METALS;
					machineryDemand -= ConditionData.FUEL_PRODUCTION_MACHINERY;
					weight *= 25;
					log.info("Removed balancing Antimatter Fuel Production from " + market.getName());
				}
			}
			
			if (size < 4) continue;
			
			weight *= getConditionWeightForArchetype(Conditions.ANTIMATTER_FUEL_PRODUCTION, entity.archetype, 0.1f);
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(market.getFactionId()))
				weight *= 0.01f;
			
			entityPicker.add(entity, weight);
		}
		
		while (fuelDemand > fuelSupply * 0.9f)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			addMarketCondition(market, entity, Conditions.ANTIMATTER_FUEL_PRODUCTION);
			fuelSupply += ConditionData.FUEL_PRODUCTION_FUEL * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FUEL);
			volatilesDemand += ConditionData.FUEL_PRODUCTION_VOLATILES;
			organicsDemand += ConditionData.FUEL_PRODUCTION_ORGANICS;
			rareMetalDemand += ConditionData.FUEL_PRODUCTION_RARE_METALS;
			machineryDemand += ConditionData.FUEL_PRODUCTION_MACHINERY;
			log.info("Added balancing Antimatter Fuel Production to " + market.getName());
		}
		log.info("Final fuel supply/demand: " + (int)fuelSupply + " / " + (int)fuelDemand);
		
		setCommoditySupply(Commodities.FUEL, fuelSupply);
		setCommodityDemand(Commodities.ORGANICS, organicsDemand);
		setCommodityDemand(Commodities.VOLATILES, volatilesDemand);
		setCommodityDemand(Commodities.RARE_METALS, rareMetalDemand);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	protected void balanceOrganics(List<EntityData> candidateEntities)
	{
		float organicsSupply = getCommoditySupply(Commodities.ORGANICS);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance organics supply/demand: " + (int)organicsSupply + " / " + (int)organicsDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.ORGANICS_COMPLEX)) 
			{
				if (organicsSupply > organicsDemand * 1.2f)
				{
					removeMarketCondition(market, entity, Conditions.ORGANICS_COMPLEX);
					organicsSupply -= ConditionData.ORGANICS_MINING_ORGANICS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
					machineryDemand -= ConditionData.ORGANICS_MINING_MACHINERY;
					weight *= 25;
					log.info("Removed balancing Organics Complex from " + market.getName());
				}
			}
			
			weight *= getConditionWeightForArchetype(Conditions.ORGANICS_COMPLEX, entity.archetype, 0.1f);
			weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (organicsDemand > organicsSupply * 1.1)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			addMarketCondition(market, entity, Conditions.ORGANICS_COMPLEX);
			organicsSupply += ConditionData.ORGANICS_MINING_ORGANICS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
			machineryDemand += ConditionData.ORGANICS_MINING_MACHINERY;
			log.info("Added balancing Organics Complex to " + market.getName());
		}
		log.info("Final organics supply/demand: " + (int)organicsSupply + " / " + (int)organicsDemand);
		
		setCommoditySupply(Commodities.ORGANICS, organicsSupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	protected void balanceVolatiles(List<EntityData> candidateEntities)
	{
		float volatilesSupply = getCommoditySupply(Commodities.VOLATILES);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance volatiles supply/demand: " + (int)volatilesSupply + " / " + (int)volatilesDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.VOLATILES_COMPLEX)) 
			{
				if (volatilesSupply > volatilesDemand * 1.2f)
				{
					removeMarketCondition(market, entity, Conditions.VOLATILES_COMPLEX);
					volatilesSupply -= ConditionData.VOLATILES_MINING_VOLATILES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.VOLATILES);
					machineryDemand -= ConditionData.VOLATILES_MINING_MACHINERY;
					weight *= 25;
					log.info("Removed balancing Volatiles Complex from " + market.getName());
				}
			}
			
			weight *= getConditionWeightForArchetype(Conditions.VOLATILES_COMPLEX, entity.archetype, 0.1f);
			weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (volatilesDemand > volatilesSupply * 1.1)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			addMarketCondition(market, entity, Conditions.VOLATILES_COMPLEX);
			volatilesSupply += ConditionData.VOLATILES_MINING_VOLATILES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.VOLATILES);
			machineryDemand += ConditionData.VOLATILES_MINING_MACHINERY;
			log.info("Added balancing Volatiles Complex to " + market.getName());
		}
		log.info("Final volatiles supply/demand: " + (int)volatilesSupply + " / " + (int)volatilesDemand);
		
		setCommoditySupply(Commodities.VOLATILES, volatilesSupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	protected void balanceMetal(List<EntityData> candidateEntities)
	{
		float metalSupply = getCommoditySupply(Commodities.METALS);
		float metalDemand = getCommodityDemand(Commodities.METALS);
		float oreDemand = getCommodityDemand(Commodities.ORE);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance metal supply/demand: " + (int)metalSupply + " / " + (int)metalDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.ORE_REFINING_COMPLEX)) 
			{
				if (metalSupply > metalDemand * 1.1f)
				{
					removeMarketCondition(market, entity, Conditions.ORE_REFINING_COMPLEX);
					metalSupply -= ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_ORE 
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
					oreDemand -= ConditionData.ORE_REFINING_ORE;
					machineryDemand -= ConditionData.ORE_REFINING_MACHINERY;
					weight *= 25;
					log.info("Removed balancing Ore Refining Complex from " + market.getName());
				}
			}
			
			weight *= getConditionWeightForArchetype(Conditions.ORE_REFINING_COMPLEX, entity.archetype, 0.1f);
			//weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (metalDemand > metalSupply * 1f)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			addMarketCondition(market, entity, Conditions.ORE_REFINING_COMPLEX);
			metalSupply += ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_ORE 
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
			oreDemand += ConditionData.ORE_REFINING_ORE;
			machineryDemand += ConditionData.ORE_REFINING_MACHINERY;
			log.info("Added balancing Ore Refining Complex to " + market.getName());
		}
		log.info("Final metal supply/demand: " + (int)metalSupply + " / " + (int)metalDemand);
		
		setCommoditySupply(Commodities.METALS, metalSupply);
		setCommodityDemand(Commodities.ORE, oreDemand);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	protected void balanceOre(List<EntityData> candidateEntities)
	{
		float oreSupply = getCommoditySupply(Commodities.ORE);
		float oreDemand = getCommodityDemand(Commodities.ORE);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance ore supply/demand: " + (int)oreSupply + " / " + (int)oreDemand);
		
		WeightedRandomPicker<EntityData> entityPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.ORE_COMPLEX)) 
			{
				if (oreSupply > oreDemand * 1.1f)
				{
					removeMarketCondition(market, entity, Conditions.ORE_COMPLEX);
					oreSupply -= ConditionData.ORE_MINING_ORE * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORE);
					machineryDemand -= ConditionData.ORE_MINING_MACHINERY;
					weight *= 25;
					log.info("Removed balancing Ore Complex from " + market.getName());
				}
			}
			
			weight *= getConditionWeightForArchetype(Conditions.ORE_COMPLEX, entity.archetype, 0.1f);
			weight /= size;
			
			entityPicker.add(entity, weight);
		}
		
		while (oreDemand > oreSupply * 0.9)
		{
			if (entityPicker.isEmpty())	break;	// fuck it, we give up
			
			EntityData entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			addMarketCondition(market, entity, Conditions.ORE_COMPLEX);
			oreSupply += ConditionData.ORE_MINING_ORE * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORE);
			machineryDemand += ConditionData.ORE_MINING_MACHINERY;
			log.info("Added balancing Ore Complex to " + market.getName());
		}
		log.info("Final ore supply/demand: " + (int)oreSupply + " / " + (int)oreDemand);
		
		setCommoditySupply(Commodities.ORE, oreSupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	public void reportSupplyDemand()
	{
		String[] commodities = {Commodities.SUPPLIES, Commodities.FUEL, Commodities.DOMESTIC_GOODS, Commodities.FOOD, Commodities.HEAVY_MACHINERY, 
			Commodities.METALS,Commodities.RARE_METALS,	Commodities.ORE, Commodities.RARE_ORE, Commodities.ORGANICS, Commodities.VOLATILES};
		for (String commodity : commodities)
		{
			if (!commodityDemand.containsKey(commodity) || !commoditySupply.containsKey(commodity))
				continue;
			float supply = commoditySupply.get(commodity);
			float demand = commodityDemand.get(commodity);
			log.info("\t" + commodity.toUpperCase() + " supply / demand: " + supply + " / " + demand);
		}
	}
	
	protected void addStartingMarketCommodities(MarketAPI market)
	{
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.GREEN_CREW, 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.REGULAR_CREW, 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.VETERAN_CREW, 0.1f, 0.2f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.MARINES, 0.8f, 1.0f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.SUPPLIES, 0.85f, 0.95f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.FUEL, 0.85f, 0.95f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.FOOD, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.DOMESTIC_GOODS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.LUXURY_GOODS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.HEAVY_MACHINERY, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.METALS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.RARE_METALS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.ORE, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.RARE_ORE, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.ORGANICS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.VOLATILES, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.HAND_WEAPONS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.DRUGS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.ORGANS, 0.8f, 0.9f);
		ExerelinUtilsCargo.addCommodityStockpile(market, Commodities.LOBSTER, 0.8f, 0.9f);
	}
	
	// =========================================================================
	// manipulates the commodity supply/demand maps
	
	protected float getCommoditySupply(String commodityId)
	{
		if (!commoditySupply.containsKey(commodityId))
			commoditySupply.put(commodityId, 0f);
		return commoditySupply.get(commodityId);
	}
	
	protected void setCommoditySupply(String commodityId, float amount)
	{
		commoditySupply.put(commodityId, amount);
	}
	
	protected void modifyCommoditySupply(String commodityId, float amount)
	{
		commoditySupply.put(commodityId, getCommoditySupply(commodityId) + amount);
	}
	
	protected float getCommodityDemand(String commodityId)
	{
		if (!commodityDemand.containsKey(commodityId))
			commodityDemand.put(commodityId, 0f);
		return commodityDemand.get(commodityId);
	}
	
	protected void setCommodityDemand(String commodityId, float amount)
	{
		commodityDemand.put(commodityId, amount);
	}
	
	protected void modifyCommodityDemand(String commodityId, float amount)
	{
		commodityDemand.put(commodityId, getCommodityDemand(commodityId) + amount);
	}
	
	// =========================================================================
	// static classes
	public static class MarketConditionDef
	{
		final String name;
		int cost = 0;
		Map<MarketArchetype, Float> archetypes = new HashMap<>();
		float chance = 0;
		int minSize = 0;
		int maxSize = 99;
		boolean allowDuplicates = true;
		boolean allowStations = true;
		boolean special = false;
		String requiredFaction;
		final List<String> allowedPlanets = new ArrayList<>();
		final List<String> disallowedPlanets  = new ArrayList<>();
		final List<String> conflictsWith  = new ArrayList<>();

		public MarketConditionDef(String name)
		{
			this.name = name;
		}
	}
	
	public static enum MarketArchetype
	{
		AGRICULTURE, ORE, VOLATILES, ORGANICS, MANUFACTURING, HEAVY_INDUSTRY, MIXED
	}
}