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
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.econ.HydroponicsLab;
import exerelin.campaign.econ.RecyclingPlant;
import exerelin.campaign.econ.SupplyWorkshop;
import exerelin.campaign.fleets.ExerelinLionsGuardFleetManager;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.ExerelinProcGen.EntityType;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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
 *	Size 2: 200
 *	Size 3: 250
 *	Size 4: 350
 *	Size 5: 450
 *	Size 6: 650
 *	Size 7: 850
 * Also have chance for bonus points based on market size
 * Special conditions allowed:
 *	Size 1-4: random(0,1)
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
	public static final float LUDDIC_MAJORITY_CHANCE = 0.1f;	// how many markets have Luddic majority even if they aren't Luddic at start
	public static final float LUDDIC_MINORITY_CHANCE = 0.15f;	// how many markets that start under Church control are non-Luddic
	public static final float PRE_BALANCE_BUDGET_MULT = 1;	// < 0 to spare some for balancer
	
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MIN = 1.3f;
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MAX = 0.5f;	// lower than min so it can swap autofacs for shipbreakers if needed
	
	protected static final int[] PLANET_SIZE_ROTATION = new int[] {4, 5, 6, 5, 4};
	protected static final int[] MOON_SIZE_ROTATION = new int[] {3, 4, 5, 4};
	protected static final int[] STATION_SIZE_ROTATION = new int[] {3, 4, 5, 4, 3};
	
	protected final Map<String, Map<Archetype, Float>> conditionArchetypes = new HashMap<>();
	protected final List<MarketConditionDef> conditions = new ArrayList<>();
	protected final Map<String, MarketConditionDef> conditionsByID = new HashMap<>();
	protected final List<MarketConditionDef> specialConditions = new ArrayList<>();
	
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
	
	static {
		// (probably) must sum to 1
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.AGRICULTURE, 0.2f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.ORE, 0.2f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.ORGANICS, 0.15f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.VOLATILES, 0.15f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.MANUFACTURING, 0.2f);
		PLANET_ARCHETYPE_QUOTAS.put(Archetype.HEAVY_INDUSTRY, 0.1f);
	}
	
	public ExerelinMarketBuilder(ExerelinProcGen procGen)
	{
		this.procGen = procGen;
		random = procGen.getRandom();
		
		try {
			JSONArray conditionArchetypesCsv = Global.getSettings().getMergedSpreadsheetDataForMod(
					"condition", SURVEY_CONDITION_FILE, "nexerelin");
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
				if (condJson.has("requires"))
				{
					cond.requires.addAll(ExerelinUtils.JSONArrayToArrayList(condJson.getJSONArray("requires")));
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
			
		} catch (IOException | JSONException ex) {
			log.error(ex);
		}
	}
	
	/**
	 * Returns a map of the weighings a market condition has for each archetype, as specified in the CSV data
	 * @param csvRow Row from the loaded CSV
	 * @return
	 */
	protected Map<Archetype, Float> loadSurveyConditionEntry(JSONObject csvRow)
	{
		Map<Archetype, Float> ret = new HashMap<>();
		for (Archetype archetype : nonMiscArchetypes)
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
	protected float getConditionWeightForArchetype(MarketConditionDef cond, Archetype archetype, float defaultWeight)
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
	protected float getConditionWeightForArchetype(String condID, Archetype archetype, float defaultWeight)
	{
		if (!conditionsByID.containsKey(condID)) return defaultWeight;
		return getConditionWeightForArchetype(conditionsByID.get(condID), archetype, defaultWeight);
	}
	
	protected boolean isConditionAllowedForPlanet(MarketConditionDef cond, String planetType)
	{
		if (!cond.allowedPlanets.isEmpty())
		{
			if (!cond.allowedPlanets.contains(planetType))
				return false;
		}
		if (cond.disallowedPlanets.isEmpty())
		{
			if (cond.disallowedPlanets.contains(planetType))
				return false;
		}
		return true;
	}
	
	protected boolean isConditionAllowedForPlanet(String condID, String planetType)
	{
		return isConditionAllowedForPlanet(conditionsByID.get(condID), planetType);
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
	
	protected MarketConditionDef pickMarketCondition(MarketAPI market, List<MarketConditionDef> possibleConds, ProcGenEntity entityData, int budget, boolean isFirst)
	{
		WeightedRandomPicker<MarketConditionDef> picker = new WeightedRandomPicker<>(random);
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
			if (!isConditionAllowedForPlanet(possibleCond, planetType)) continue;
			if (hasConflict(possibleCond, market)) continue;
			
			float weight = getConditionWeightForArchetype(possibleCond, entityData.archetype, 0);
			if (weight <= 0) continue;
			
			picker.add(possibleCond, weight);
			numConds++;
		}
		
		if (numConds == 0)
			return null;	// out of possible conditions; nothing more to do
		
		return picker.pick();
	}
	
	public void addMarketCondition(MarketAPI market, ProcGenEntity entityData, MarketConditionDef cond)
	{
		market.addCondition(cond.name);
		entityData.marketPointsSpent += cond.cost;
		
		if (cond.name.equals(Conditions.ORBITAL_STATION))
		{
			ProcGenEntity station = procGen.createEntityDataForStation(entityData.entity);
			station.market = market;
			procGen.createStation(station, market.getFactionId(), false);
		}
	}
	
	public void addMarketCondition(MarketAPI market, ProcGenEntity entityData, String cond)
	{
		addMarketCondition(market, entityData, conditionsByID.get(cond));
	}
	
	public void removeMarketCondition(MarketAPI market, ProcGenEntity entityData, MarketConditionDef cond)
	{
		ExerelinUtilsMarket.removeOneMarketCondition(market, cond.name);
		entityData.marketPointsSpent -= cond.cost;
	}
	
	public void removeMarketCondition(MarketAPI market, ProcGenEntity entityData, String cond)
	{
		removeMarketCondition(market, entityData, conditionsByID.get(cond));
	}
	
	public void initMarketPointsAndAddRandomConditions(MarketAPI market, ProcGenEntity entityData)
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
			if (random.nextFloat() > 0.4) bonusPoints += 50;
		}
				
		entityData.bonusMarketPoints = bonusPoints;
		points += bonusPoints;
		entityData.marketPoints = points;
		
		while (entityData.marketPointsSpent < points * PRE_BALANCE_BUDGET_MULT)
		{
			MarketConditionDef cond = pickMarketCondition(market, conditions, entityData, (int)(points * PRE_BALANCE_BUDGET_MULT - entityData.marketPointsSpent), false);
			if (cond == null) break;
			log.info("\tAdding condition: " + cond.name);
			addMarketCondition(market, entityData, cond);
		}
		
		int numSpecial = 0;
		if (size == 2 && random.nextFloat() > 0.5) numSpecial = 1;
		else if (size <= 4) numSpecial = ExerelinUtils.randomNextIntInclusive(random, 1);
		else if (size <= 6) numSpecial = ExerelinUtils.randomNextIntInclusive(random, 1) + ExerelinUtils.randomNextIntInclusive(random, 1);
		else if (size <= 8) numSpecial = 1 + ExerelinUtils.randomNextIntInclusive(random, 1);
		
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
		for (Archetype archetype : nonMiscArchetypes)
		{
			if (archetype == wantedArchetype) continue;
			float score = scores.get(archetype);
			if (score > bestScore)
				bestScore = score;
		}
		return bestScore;
	}
	
	/**
	 * Returns a map of markets to maps of their scores for each archetype
	 * @param markets
	 * @return 
	 */
	protected Map<ProcGenEntity, Map<Archetype, Float>> getMarketArchetypeScores(List<ProcGenEntity> markets)
	{
		Map<ProcGenEntity, Map<Archetype, Float>> scores = new HashMap<>();
		for (ProcGenEntity market : markets)
		{
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
				for (Archetype archetype : nonMiscArchetypes)
				{
					marketScores.put(archetype, marketScores.get(archetype) + condScores.get(archetype));
				}
			}
			scores.put(market, marketScores);
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
				else if (weight1 > weight2) return 1;
				else return -1;
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
		}
		
		marketsByArchetype.put(archetype, results);
		
		markets.removeAll(results);
		
		return results;
	}
	
	public void pickMarketArchetypes(List<ProcGenEntity> markets)
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
		picker.add(Archetype.MISC, 2);
		if (station.terrain != null)
		{
			if (station.terrain.getType().equals(Terrain.ASTEROID_BELT) || station.terrain.getType().equals(Terrain.ASTEROID_FIELD))
				picker.add(Archetype.ORE, 5);
			else if (station.terrain.getType().equals(Terrain.RING))
				picker.add(Archetype.VOLATILES, 4);
		}
		
		if (station.primary instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI) station.primary;
			if (planet.isGasGiant())
				picker.add(Archetype.VOLATILES, 4);
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
	
	// =========================================================================
	// main market adding method
	protected MarketAPI addMarket(ProcGenEntity data, String factionId)
	{
		log.info("Creating market for " + data.name + " (" + data.type + ")");
		
		
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
		
		MarketAPI market = entity.getMarket();
		if (market == null) {
			log.info("No market, creating one");
			market = Global.getFactory().createMarket(entity.getId(), entity.getName(), marketSize);
			entity.setMarket(market);
			market.setPrimaryEntity(entity);
		}
		else 
		{
			log.info("Has market, converting");
			Global.getFactory().convertToRegularMarket(market);
			market.setSize(marketSize);
		}
		market.setFactionId(factionId);
		market.setPlanetConditionMarketOnly(false);
		// needed?
		//market.setBaseSmugglingStabilityValue(0);
		
		if (data.isHQ)
		{
			market.addCondition(Conditions.HEADQUARTERS);
			//newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);	// dependent on number of factions; bad idea
			//market.addCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			//market.addCondition("exerelin_recycling_plant");
			//market.addCondition("exerelin_recycling_plant");
			//market.addCondition("exerelin_supply_workshop");
			//market.addCondition("exerelin_hydroponics");
			if (false)	//(data == sectorGen.homeworld) 
			{
				//newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
				market.addCondition("exerelin_supply_workshop");
				//newMarket.addCondition(Conditions.SHIPBREAKING_CENTER);
				market.addCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION);
			}
		}
		else if (data.isCapital)
		{
			//market.addCondition(Conditions.REGIONAL_CAPITAL);
			//market.addCondition("exerelin_recycling_plant");
			//market.addCondition("exerelin_supply_workshop");
			//newMarket.addCondition("exerelin_hydroponics");
		}
		market.addCondition("population_" + marketSize);
		market.removeCondition(Conditions.DECIVILIZED);
		
		int minSizeForMilitaryBase = 6;
		if (ExerelinUtilsFaction.isPirateFaction(factionId))
			minSizeForMilitaryBase = 5;
		if (isMoon) minSizeForMilitaryBase = 5;
		else if (isStation) minSizeForMilitaryBase = 5;
		
		if (marketSize >= minSizeForMilitaryBase)
		{
			market.addCondition(Conditions.MILITARY_BASE);
		}
		
		// planet type stuff
		if (planetType != null && !planetType.isEmpty())
		{
			//log.info("Attempting to add planet type condition: " + planetType);
			if (planetType.equals("terran-eccentric"))
			{
				// add mirror/shade
				LocationAPI system = entity.getContainingLocation();
				SectorEntityToken mirror = system.addCustomEntity(entity.getId() + "_mirror", "Stellar Mirror", "stellar_mirror", factionId);
				mirror.setCircularOrbitPointingDown(entity, ExerelinUtilsAstro.getCurrentOrbitAngle(entity.getOrbitFocus(), entity), 
						entity.getRadius() + 150, data.entity.getOrbit().getOrbitalPeriod());
				mirror.setCustomDescriptionId("stellar_mirror");
				SectorEntityToken shade = system.addCustomEntity(entity.getId() + "_shade", "Stellar Shade", "stellar_shade", factionId);
				shade.setCircularOrbitPointingDown(entity, ExerelinUtilsAstro.getCurrentOrbitAngle(entity.getOrbitFocus(), entity) + 180, 
						entity.getRadius() + 150, data.entity.getOrbit().getOrbitalPeriod());		
				shade.setCustomDescriptionId("stellar_shade");
			}
		}
				
		if (marketSize <= 4 && !isStation){
			market.addCondition(Conditions.FRONTIER);
		}
		
		// add random market conditions
		initMarketPointsAndAddRandomConditions(market, data);

		if (isStation && marketSize >= 3)
		{
			//newMarket.addCondition("exerelin_recycling_plant");
		}
				
		// add per-faction market conditions
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (config.freeMarket)
		{
			market.addCondition(Conditions.FREE_PORT);
		}
		
		market.getTariff().modifyFlat("generator", Global.getSector().getFaction(factionId).getTariffFraction());
		ExerelinUtilsMarket.setTariffs(market);
		
		if (factionId.equals(Factions.LUDDIC_CHURCH) && random.nextFloat() < LUDDIC_MINORITY_CHANCE
				|| random.nextFloat() < LUDDIC_MAJORITY_CHANCE) {
			market.addCondition(Conditions.LUDDIC_MAJORITY);
			//newMarket.addCondition("cottage_industry");
		}
		else if (factionId.equals("spire")) {
			market.addCondition("aiw_inorganic_populace");
		}
		else if (factionId.equals("crystanite")) {
			//newMarket.addCondition("crys_population");
		}
		
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
		
		// maybe already handled by convertToRegularMarket above?
		Global.getSector().getEconomy().addMarket(market);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		if (data.isHQ && factionId.equals(Factions.DIKTAT))
		{
			ExerelinLionsGuardFleetManager script = new ExerelinLionsGuardFleetManager(market);
			entity.addScript(script);
		}
		
		//market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);	// probably not needed
		market.reapplyConditions();	// this breaks demand getter?
		addStartingMarketCommodities(market);
		
		// count some demand/supply values for market balancing
		
		//domesticGoodsSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.DOMESTIC_GOODS);
		//metalSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.METALS);
		//suppliesSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.SUPPLIES);

		int autofacCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.AUTOFAC_HEAVY_INDUSTRY);
		int shipbreakingCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.SHIPBREAKING_CENTER);
		int fuelProdCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.ANTIMATTER_FUEL_PRODUCTION);
		int recyclingCount = ExerelinUtilsMarket.countMarketConditions(market, "exerelin_recycling_plant");
		int workshopCount = ExerelinUtilsMarket.countMarketConditions(market, "exerelin_supply_workshop");
		float pop = ExerelinUtilsMarket.getPopulation(marketSize);
		
		// domestic goods
		float dgSupply = ExerelinUtilsMarket.countMarketConditions(market, Conditions.LIGHT_INDUSTRIAL_COMPLEX) * ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS;
		dgSupply += ExerelinUtilsMarket.countMarketConditions(market, Conditions.COTTAGE_INDUSTRY) * ConditionData.COTTAGE_INDUSTRY_DOMESTIC_GOODS;
		dgSupply *= pop * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
		modifyCommoditySupply(Commodities.DOMESTIC_GOODS, dgSupply);
		modifyCommodityDemand(Commodities.DOMESTIC_GOODS, pop * ConditionData.POPULATION_DOMESTIC_GOODS * ExerelinUtilsMarket.getCommodityDemandMult(market, Commodities.DOMESTIC_GOODS));
		
		// metal
		float mSupply = ExerelinUtilsMarket.countMarketConditions(market, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_ORE;
		mSupply += shipbreakingCount * ConditionData.SHIPBREAKING_METALS;
		mSupply += recyclingCount * RecyclingPlant.RECYCLING_METALS * RecyclingPlant.HAX_MULT_07_METALS;
		mSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
		modifyCommoditySupply(Commodities.METALS, mSupply);
		float mDemand = autofacCount * ConditionData.AUTOFAC_HEAVY_METALS;
		mDemand += workshopCount * SupplyWorkshop.WORKSHOP_METALS;
		modifyCommodityDemand(Commodities.METALS, mDemand);
		//modifyCommodityDemand(Commodities.METALS, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.METALS) * 0.5f);	// hax
		
		// rare metal
		float rmSupply = ExerelinUtilsMarket.countMarketConditions(market, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_RARE_ORE;
		rmSupply += shipbreakingCount * ConditionData.SHIPBREAKING_RARE_METALS;
		rmSupply += recyclingCount * RecyclingPlant.RECYCLING_RARE_METALS * RecyclingPlant.HAX_MULT_07_METALS;
		rmSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.RARE_METALS);
		ExerelinMarketBuilder.this.modifyCommoditySupply(Commodities.RARE_METALS, rmSupply);
		float rmDemand = autofacCount * ConditionData.AUTOFAC_HEAVY_RARE_METALS;
		rmDemand += workshopCount * SupplyWorkshop.WORKSHOP_RARE_METALS;
		rmDemand += fuelProdCount * ConditionData.FUEL_PRODUCTION_RARE_METALS;
		modifyCommodityDemand(Commodities.RARE_METALS, rmDemand);
		
		// supplies
		float sSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_SUPPLIES; 
		sSupply += shipbreakingCount * ConditionData.SHIPBREAKING_SUPPLIES;
		sSupply += recyclingCount * RecyclingPlant.RECYCLING_SUPPLIES;
		sSupply += workshopCount * SupplyWorkshop.WORKSHOP_SUPPLIES;
		sSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
		modifyCommoditySupply(Commodities.SUPPLIES, sSupply);
		float sDemand = ExerelinUtilsMarket.getCommodityDemand(market, Commodities.SUPPLIES);
		sDemand += 0.0013 * pop;	// fudge factor to account for crew and marines using supplies
		modifyCommodityDemand(Commodities.SUPPLIES, sDemand * 1.2f);	// hax
		
		// fuel
		float fSupply = fuelProdCount * ConditionData.FUEL_PRODUCTION_FUEL;
		fSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FUEL);
		modifyCommoditySupply(Commodities.FUEL, fSupply);
		
		modifyCommodityDemand(Commodities.FUEL, ExerelinUtilsMarket.getMarketBaseFuelDemand(market, 20*marketSize));
		//modifyCommodityDemand(Commodities.FUEL, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.FUEL) * 0.7f);
		
		// food
		modifyCommoditySupply(Commodities.FOOD, ExerelinUtilsMarket.getMarketBaseFoodSupply(market, true));
		modifyCommodityDemand(Commodities.FOOD, ConditionData.POPULATION_FOOD * ExerelinUtilsMarket.getPopulation(marketSize));
		
		// guns (hand weapons)
		float gSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_HAND_WEAPONS;
		gSupply += workshopCount * SupplyWorkshop.WORKSHOP_HAND_WEAPONS;
		gSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
		modifyCommoditySupply(Commodities.HAND_WEAPONS, gSupply);
		modifyCommodityDemand(Commodities.HAND_WEAPONS, ExerelinUtilsMarket.getCommodityDemand(market, Commodities.HAND_WEAPONS));
		
		// machinery
		float hmSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_MACHINERY;
		hmSupply += shipbreakingCount * ConditionData.SHIPBREAKING_MACHINERY;
		hmSupply += workshopCount * SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY;
		hmSupply += recyclingCount * RecyclingPlant.RECYCLING_HEAVY_MACHINERY;
		hmSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
		modifyCommoditySupply(Commodities.HEAVY_MACHINERY, hmSupply);
		//modifyCommodityDemand(Commodities.HEAVY_MACHINERY, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.HEAVY_MACHINERY) * 0.75f);	// hax
		modifyCommodityDemand(Commodities.HEAVY_MACHINERY, ExerelinUtilsMarket.getMarketBaseMachineryDemand(market, 10 * marketSize));
		
		// organics
		float oSupply = ExerelinUtilsMarket.countMarketConditions(market, Conditions.ORGANICS_COMPLEX) * ConditionData.ORGANICS_MINING_ORGANICS;
		oSupply += ExerelinUtilsMarket.getFarmingFoodSupply(market, true) * ConditionData.FARMING_ORGANICS_FRACTION;
		oSupply += recyclingCount * RecyclingPlant.RECYCLING_ORGANICS * RecyclingPlant.HAX_MULT_07_OV;
		oSupply += ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
		modifyCommoditySupply(Commodities.ORGANICS, oSupply);
		modifyCommodityDemand(Commodities.ORGANICS, ExerelinUtilsMarket.getCommodityDemand(market, Commodities.ORGANICS) * 0.95f);	// hax
		
		// volatiles
		float vSupply = ExerelinUtilsMarket.countMarketConditions(market, Conditions.VOLATILES_COMPLEX) * ConditionData.VOLATILES_MINING_VOLATILES;
		vSupply += recyclingCount * RecyclingPlant.RECYCLING_VOLATILES * RecyclingPlant.HAX_MULT_07_OV;
		ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.VOLATILES);
		modifyCommoditySupply(Commodities.VOLATILES, vSupply);
		modifyCommodityDemand(Commodities.VOLATILES, ExerelinUtilsMarket.getCommodityDemand(market, Commodities.VOLATILES) * 1.05f);	// hax
		
		// ore
		modifyCommoditySupply(Commodities.ORE, ExerelinUtilsMarket.countMarketConditions(market, Conditions.ORE_COMPLEX) 
				* ConditionData.ORE_MINING_ORE * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORE));
		float orDemand = ExerelinUtilsMarket.countMarketConditions(market, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_ORE;
		if (market.hasCondition("aiw_inorganic_populace"))
			orDemand += (pop * ConditionData.POPULATION_FOOD) * 0.95f * 0.02f;
		modifyCommodityDemand(Commodities.ORE, orDemand);
		//modifyCommodityDemand(Commodities.ORE, ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.ORE));
		
		data.market = market;
		return market;
	}
	
	// =========================================================================	
	// Economy balancer functions
	
	protected void balanceDomesticGoods(List<ProcGenEntity> candidateEntities)
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
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float pop = ExerelinUtilsMarket.getPopulation(size);
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX)) 
			{
				// oversupply; remove this LIC and prioritise the market for any readding later
				if (domesticGoodsSupply > domesticGoodsDemand * 1.2)
				{
					removeMarketCondition(market, entity, Conditions.LIGHT_INDUSTRIAL_COMPLEX);
					domesticGoodsSupply -= ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS * pop
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
					organicsDemand -= ConditionData.LIGHT_INDUSTRY_ORGANICS * pop;
					volatilesDemand -= ConditionData.LIGHT_INDUSTRY_VOLATILES * pop;
					machineryDemand -= ConditionData.LIGHT_INDUSTRY_MACHINERY * pop;
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			int size = market.getSize();
			if (size > maxSize) continue;
			if (size < maxSize - 2) continue;
			
			float pop = ExerelinUtilsMarket.getPopulation(size);
			
			addMarketCondition(market, entity, Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			domesticGoodsSupply += ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS * pop
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
			organicsDemand += ConditionData.LIGHT_INDUSTRY_ORGANICS * pop;
			volatilesDemand += ConditionData.LIGHT_INDUSTRY_VOLATILES * pop;
			machineryDemand += ConditionData.LIGHT_INDUSTRY_MACHINERY * pop;
			log.info("Added balancing Light Industrial Complex to " + market.getName() + " (size " + size + ")");
		}
		log.info("Final domestic goods supply/demand: " + (int)domesticGoodsSupply + " / " + (int)domesticGoodsDemand);
		
		setCommoditySupply(Commodities.DOMESTIC_GOODS, domesticGoodsSupply);
		setCommodityDemand(Commodities.ORGANICS, organicsDemand);
		setCommodityDemand(Commodities.VOLATILES, volatilesDemand);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
	}
	
	protected void balanceRareMetal(List<ProcGenEntity> candidateEntities)
	{
		float suppliesSupply = getCommoditySupply(Commodities.SUPPLIES);
		float rareMetalSupply = getCommoditySupply(Commodities.RARE_METALS);
		float rareMetalDemand = getCommodityDemand(Commodities.RARE_METALS);
		float metalSupply = getCommoditySupply(Commodities.METALS);
		float machinerySupply = getCommoditySupply(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance rare metal supply/demand: " + (int)rareMetalSupply + " / " + (int)rareMetalDemand);
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
					metalSupply -= ConditionData.SHIPBREAKING_METALS
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
					rareMetalSupply -= ConditionData.SHIPBREAKING_RARE_METALS
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.RARE_METALS);
					machinerySupply -= ConditionData.SHIPBREAKING_MACHINERY
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			addMarketCondition(market, entity, Conditions.SHIPBREAKING_CENTER);
			suppliesSupply += ConditionData.SHIPBREAKING_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
			metalSupply += ConditionData.SHIPBREAKING_METALS
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
			rareMetalSupply += ConditionData.SHIPBREAKING_RARE_METALS
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.RARE_METALS);
			machinerySupply += ConditionData.SHIPBREAKING_MACHINERY
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
			log.info("Added balancing shipbreaking center to " + market.getName());
		}
		log.info("Final rare metal supply/demand: " + (int)rareMetalSupply + " / " + (int)rareMetalDemand);
		
		setCommoditySupply(Commodities.SUPPLIES, suppliesSupply);
		setCommoditySupply(Commodities.METALS, metalSupply);
		setCommoditySupply(Commodities.RARE_METALS, rareMetalSupply);
		setCommoditySupply(Commodities.HEAVY_MACHINERY, machinerySupply);
	}
	
	protected void balanceMachinery(List<ProcGenEntity> candidateEntities, boolean firstPass)
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
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
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
	
	protected void balanceSupplies(List<ProcGenEntity> candidateEntities)
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
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
					suppliesSupply -= SupplyWorkshop.WORKSHOP_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
					machinerySupply -= SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
					machineryDemand -= SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY_DEMAND;
					metalDemand -= SupplyWorkshop.WORKSHOP_METALS;
					rareMetalDemand -= SupplyWorkshop.WORKSHOP_RARE_METALS;
					gunsSupply -= SupplyWorkshop.WORKSHOP_HAND_WEAPONS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
					organicsDemand -= SupplyWorkshop.WORKSHOP_ORGANICS;
					volatilesDemand -= SupplyWorkshop.WORKSHOP_VOLATILES;
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
		
		while (suppliesDemand > suppliesSupply * 0.9f)
		{
			if (entityPicker.isEmpty())	break;
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			addMarketCondition(market, entity, "exerelin_supply_workshop");
			suppliesSupply += SupplyWorkshop.WORKSHOP_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
			machinerySupply += SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HEAVY_MACHINERY);
			machineryDemand += SupplyWorkshop.WORKSHOP_HEAVY_MACHINERY_DEMAND;
			metalDemand += SupplyWorkshop.WORKSHOP_METALS;
			rareMetalDemand += SupplyWorkshop.WORKSHOP_RARE_METALS;
			gunsSupply += SupplyWorkshop.WORKSHOP_HAND_WEAPONS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.HAND_WEAPONS);
			organicsDemand += SupplyWorkshop.WORKSHOP_ORGANICS;
			volatilesDemand += SupplyWorkshop.WORKSHOP_VOLATILES;
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

	protected void balanceFood(List<ProcGenEntity> candidateEntities)
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
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float pop = ExerelinUtilsMarket.getPopulation(size);
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
				if (market.hasCondition(Conditions.AQUACULTURE) && surplus > ConditionData.AQUACULTURE_FOOD_MULT * pop)
				{
					removeMarketCondition(market, entity, Conditions.AQUACULTURE);
					foodSupply -= ConditionData.AQUACULTURE_FOOD_MULT * pop;
					machineryDemand -= ConditionData.AQUACULTURE_MACHINERY_MULT * pop;
					organicsSupply -= baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
					
					weight *= 25;
					log.info("Removed balancing aquaculture from " + market.getName() + " (size " + size + ")");
				}
				else if (market.hasCondition(Conditions.RURAL_POLITY) && random.nextFloat() > 0.5 && surplus > baseFarming)
				{
					removeMarketCondition(market, entity, Conditions.RURAL_POLITY);
					foodSupply -= baseFarming;
					organicsSupply -= baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);;
					domesticGoodsDemand += ConditionData.POPULATION_DOMESTIC_GOODS * pop * 0.5;
					
					weight *= 25;
					log.info("Removed balancing Rural Polity from " + market.getName() + " (size " + size + ")");
				}
				else if (market.hasCondition("exerelin_hydroponics"))
				{
					removeMarketCondition(market, entity, "exerelin_hydroponics");
					foodSupply -= HydroponicsLab.HYDROPONICS_FOOD_POP_MULT * pop
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FOOD);
					machineryDemand -= HydroponicsLab.HYDROPONICS_HEAVY_MACHINERY_POP_MULT * pop;
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
			MarketAPI market = entity.market;
			
			int size = market.getSize();
			if (size > maxSize) continue;
			float pop = ExerelinUtilsMarket.getPopulation(size);
			float baseFarming = ExerelinUtilsMarket.getFarmingFoodSupply(market, false);
			
			if (isConditionAllowedForPlanet(Conditions.ORBITAL_BURNS, entity.planetType) && entity.type != EntityType.STATION && !market.hasCondition(Conditions.ORBITAL_BURNS)
					&& shortfall > baseFarming)
			{
				addMarketCondition(market, entity, Conditions.ORBITAL_BURNS);
				foodSupply += baseFarming;
				organicsSupply += baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
				log.info("Added balancing Orbital Burns to " + market.getName() + " (size " + size + ")");
			}
			else if (isConditionAllowedForPlanet(Conditions.AQUACULTURE, entity.planetType) && entity.type != EntityType.STATION
					&& shortfall > baseFarming)
			{
				addMarketCondition(market, entity, Conditions.AQUACULTURE);
				foodSupply += ConditionData.AQUACULTURE_FOOD_MULT * pop;
				machineryDemand += ConditionData.AQUACULTURE_MACHINERY_MULT * pop;
				organicsSupply += baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
				log.info("Added balancing Orbital Burns to " + market.getName() + " (size " + size + ")");
			}
			/*
			else if (entity.type != EntityType.STATION && getConditionWeightForArchetype(Conditions.RURAL_POLITY, entity.archetype, 0) > random.nextFloat() 
					&& !market.hasCondition(Conditions.RURAL_POLITY) && !market.hasCondition(Conditions.URBANIZED_POLITY) && shortfall > baseFarming && size >= 4)
			{
				addMarketCondition(market, entity, Conditions.RURAL_POLITY);
				foodSupply += baseFarming;
				organicsSupply += baseFarming * ConditionData.FARMING_ORGANICS_FRACTION * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.ORGANICS);
				domesticGoodsDemand -= ConditionData.POPULATION_DOMESTIC_MULT * pop * 0.5;
				log.info("Added balancing Rural Polity to " + market.getName() + " (size " + size + ")");
			}*/
			else
			{
				addMarketCondition(market, entity, "exerelin_hydroponics");
				foodSupply += HydroponicsLab.HYDROPONICS_FOOD_POP_MULT * ExerelinUtilsMarket.getPopulation(size) 
						* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FOOD);
				machineryDemand += HydroponicsLab.HYDROPONICS_HEAVY_MACHINERY_POP_MULT * pop;
				log.info("Added balancing Hydroponics Lab to " + market.getName() + " (size " + size + ")");
			}
		}
		log.info("Final food supply/demand: " + (int)foodSupply + " / " + (int)foodDemand);
		
		setCommoditySupply(Commodities.FOOD, foodSupply);
		setCommoditySupply(Commodities.ORGANICS, organicsSupply);
		setCommodityDemand(Commodities.HEAVY_MACHINERY, machineryDemand);
		setCommodityDemand(Commodities.DOMESTIC_GOODS, domesticGoodsDemand);
	}
	
	protected void balanceFuel(List<ProcGenEntity> candidateEntities)
	{
		float fuelSupply = getCommoditySupply(Commodities.FUEL);
		float fuelDemand = getCommodityDemand(Commodities.FUEL);
		float rareMetalDemand = getCommodityDemand(Commodities.RARE_METALS);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance fuel supply/demand: " + (int)fuelSupply + " / " + (int)fuelDemand);
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = Math.max(entity.marketPoints - entity.marketPointsSpent, 100);
			if (market.hasCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION) && entity.market != procGen.homeworld.market) 
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
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
	
	protected void balanceOrganics(List<ProcGenEntity> candidateEntities)
	{
		float organicsSupply = getCommoditySupply(Commodities.ORGANICS);
		float organicsDemand = getCommodityDemand(Commodities.ORGANICS);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance organics supply/demand: " + (int)organicsSupply + " / " + (int)organicsDemand);
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
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
	
	protected void balanceVolatiles(List<ProcGenEntity> candidateEntities)
	{
		float volatilesSupply = getCommoditySupply(Commodities.VOLATILES);
		float volatilesDemand = getCommodityDemand(Commodities.VOLATILES);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance volatiles supply/demand: " + (int)volatilesSupply + " / " + (int)volatilesDemand);
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
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
	
	protected void balanceMetal(List<ProcGenEntity> candidateEntities)
	{
		float metalSupply = getCommoditySupply(Commodities.METALS);
		float metalDemand = getCommodityDemand(Commodities.METALS);
		float oreDemand = getCommodityDemand(Commodities.ORE);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance metal supply/demand: " + (int)metalSupply + " / " + (int)metalDemand);
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
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
	
	protected void balanceOre(List<ProcGenEntity> candidateEntities)
	{
		float oreSupply = getCommoditySupply(Commodities.ORE);
		float oreDemand = getCommodityDemand(Commodities.ORE);
		float machineryDemand = getCommodityDemand(Commodities.HEAVY_MACHINERY);
		
		log.info("Pre-balance ore supply/demand: " + (int)oreSupply + " / " + (int)oreDemand);
		
		WeightedRandomPicker<ProcGenEntity> entityPicker = new WeightedRandomPicker<>(random);
		
		for (ProcGenEntity entity:candidateEntities)
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
			
			ProcGenEntity entity = entityPicker.pickAndRemove();
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
			log.info("\t" + commodity.toUpperCase() + " supply / demand: " + (int)supply + " / " + (int)demand);
		}
	}
	
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
		Map<Archetype, Float> archetypes = new HashMap<>();
		float chance = 0;
		int minSize = 0;
		int maxSize = 99;
		boolean allowDuplicates = true;
		boolean allowStations = true;
		boolean special = false;
		String requiredFaction;
		final List<String> allowedPlanets = new ArrayList<>();
		final List<String> disallowedPlanets  = new ArrayList<>();
		final List<String> requires = new ArrayList<>();
		final List<String> conflictsWith  = new ArrayList<>();

		public MarketConditionDef(String name)
		{
			this.name = name;
		}
	}
	
	public static Archetype[] nonMiscArchetypes = new Archetype[]{ Archetype.AGRICULTURE, Archetype.ORE, Archetype.VOLATILES, 
			Archetype.ORGANICS, Archetype.MANUFACTURING, Archetype.HEAVY_INDUSTRY };
	
	public static enum Archetype
	{
		AGRICULTURE, ORE, VOLATILES, ORGANICS, MANUFACTURING, HEAVY_INDUSTRY, MISC
	}
}