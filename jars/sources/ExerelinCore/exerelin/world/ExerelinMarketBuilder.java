package exerelin.world;

import java.util.List;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.fleets.ExerelinLionsGuardFleetManager;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.NexUtilsMath;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.ExerelinProcGen.EntityType;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * There are several possible market archetypes: Agriculture, Ore, Organics, Volatiles, Manufacturing, Heavy Industry
 * Pick an archetype for each planet based on its surveyable market conditions
 * Each market gets a number of points to spend on market condition based on market size
 * Each market condition has a cost and a number of weightings based on the market archetype and other properties
 * For each market, randomly pick a valid condition and spend points on it; repeat till out of points
 * Available points by market size:
 *	Size 2: 150
 *	Size 3: 200
 *	Size 4: 200
 *	Size 5: 250
 *	Size 6: 250
 *	Size 7: 300
 * Also have chance for bonus points based on market size
 * Special conditions allowed:
 *	Size 1-2: 0
 *	Size 3-4: random(0,1)
 *	Size 5-6: random(0,1) + random(0,1)
 *	Size 7: 1 + random(0,1)
 * 
 * Once it's done assigning markets, it does a second pass
 * Add/remove market conditions to balance supply and demand of domestic goods, metal and supplies
*/

@SuppressWarnings("unchecked")
public class ExerelinMarketBuilder
{
	public static Logger log = Global.getLogger(ExerelinMarketBuilder.class);
	//private List possibleMarketConditions;
	
	public static final String CONFIG_FILE = "data/config/exerelin/marketConfig.json";
	public static final String SURVEY_CONDITION_FILE = "data/config/exerelin/survey_condition_archetypes.csv";
	public static final Map<Archetype, Float> PLANET_ARCHETYPE_QUOTAS = new HashMap<>(Archetype.values().length);
	//public static final Map<MarketArchetype, Float> STATION_ARCHETYPE_QUOTAS = new HashMap<>(MarketArchetype.values().length);
	// this proportion of TT markets with no military bases will have Cabal submarkets (Underworld)
	public static final float CABAL_MARKET_MULT = 0.4f;	
	// this is the chance a market with a military base will still be a candidate for Cabal markets
	public static final float CABAL_MILITARY_MARKET_CHANCE = 0.5f;
	public static final float LUDDIC_MAJORITY_CHANCE = 0.05f;	// how many markets have Luddic majority even if they aren't Luddic at start
	public static final float LUDDIC_MINORITY_CHANCE = 0.15f;	// how many markets that start under Church control are non-Luddic
	public static final float FORCE_MILITARY_BASE_CHANCE = 0.5f;	// if meets size requirements
	public static final float FORCE_MILITARY_BASE_CHANCE_PIRATE = 0.5f;
	public static final float PRE_BALANCE_BUDGET_MULT = 0.9f;	// < 1 to spare some points for balancer
	public static final int MAX_CONDITIONS = 14;
	
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MIN = 1.3f;
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MAX = 0.5f;	// lower than min so it can swap autofacs for shipbreakers if needed
	
	protected static final int[] PLANET_SIZE_ROTATION = new int[] {4, 5, 6, 5, 4};
	protected static final int[] MOON_SIZE_ROTATION = new int[] {3, 4, 5, 4};
	protected static final int[] STATION_SIZE_ROTATION = new int[] {3, 4, 5, 4, 3};
	
	public static final Archetype[] NON_MISC_ARCHETYPES = new Archetype[]{ Archetype.AGRICULTURE, Archetype.ORE, Archetype.VOLATILES, 
			Archetype.ORGANICS, Archetype.MANUFACTURING, Archetype.HEAVY_INDUSTRY };
	
	protected static final Map<String, Map<Archetype, Float>> conditionArchetypes = new HashMap<>();
	protected static final List<MarketConditionDef> conditions = new ArrayList<>();
	protected static final Map<String, MarketConditionDef> conditionsByID = new HashMap<>();
	protected static final List<MarketConditionDef> specialConditions = new ArrayList<>();
	
	protected Map<String, Float> commodityDemand = new HashMap<>();
	protected Map<String, Float> commoditySupply = new HashMap<>();
	
	protected Map<Archetype, List<ProcGenEntity>> marketsByArchetype = new HashMap<>();
	protected Map<ProcGenEntity, Map<Archetype, Float>> marketScoresForArchetypes = new HashMap<>();
	protected int marketArchetypeQueueNum = 0;
	
	protected int numStations = 0;
	protected int numPlanets = 0;
	protected int numMoons = 0;
	
	protected final ExerelinProcGen procGen;
	protected final Random random;
	protected final MarketBalancer balancer = new MarketBalancer(this);
	
	static {
		// (probably) must sum to 1
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.AGRICULTURE, 0.15f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.ORE, 0.18f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.ORGANICS, 0.18f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.VOLATILES, 0.14f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.MANUFACTURING, 0.2f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.HEAVY_INDUSTRY, 0.15f);
		
		loadConditions();
	}
	
	protected static void loadConditions()
	{
		try {
			JSONArray conditionArchetypesCsv = Global.getSettings().getMergedSpreadsheetDataForMod(
					"condition", SURVEY_CONDITION_FILE, ExerelinConstants.MOD_ID);
			for(int x = 0; x < conditionArchetypesCsv.length(); x++)
            {
                JSONObject row = conditionArchetypesCsv.getJSONObject(x);
                String cond = row.getString("condition");
				Map<Archetype, Float> entry = loadSurveyConditionEntry(row);
				conditionArchetypes.put(cond, entry);
            }
			
			JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
			
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
				cond.productive = condJson.optBoolean("productive", true);
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
				if (condJson.has("requiresOneOf"))
				{
					cond.requiresOneOf.addAll(ExerelinUtils.JSONArrayToArrayList(condJson.getJSONArray("requiresOneOf")));
				}
				if (condJson.has("noRequireForArchetype"))
				{
					String archetypeName = StringHelper.flattenToAscii(condJson.getString("noRequireForArchetype").toUpperCase());
					cond.noRequireForArchetype = Archetype.valueOf(archetypeName);
				}
				
				if (condJson.has("archetypes"))
				{
					JSONObject archetypesJson = condJson.getJSONObject("archetypes");
					float defaultWeight = (float)archetypesJson.optDouble("default", 0);
					for (Archetype possibleArchetype : Archetype.values())
					{
						float weight = (float)archetypesJson.optDouble(possibleArchetype.name().toLowerCase(), defaultWeight);
						cond.archetypes.put(possibleArchetype, weight);
					}
				}
				else
				{
					for (Archetype possibleArchetype : Archetype.values())
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
			
		} catch (IOException | JSONException ex) {	// fail-deadly to make sure errors don't go unnoticed
			log.error(ex);
			throw new IllegalStateException("Error loading market condition file for proc gen: " + ex);
		}	
	}
	
	public ExerelinMarketBuilder(ExerelinProcGen procGen)
	{
		this.procGen = procGen;
		random = procGen.getRandom();
	}
	
	/**
	 * Returns a map of the weighings a market condition has for each archetype, as specified in the CSV data
	 * @param csvRow Row from the loaded CSV
	 * @return
	 */
	protected static Map<Archetype, Float> loadSurveyConditionEntry(JSONObject csvRow)
	{
		Map<Archetype, Float> ret = new HashMap<>();
		for (Archetype archetype : NON_MISC_ARCHETYPES)
		{
			String archetypeName = StringHelper.flattenToAscii(archetype.name().toLowerCase());
			float val = (float)csvRow.optDouble(archetypeName, 0);
			ret.put(archetype, val);
		}
		return ret;
	}
	
	/**
	 * Gets the weight the specified market condition has for the specified archetype
	 * @param cond
	 * @param archetype
	 * @param defaultWeight
	 * @return
	 */
	protected static float getConditionWeightForArchetype(MarketConditionDef cond, Archetype archetype, float defaultWeight)
	{
		float weight = cond.archetypes.get(archetype);
		if (weight <= 0) weight = defaultWeight;
		return weight;
	}
	
	/**
	 * Gets the weight the specified market condition has for the specified archetype
	 * @param condID
	 * @param archetype
	 * @param defaultWeight
	 * @return
	 */
	protected static float getConditionWeightForArchetype(String condID, Archetype archetype, float defaultWeight)
	{
		if (!conditionsByID.containsKey(condID)) return defaultWeight;
		return getConditionWeightForArchetype(conditionsByID.get(condID), archetype, defaultWeight);
	}
	
	protected boolean isConditionAllowedForPlanet(MarketConditionDef cond, PlanetAPI planet)
	{
		if (!cond.allowedPlanets.isEmpty())
		{
			for (String type : cond.allowedPlanets)
				if (isPlanetOfType(planet, type)) return true;
			return false;
		}
		if (cond.disallowedPlanets.isEmpty())
		{
			for (String type : cond.disallowedPlanets)
				if (isPlanetOfType(planet, type)) return false;
		}
		return true;
	}
	
	protected boolean isConditionAllowedForPlanet(String condID, PlanetAPI planet)
	{
		return isConditionAllowedForPlanet(conditionsByID.get(condID), planet);
	}
	
	protected boolean hasConflict(MarketConditionDef possibleCond, MarketAPI market)
	{
		for (String conflict : possibleCond.conflictsWith)
		{
			if (market.hasCondition(conflict)) 
				return true;
		}
		return false;
	}
	
	/**
	 * Does this market have a market condition required for the specified condition, if applicable?
	 * @param cond
	 * @param entityData
	 * @return True if the market meets requirements, false otherwise
	 */
	protected boolean checkRequisiteConditions(MarketConditionDef cond, ProcGenEntity entityData)
	{
		if (cond.requiresOneOf.isEmpty()) return true;		
		if (cond.noRequireForArchetype != null && entityData.archetype == cond.noRequireForArchetype)
			return true;
		
		for (String reqId : cond.requiresOneOf)
		{
			if (entityData.market.hasCondition(reqId))
			{
				return true;
			}
		}
		return false;
	}
	
	public boolean isConditionAllowed(MarketConditionDef cond, ProcGenEntity entityData)
	{
		if (cond == null) return false;
		MarketAPI market = entityData.market;
		boolean isStation = entityData.type == EntityType.STATION;
		int size = market.getSize();
		
		if (cond.minSize > size || cond.maxSize < size) return false;
		if (!cond.allowStations && isStation) return false;
		if (!cond.allowDuplicates && market.hasCondition(cond.name)) return false;
		if (entityData.entity instanceof PlanetAPI)
			if (!isConditionAllowedForPlanet(cond, (PlanetAPI)entityData.entity)) return false;
		if (hasConflict(cond, market)) return false;
		if (!checkRequisiteConditions(cond, entityData)) return false;		
		
		return true;
	}
	
	public boolean isConditionAllowed(String conditionId, ProcGenEntity entityData)
	{
		return isConditionAllowed(conditionsByID.get(conditionId), entityData);
	}
	
	protected MarketConditionDef pickMarketCondition(MarketAPI market, List<MarketConditionDef> possibleConds, 
			ProcGenEntity entityData, int budget, boolean isFirst)
	{
		WeightedRandomPicker<MarketConditionDef> picker = new WeightedRandomPicker<>(random);
		int numConds = 0;
		
		if (isFirst) {
			// assign default first condition for certain archetypes
			switch (entityData.archetype) {
				case ORE:
					return conditionsByID.get(Conditions.ORE_COMPLEX);
				case ORGANICS:
					return conditionsByID.get(Conditions.ORGANICS_COMPLEX);
				case VOLATILES:
					return conditionsByID.get(Conditions.VOLATILES_COMPLEX);
			}
		}	
		
		for (MarketConditionDef possibleCond : possibleConds) 
		{
			if (possibleCond.cost > budget) continue;
			if (!isConditionAllowed(possibleCond, entityData)) continue;
			
			float weight = getConditionWeightForArchetype(possibleCond, entityData.archetype, 0);
			if (weight <= 0) continue;
			
			picker.add(possibleCond, weight);
			numConds++;
		}
		
		if (numConds == 0)
			return null;	// out of possible conditions; nothing more to do
		
		return picker.pick();
	}
	
	public void addMarketCondition(ProcGenEntity entityData, MarketConditionDef cond)
	{
		MarketAPI market = entityData.market;
		market.addCondition(cond.name);
		entityData.marketPointsSpent += cond.cost;
		
		if (cond.name.equals(Conditions.ORBITAL_STATION))
		{
			ProcGenEntity station = procGen.createEntityDataForStation(entityData.entity);
			station.market = market;
			procGen.createStation(station, market.getFactionId(), false);
		}
	}
	
	public void addMarketCondition(ProcGenEntity entityData, String cond)
	{
		addMarketCondition(entityData, conditionsByID.get(cond));
	}
	
	public void removeMarketCondition(ProcGenEntity entityData, MarketConditionDef cond)
	{
		ExerelinUtilsMarket.removeOneMarketCondition(entityData.market, cond.name);
		entityData.marketPointsSpent -= cond.cost;
	}
	
	public void removeMarketCondition(ProcGenEntity entityData, MarketConditionAPI cond)
	{
		MarketConditionDef def = conditionsByID.get(cond.getId());
		entityData.market.removeSpecificCondition(cond.getIdForPluginModifications());
		entityData.marketPointsSpent -= def.cost;
	}
	
	public void removeMarketCondition(ProcGenEntity entityData, String cond)
	{
		removeMarketCondition(entityData, conditionsByID.get(cond));
	}
	
	public void initMarketPointsAndAddRandomConditions(MarketAPI market, ProcGenEntity entityData)
	{
		log.info("Processing market conditions for " + market.getPrimaryEntity().getName() 
				+ " (" + market.getFaction().getDisplayName()
				+ ", " + entityData.archetype + ")"
		);
		
		int size = market.getSize();
		int points = 150;
		if (size == 3) points = 200;
		else if (size == 4) points = 200;
		else if (size == 5) points = 250;
		else if (size == 6) points = 250;
		else if (size >= 7) points = 300;
		
		int bonusPoints = 0;
		for (int i=0; i<size/2; i++)
		{
			if (random.nextFloat() > 0.4) bonusPoints += 50;
		}
		
		entityData.bonusMarketPoints = bonusPoints;
		points += bonusPoints;
		entityData.marketPoints = points;
		
		boolean first = true;
		while (entityData.marketPointsSpent < points * PRE_BALANCE_BUDGET_MULT)
		{
			MarketConditionDef cond = pickMarketCondition(market, conditions, entityData, 
					(int)(points * PRE_BALANCE_BUDGET_MULT - entityData.marketPointsSpent), first);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			addMarketCondition(entityData, cond);
			first = false;
		}
		
		int numSpecial = 0;
		if (size == 2) numSpecial = 0;
		else if (size <= 4) numSpecial = NexUtilsMath.randomNextIntInclusive(random, 1);
		else if (size <= 6) numSpecial = NexUtilsMath.randomNextIntInclusive(random, 1) + NexUtilsMath.randomNextIntInclusive(random, 1);
		else if (size <= 8) numSpecial = 1 + NexUtilsMath.randomNextIntInclusive(random, 1);
		else numSpecial = 2;
		
		for (int i=0; i<numSpecial; i++)
		{
			MarketConditionDef cond = pickMarketCondition(market, specialConditions, entityData, 0, false);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			addMarketCondition(entityData, cond);
		}
	}
	
	// =========================================================================
	// archetype handling
	
	/**
	 * Returns the highest score for any archetype other than wantedArchetype
	 * @param market
	 * @param wantedArchetype
	 * @param scores
	 * @return
	 */
	protected float getMarketBestScoreForOtherArchetype(ProcGenEntity market, Archetype wantedArchetype, Map<Archetype, Float> scores)
	{
		float bestScore = 0;
		for (Archetype archetype : NON_MISC_ARCHETYPES)
		{
			if (archetype == wantedArchetype) continue;
			float score = scores.get(archetype);
			if (score > bestScore)
				bestScore = score;
		}
		return bestScore;
	}
	
	protected Map<Archetype, Float> getMarketArchetypeScore(ProcGenEntity market)
	{
		//log.info("Processing archetype scores for market " + market.name);
		Map<Archetype, Float> marketScores = new HashMap<>();
		for (Archetype archetype : Archetype.values())
		{
			marketScores.put(archetype, 0f);
		}

		for (MarketConditionAPI cond : market.market.getConditions())
		{
			if (!conditionArchetypes.containsKey(cond.getId()))
				continue;
			
			Map<Archetype, Float> condScores = conditionArchetypes.get(cond.getId());
			for (Archetype archetype : NON_MISC_ARCHETYPES)
			{
				float score = condScores.get(archetype);
				//if (score > 0)
				//	log.info("Condition " + cond.getName() + " has score " + score + " for " + archetype);
				marketScores.put(archetype, marketScores.get(archetype) + score);
			}
		}
		return marketScores;
	}
	
	/**
	 * Returns a map of markets to maps of their scores for each archetype
	 * @param markets
	 * @return 
	 */
	protected Map<ProcGenEntity, Map<Archetype, Float>> getMarketArchetypeScores(Collection<ProcGenEntity> markets)
	{
		Map<ProcGenEntity, Map<Archetype, Float>> scores = new HashMap<>();
		for (ProcGenEntity market : markets)
		{
			scores.put(market, getMarketArchetypeScore(market));
		}
		return scores;
	}
	
	/**
	 * Returns a sorted list of markets based on their weight for the specified archetype.
	 * Weight = Score for archetype - highest score for another archetype
	 * @param markets
	 * @param archetype
	 * @return
	 */
	protected List<ProcGenEntity> getOrderedListOfMarketsForArchetype(List<ProcGenEntity> markets, Archetype archetype)
	{
		final Map<ProcGenEntity, Float> weightsForArchetype = new HashMap<>();
		for (ProcGenEntity market : markets)
		{
			float weight = marketScoresForArchetypes.get(market).get(archetype);
			weight -= getMarketBestScoreForOtherArchetype(market, archetype, marketScoresForArchetypes.get(market));
			weightsForArchetype.put(market, weight);
		}
		
		List<ProcGenEntity> ret = new ArrayList<>(markets);
		Collections.sort(ret, new Comparator<ProcGenEntity>() {	// biggest markets first
			@Override
			public int compare(ProcGenEntity data1, ProcGenEntity data2)
			{
				float weight1 = weightsForArchetype.get(data1);
				float weight2 = weightsForArchetype.get(data2);
				if (weight1 == weight2) return 0;
				else if (weight1 > weight2) return -1;
				else return 1;
			}
		});
		
		return ret;
	}
	
	/**
	 * Assigns archetype to a specified number the highest-scoring markets available for that archetype
	 * @param markets List of all candidate markets. Successful candidates will be removed from this list
	 * @param archetype
	 * @param num Number of markets to pick
	 * @return A list of the top markets for the archetype
	 */
	public List<ProcGenEntity> assignArchetypesToTopMarkets(List<ProcGenEntity> markets, Archetype archetype, int num)
	{
		log.info("Assigning archetypes for archetype " + archetype.name() + ", available: " + markets.size());
		List<ProcGenEntity> sorted = getOrderedListOfMarketsForArchetype(markets, archetype);
		List<ProcGenEntity> results = new ArrayList<>();
		for (int i=0; i<num; i++)
		{
			ProcGenEntity market = sorted.get(i);
			market.archetype = archetype;
			results.add(market);
			log.info("\t" + market.name + " has archetype " + archetype.toString());
		}
		
		marketsByArchetype.put(archetype, results);
		
		markets.removeAll(results);
		
		return results;
	}
	
	public void pickMarketArchetypes(Collection<ProcGenEntity> markets)
	{
		List<ProcGenEntity> marketsCopy = new ArrayList<>(markets);
		int numMarkets = markets.size();
		int numAgri = (int)(numMarkets * PLANET_ARCHETYPE_QUOTAS.get(Archetype.AGRICULTURE));
		int numOre = (int)(numMarkets * PLANET_ARCHETYPE_QUOTAS.get(Archetype.ORE));
		int numVol = (int)(numMarkets * PLANET_ARCHETYPE_QUOTAS.get(Archetype.VOLATILES));
		int numOrg = (int)(numMarkets * PLANET_ARCHETYPE_QUOTAS.get(Archetype.ORGANICS));
		int numManf = (int)(numMarkets * PLANET_ARCHETYPE_QUOTAS.get(Archetype.MANUFACTURING));
		int numHI = (int)(numMarkets * PLANET_ARCHETYPE_QUOTAS.get(Archetype.HEAVY_INDUSTRY));
		
		marketScoresForArchetypes = getMarketArchetypeScores(markets);
		
		List<ProcGenEntity> agriMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.AGRICULTURE, numAgri);
		List<ProcGenEntity> oreMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.ORE, numOre);
		List<ProcGenEntity> volMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.VOLATILES, numVol);	
		List<ProcGenEntity> orgMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.ORGANICS, numOrg);
		List<ProcGenEntity> manfMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.MANUFACTURING, numManf);
		List<ProcGenEntity> hiMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.HEAVY_INDUSTRY, numHI);
		
		// assign any remaining markets to misc type
		List<ProcGenEntity> miscMarkets = assignArchetypesToTopMarkets(marketsCopy, Archetype.MISC, marketsCopy.size());
	}
	
	public Archetype pickArchetypeForStation(ProcGenEntity station)
	{
		WeightedRandomPicker<Archetype> picker = new WeightedRandomPicker<>(random);
		picker.add(Archetype.MISC, 4);
		if (station.terrain != null)
		{
			if (station.terrain.getType().equals(Terrain.ASTEROID_BELT) || station.terrain.getType().equals(Terrain.ASTEROID_FIELD))
				picker.add(Archetype.ORE, 20);
			else if (station.terrain.getType().equals(Terrain.RING))
				picker.add(Archetype.VOLATILES, 20);
		}
		
		if (station.primary instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI) station.primary;
			if (planet.isGasGiant())
				picker.add(Archetype.VOLATILES, 30);
			else
			{
				Map<Archetype, Float> scores = getMarketArchetypeScore(procGen.createEntityDataForPlanet(planet));
				for (Map.Entry<Archetype, Float> tmp : scores.entrySet())
				{
					if (tmp.getValue() > 0)
						picker.add(tmp.getKey(), tmp.getValue() * 0.1f);
				}
			}
		}
		
		return picker.pick();
	}
	
	// =========================================================================
	// other stuff
	
	protected int getSizeFromRotation(int[] array, int num)
	{
		return array[num % array.length];
	}
	
	protected void addCabalSubmarkets()
	{
		// add Cabal submarkets
		if (ExerelinModPlugin.HAVE_UNDERWORLD)
		{
			List<MarketAPI> cabalCandidates = new ArrayList<>();
			List<MarketAPI> cabalCandidatesBackup = new ArrayList<>();
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
			{
				if (!market.getFactionId().equals(Factions.TRITACHYON)) continue;
				if (market.hasCondition(Conditions.MILITARY_BASE) && random.nextFloat() > CABAL_MILITARY_MARKET_CHANCE) 
				{
					cabalCandidatesBackup.add(market);
					continue;
				}
				
				//log.info("Cabal candidate added: " + market.getName() + " (size " + market.getSize() + ")");
				cabalCandidates.add(market);
			}
			if (cabalCandidates.isEmpty())
				cabalCandidates = cabalCandidatesBackup;
			
			Comparator<MarketAPI> marketSizeComparator = new Comparator<MarketAPI>() {

				public int compare(MarketAPI m1, MarketAPI m2) {
				   int size1 = m1.getSize();
					int size2 = m2.getSize();

					if (size1 > size2) return -1;
					else if (size2 > size1) return 1;
					else return 0;
				}};
			
			Collections.sort(cabalCandidates, marketSizeComparator);
			
			try {
				for (int i=0; i<cabalCandidates.size()*CABAL_MARKET_MULT; i++)
				{
					MarketAPI market = cabalCandidates.get(i);
					market.addSubmarket("uw_cabalmarket");
					market.addCondition("cabal_influence");
					log.info("Added Cabal submarket to " + market.getName() + " (size " + market.getSize() + ")");
				}
			} catch (RuntimeException rex) {
				// old SS+ version, do nothing
			}
		}
	}
	
	protected boolean isPlanetOfType(PlanetAPI planet, String wantedType)
	{
		if (planet.getTypeId().startsWith(wantedType)) return true;
		if (planet.getSpec().getName().toLowerCase(Locale.ROOT).startsWith(wantedType)) 
			return true;
		
		return false;
	}
	
	protected void addMarketConditionForPlanetType(ProcGenEntity data)
	{
		if (!(data.entity instanceof PlanetAPI)) return;
		PlanetAPI planet = (PlanetAPI)data.entity;
		MarketAPI market = data.market;
		
		//log.info("Attempting to add planet type condition for planet " + planet.getName() + ": " + planet.getTypeId() + ", " + planet.getSpec().getName());
		if (isPlanetOfType(planet, "frozen") || isPlanetOfType(planet, "rocky_ice") 
				|| isPlanetOfType(planet, "US_blue") || isPlanetOfType(planet, "US_ice"))
		{
			market.addCondition(Conditions.ICE);
		}
		else if (isPlanetOfType(planet, "barren") || isPlanetOfType(planet, "rocky_metallic") 
				|| isPlanetOfType(planet, "barren-bombarded"))
		{

		}
		else if (isPlanetOfType(planet, "barren-desert") || isPlanetOfType(planet, "US_red"))
		{
			market.addCondition("barren_marginal");
		}
		else if (isPlanetOfType(planet, "terran-eccentric") || isPlanetOfType(planet, "US_lifeless"))
		{
			market.addCondition("twilight");
		}
		else if (isPlanetOfType(planet, "terran") || isPlanetOfType(planet, "US_continent"))
		{
			market.addCondition(Conditions.TERRAN);
		}
		else if (isPlanetOfType(planet, "jungle") || isPlanetOfType(planet, "US_alkali"))
		{
			market.addCondition(Conditions.JUNGLE);
		}
		else if (isPlanetOfType(planet, "arid") || isPlanetOfType(planet, "US_lifelessArid") 
				|| isPlanetOfType(planet, "auric") )
		{
			market.addCondition(Conditions.ARID);
		}
		else if (isPlanetOfType(planet, "desert") || isPlanetOfType(planet, "US_crimson"))
		{
			market.addCondition(Conditions.DESERT);
		}
		else if (isPlanetOfType(planet, "water"))
		{
			market.addCondition(Conditions.WATER);
		}
		else if (isPlanetOfType(planet, "tundra"))
		{
			market.addCondition("tundra");
		}
		else if (isPlanetOfType(planet, "cryovolcanic"))
		{
			market.addCondition("cryovolcanic");
		}
	}
	
	// =========================================================================
	// main market adding method
	protected MarketAPI addMarket(ProcGenEntity data, String factionId)
	{
		log.info("Creating market for " + data.name + " (" + data.type + "), faction " + factionId);
		
		SectorEntityToken entity = data.entity;
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
			marketSize = Math.min(marketSize + 1, 6);
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
		
		MarketAPI market = entity.getMarket();
		if (market == null) {
			market = Global.getFactory().createMarket(entity.getId(), entity.getName(), marketSize);
			entity.setMarket(market);
			market.setPrimaryEntity(entity);
		}
		else 
		{
			market.setSize(marketSize);
		}
		data.market = market;
		market.setFactionId(factionId);
		market.setPlanetConditionMarketOnly(false);
		// needed?
		//market.setBaseSmugglingStabilityValue(0);
		
		if (data.isHQ)
		{
			market.addCondition(Conditions.HEADQUARTERS);
			market.addCondition(Conditions.MILITARY_BASE);
			//newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);	// dependent on number of factions; bad idea
			//market.addCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			//market.addCondition("nex_recycling_plant");
			market.addCondition("exerelin_supply_workshop");
			//market.addCondition("exerelin_hydroponics");
			if (data == procGen.getHomeworld()) 
			{
				//market.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
				//newMarket.addCondition(Conditions.SHIPBREAKING_CENTER);
				//market.addCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION);
			}
		}
		else if (data.isCapital)
		{
			//market.addCondition(Conditions.REGIONAL_CAPITAL);
			//market.addCondition("nex_recycling_plant");
			//market.addCondition("exerelin_supply_workshop");
			//newMarket.addCondition("exerelin_hydroponics");
		}
		market.addCondition("population_" + marketSize);
		market.removeCondition(Conditions.DECIVILIZED);
		
		boolean isPirate = ExerelinUtilsFaction.isPirateFaction(factionId);
		
		int minSizeForMilitaryBase = 6;
		if (isMoon) minSizeForMilitaryBase = 5;
		else if (isStation) minSizeForMilitaryBase = 5;
		if (isPirate) minSizeForMilitaryBase -= 1;
		
		if (marketSize >= minSizeForMilitaryBase && !market.hasCondition(Conditions.MILITARY_BASE))
		{
			float roll = (random.nextFloat() + random.nextFloat())*0.5f;
			float req = FORCE_MILITARY_BASE_CHANCE;
			if (isPirate) req = FORCE_MILITARY_BASE_CHANCE_PIRATE;
			if (roll > req)
				market.addCondition(Conditions.MILITARY_BASE);
		}
		
		// planet type stuff
		if (!isStation)
		{
			if (planetType.equals("terran-eccentric"))
			{
				// add mirror/shade
				LocationAPI system = entity.getContainingLocation();
				SectorEntityToken mirror = system.addCustomEntity(entity.getId() + "_mirror", "Stellar Mirror", "stellar_mirror", factionId);
				mirror.setCircularOrbitPointingDown(entity, ExerelinUtilsAstro.getCurrentOrbitAngle(entity.getOrbitFocus(), entity) + 180, 
						entity.getRadius() + 150, data.entity.getOrbit().getOrbitalPeriod());
				mirror.setCustomDescriptionId("stellar_mirror");
				SectorEntityToken shade = system.addCustomEntity(entity.getId() + "_shade", "Stellar Shade", "stellar_shade", factionId);
				shade.setCircularOrbitPointingDown(entity, ExerelinUtilsAstro.getCurrentOrbitAngle(entity.getOrbitFocus(), entity), 
						entity.getRadius() + 150, data.entity.getOrbit().getOrbitalPeriod());		
				shade.setCustomDescriptionId("stellar_shade");
				
				((PlanetAPI)entity).getSpec().setRotation(0);	// planet don't spin
			}
		}
				
		if (marketSize <= 4 && !isStation){
			market.addCondition(Conditions.FRONTIER);
		}
		
		// add random market conditions
		initMarketPointsAndAddRandomConditions(market, data);

		if (isStation && marketSize >= 3)
		{
			//newMarket.addCondition("nex_recycling_plant");
		}
		
		// add per-faction market conditions
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (config.freeMarket)
		{
			market.addCondition(Conditions.FREE_PORT);
		}
		
		market.getTariff().modifyFlat("generator", Global.getSector().getFaction(factionId).getTariffFraction());
		ExerelinUtilsMarket.setTariffs(market);
		
		// Luddic Majority
		if (factionId.equals(Factions.LUDDIC_CHURCH) || factionId.equals(Factions.LUDDIC_PATH)) {
			if (random.nextFloat() > LUDDIC_MINORITY_CHANCE || data.isHQ)
				market.addCondition(Conditions.LUDDIC_MAJORITY);
		}
		else
		{
			if (random.nextFloat() < LUDDIC_MAJORITY_CHANCE && !data.isHQ)
				market.addCondition(Conditions.LUDDIC_MAJORITY);
		}
		
		if (factionId.equals("spire")) {
			market.addCondition("aiw_inorganic_populace");
		}
		else if (factionId.equals("crystanite")) {
			//newMarket.addCondition("crys_population");
		}
		else if (factionId.equals("interstellarimperium") && !market.hasCondition(Conditions.DISSIDENT)
				&& !market.hasCondition(Conditions.LARGE_REFUGEE_POPULATION)) {
			market.addCondition("ii_imperialdoctrine");
		}
		
		// submarkets
		if (factionId.equals("templars"))
		{
			market.addSubmarket("tem_templarmarket");
			market.addCondition("exerelin_templar_control");
		}
		else
		{
			market.addSubmarket(Submarkets.SUBMARKET_OPEN);
			market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		}
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		Global.getSector().getEconomy().addMarket(market);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		// Lion's Guard
		if (data.isHQ && factionId.equals(Factions.DIKTAT))
		{
			ExerelinLionsGuardFleetManager script = new ExerelinLionsGuardFleetManager(market);
			entity.addScript(script);
		}
		
		//addStartingMarketCommodities(market);	// done after balancing
		
		for (MarketConditionAPI cond : market.getConditions())
		{
			balancer.onAddMarketCondition(market, cond);
		}
		
		// remove excess market conditions (prevent GUI overflow)
		int condCount = market.getConditions().size();
		Set<MarketConditionAPI> toRemove = new HashSet<>();
		int needRemoval = condCount - MAX_CONDITIONS;
		if (needRemoval > 0)
		{
			List<MarketConditionAPI> conds = new ArrayList<>(market.getConditions());
			Collections.shuffle(conds, random);
			for (MarketConditionAPI cond : conds)
			{
				if (cond.getGenSpec() != null && cond.getGenSpec().getXpMult() > 1)
				{
					toRemove.add(cond);
					log.info("\tRemoving surplus market condition " + cond.getId());
					needRemoval--;
					if (needRemoval <= 0) break;
				}
			}
			for (MarketConditionAPI cond : toRemove)
			{
				market.removeSpecificCondition(cond.getIdForPluginModifications());
			}
		}
		
		procGen.pickEntityInteractionImage(data.entity, market, planetType, data.type);
		
		return market;
	}
	
	// =========================================================================	
	// Economy balancer functions
	
	
	public static void addStartingMarketCommodities(MarketAPI market)
	{
		for (CommodityOnMarketAPI commodity : market.getAllCommodities())
		{
			if (commodity.isNonEcon()) continue;
			if (commodity.getCommodity().hasTag("noseed")) continue;
			float demand = commodity.getDemand().getDemand().modified;
			float unmet = 1.2f - commodity.getDemand().getFractionMet();
			commodity.addToStockpile(demand * unmet);
			
			float supply = commodity.getSupply().modified;
			commodity.addToStockpile(supply);
		}
	}
	
	public static boolean hasProductiveCondition(MarketAPI market)
	{
		for (MarketConditionAPI cond : market.getConditions())
		{
			String id = cond.getId();
			if (id.equals("nex_recycling_plany")) return true;
			if (conditionsByID.containsKey(id))
			{
				MarketConditionDef def = conditionsByID.get(id);
				if (def.productive) return true;
			}
		}
		return false;
	}
	
	// =========================================================================
	// static classes
	public static class MarketConditionDef
	{
		final String name;
		int cost = 0;
		Map<Archetype, Float> archetypes = new HashMap<>();
		float chance = 0;
		int minSize = 0;
		int maxSize = 99;
		boolean allowDuplicates = true;
		boolean allowStations = true;
		boolean special = false;
		boolean productive = true;
		String requiredFaction;
		final List<String> allowedPlanets = new ArrayList<>();
		final List<String> disallowedPlanets = new ArrayList<>();
		final List<String> conflictsWith = new ArrayList<>();
		final List<String> requiresOneOf = new ArrayList<>();
		Archetype noRequireForArchetype = null;

		public MarketConditionDef(String name)
		{
			this.name = name;
		}
	}
	
	public static enum Archetype
	{
		AGRICULTURE, ORE, VOLATILES, ORGANICS, MANUFACTURING, HEAVY_INDUSTRY, MISC
	}
}