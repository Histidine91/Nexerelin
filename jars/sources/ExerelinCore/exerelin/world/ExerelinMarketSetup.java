package exerelin.world;

import java.util.List;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtils;
import exerelin.world.ExerelinSectorGen.EntityData;
import exerelin.world.ExerelinSectorGen.EntityType;
import java.io.IOException;
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
 * Each time an archetype is picked, reduce its weighting for the random picker
 * Each archetype gets a certain selection of planet types to randomly pick from
 * Each market gets a number of points to spend on market condition based on market size
 * Each market condition has a cost and a number of weightings based on the market
 * For each market, randomly pick a valid condition and spend points on it; repeat till out of points
 * Available points by market size:
 *	Size 2: 100
 *	Size 3: 200
 *	Size 4: 400
 *	Size 5: 700
 *	Size 6: 1100
 * Also have chance for bonus points based on market size
 * Special conditions allowed:
 *	Size 2: random(0, 1)
 *	Size 3-4: random(0,1) + random(0,1)
 *	Size 5-6: 1 + random(0,1)
*/

@SuppressWarnings("unchecked")
public class ExerelinMarketSetup
{
	public static Logger log = Global.getLogger(ExerelinMarketSetup.class);
	//private List possibleMarketConditions;
	
	protected static final String CONFIG_FILE = "data/config/exerelin/marketConfig.json";
	
	protected final Map<MarketArchetype, Map<String, Float>> planetsForArchetypes = new HashMap<>();
	protected final List<MarketConditionDef> conditions = new ArrayList<>();
	protected final List<MarketConditionDef> specialConditions = new ArrayList<>();
	
	private final List<MarketConditionDef> primaryResourceConds = new ArrayList<>();
	private final List<MarketConditionDef> industryConds = new ArrayList<>();
	private final List<MarketConditionDef> heavyIndustryConds = new ArrayList<>();
	private final List<MarketConditionDef> specialConds = new ArrayList<>();
	
	public ExerelinMarketSetup()
	{
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
				
				MarketConditionDef cond = new MarketConditionDef(name);
				cond.cost = condJson.optInt("cost", 0);
				cond.special = condJson.optBoolean("special", false);
				cond.minSize = condJson.optInt("minSize", 0);
				cond.maxSize = condJson.optInt("maxSize", 99);
				cond.allowStations = condJson.optBoolean("allowStations", true);
				cond.allowDuplicates = condJson.optBoolean("allowDuplicates", true);
				
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
				else conditions.add(cond);
				
			}
			
		} catch (IOException | JSONException ex) {
			log.error(ex);
		}
	}
	
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
			if (isMoon && type.equals("arid") || type.equals("terran"))
				continue;
			
			float weight = tmp.getValue();
			picker.add(type, weight);
		}
		if (picker.isEmpty()) return "desert";
		return picker.pick();
	}
	
	MarketConditionDef pickMarketCondition(MarketAPI market, List<MarketConditionDef> possibleConds, EntityData entityData, int budget)
	{
		WeightedRandomPicker<MarketConditionDef> picker = new WeightedRandomPicker<>();
		int numConds = 0;
		int size = market.getSize();
		String planetType = entityData.planetType;
		boolean isStation = entityData.type == EntityType.STATION;
		for (MarketConditionDef possibleCond : possibleConds) 
		{
			if (possibleCond.cost > budget) continue;
			if (possibleCond.minSize>= size || possibleCond.maxSize <= size) continue;
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
			float weight = possibleCond.archetypes.get(entityData.archetype);
			if (weight <= 0) continue;
			
			picker.add(possibleCond, weight);
			numConds++;
		}
		
		if (numConds == 0)
			return null;	// out of possible conditions; nothing more to do
		
		return picker.pick();
	}
	
	public void addMarketConditions(MarketAPI market, EntityData entityData)
	{
		log.info("Processing market conditions for " + market.getPrimaryEntity().getName() + " (" + market.getFaction().getDisplayName() + ")");
		
		int size = market.getSize();
		int points = 100;
		if (size == 3) points = 200;
		else if (size == 4) points = 400;
		else if (size == 5) points = 700;
		else if (size == 6) points = 1100;
		else if (size >= 7) points = 1500;
		for (int i=0; i<size; i++)
		{
			if (Math.random() > 0.33) points += 50;
		}
		
		while (points > 0)
		{
			MarketConditionDef cond = pickMarketCondition(market, conditions, entityData, points);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			market.addCondition(cond.name);
			points -= cond.cost;
		}
		
		int numSpecial = 0;
		if (size == 2 && Math.random() > 0.5) numSpecial = 1;
		else if (size <= 4) numSpecial = MathUtils.getRandomNumberInRange(0, 1) + MathUtils.getRandomNumberInRange(0, 1);
		else if (size <= 7) numSpecial = 1 + MathUtils.getRandomNumberInRange(0, 1);
		for (int i=0; i<numSpecial; i++)
		{
			MarketConditionDef cond = pickMarketCondition(market, specialConditions, entityData, 0);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			market.addCondition(cond.name);
		}
	}
	
	
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