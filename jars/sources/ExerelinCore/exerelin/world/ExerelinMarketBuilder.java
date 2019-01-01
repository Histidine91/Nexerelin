package exerelin.world;

import java.util.List;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinFactionConfig.IndustrySeed;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.ExerelinProcGen.EntityType;
import exerelin.world.industry.IndustryClassGen;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * How it works.
 * Stage 1: init markets (add to economy, set size, add population industry), etc.
 *	Add defenses & starport based on size
 * Stage 2: add key manufacturing from config to randomly picked planet
 * Stage 3: for each market, for all possible industries, get priority, add to market in order of priority till max industries reached
 *	Priority is based on local resources and hazard rating
 * Stage 4: distribute bonus items to markets
 * 
 * Notes:
 *	Free stations in asteroid belt should have ore mining,
 *	free stations orbiting gas giant should have gas extraction
 * 
 * How to tell Luddic Church etc. "don't build industry"?
 * Perhaps just return 0 priority for those factions
*/

// TODO: overhaul pending
@SuppressWarnings("unchecked")
public class ExerelinMarketBuilder
{
	public static Logger log = Global.getLogger(ExerelinMarketBuilder.class);
	//private List possibleMarketConditions;
	
	public static final String INDUSTRY_CONFIG_FILE = "data/config/exerelin/industryClassDefs.csv";
	//public static final Map<MarketArchetype, Float> STATION_ARCHETYPE_QUOTAS = new HashMap<>(MarketArchetype.values().length);
	// this proportion of TT markets with no military bases will have Cabal submarkets (Underworld)
	public static final float CABAL_MARKET_MULT = 0.4f;	
	// this is the chance a market with a military base will still be a candidate for Cabal markets
	public static final float CABAL_MILITARY_MARKET_CHANCE = 0.5f;
	public static final float LUDDIC_MAJORITY_CHANCE = 0.05f;	// how many markets have Luddic majority even if they aren't Luddic at start
	public static final float LUDDIC_MINORITY_CHANCE = 0.15f;	// how many markets that start under Church control are non-Luddic
	public static final float MILITARY_BASE_CHANCE = 0.5f;	// if meets size requirements
	public static final float MILITARY_BASE_CHANCE_PIRATE = 0.5f;
	
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MIN = 1.3f;
	//protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MAX = 0.5f;	// lower than min so it can swap autofacs for shipbreakers if needed
	
	protected static final int[] PLANET_SIZE_ROTATION = new int[] {4, 5, 6, 7, 6, 5, 4};
	protected static final int[] MOON_SIZE_ROTATION = new int[] {3, 4, 5, 6, 5, 4};
	protected static final int[] STATION_SIZE_ROTATION = new int[] {3, 4, 5, 4, 3};
	
	protected static final List<IndustryClassDef> industryDefs = new ArrayList<>();
	protected static final Map<String, IndustryClassDef> industryDefsByDefId = new HashMap<>();
	protected static final Map<String, IndustryClassDef> industryDefsByIndustryId = new HashMap<>();
	protected static final List<IndustryClassDef> specialIndustryClassDefs = new ArrayList<>();
	
	// not a literally accurate count since it disregards HQs
	// only used to handle the market size rotation
	protected int numStations = 0;
	protected int numPlanets = 0;
	protected int numMoons = 0;
	
	protected List<ProcGenEntity> markets = new ArrayList<>();
	protected Map<String, List<ProcGenEntity>> marketsByFactionId = new HashMap<>();
	
	protected final ExerelinProcGen procGen;
	protected final Random random;
	
	static {
		loadIndustries();
	}
	
	protected static void loadIndustries()
	{
		try {			
			JSONArray configJson = Global.getSettings().getMergedSpreadsheetDataForMod("id", INDUSTRY_CONFIG_FILE, ExerelinConstants.MOD_ID);
			for(int i=0; i<configJson.length(); i++)
			{
				JSONObject row = configJson.getJSONObject(i);
				String id = row.getString("id");
				String name = row.getString("name");
				String classDef = row.getString("class");
				boolean special = row.optBoolean("special", false);
				String requiredMod = row.optString("requiredMod", "");
				
				if (!requiredMod.isEmpty() && !Global.getSettings().getModManager().isModEnabled(requiredMod))
					continue;
				
				IndustryClassDef def = new IndustryClassDef(id, name, classDef, special);
				
				industryDefs.add(def);
				industryDefsByDefId.put(id, def);
				if (special) specialIndustryClassDefs.add(def);
				Set<String> industryIds = def.generator.getIndustryIds();
				for (String industryId : industryIds)
				{
					industryDefsByIndustryId.put(industryId, def);
				}
			}
			
		} catch (IOException | JSONException ex) {	// fail-deadly to make sure errors don't go unnoticed
			log.error(ex);
			throw new IllegalStateException("Error loading industries for procgen: " + ex);
		}	
	}
	
	public ExerelinMarketBuilder(ExerelinProcGen procGen)
	{
		this.procGen = procGen;
		random = procGen.getRandom();
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
				if ((market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND)) && random.nextFloat() > CABAL_MILITARY_MARKET_CHANCE) 
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
		
	protected int getWantedMarketSize(ProcGenEntity data, String factionId)
	{
		if (data.forceMarketSize != -1) return data.forceMarketSize;
		
		boolean isStation = data.type == EntityType.STATION; 
		boolean isMoon = data.type == EntityType.MOON;
		int size = 1;
		if (data.isHQ) {
			size = 7;
		}
		else {
			if (isStation) size = getSizeFromRotation(STATION_SIZE_ROTATION, numStations);
			else if (isMoon) size = getSizeFromRotation(MOON_SIZE_ROTATION, numMoons);
			else size = getSizeFromRotation(PLANET_SIZE_ROTATION, numPlanets);
		}
		
		if (ExerelinUtilsFaction.isPirateFaction(factionId)) {
			size--;
			if (size < 3) size = 3;
		}
		
		return size;
	}
	
	protected int getMaxProductiveIndustries(ProcGenEntity ent)
	{
		if (ent.isHQ) return 5;
		int size = ent.market.getSize();
		if (size <= 4) return 1;
		if (size <= 6) return 2;
		if (size <= 8) return 3;
		return 4;
	}
	
	protected float getSpecialIndustryChance(ProcGenEntity ent)
	{
		return ent.market.getSize() * 0.08f;
	}
	
	protected void addSpaceportOrMegaport(MarketAPI market, EntityType type, int marketSize)
	{
		int size = marketSize;
		if (type == EntityType.STATION) size +=1;
		if (random.nextBoolean()) size += 1;
		
		if (size > 6) market.addIndustry(Industries.MEGAPORT);
		else market.addIndustry(Industries.SPACEPORT);
	}
	
	/**
	 * Adds patrol/military bases, ground defenses and defense stations as appropriate to the market.
	 * @param entity
	 * @param marketSize
	 */
	protected void addMilitaryStructures(ProcGenEntity entity, int marketSize)
	{
		MarketAPI market = entity.market;
		
		boolean isPirate = ExerelinUtilsFaction.isPirateFaction(market.getFactionId());
		boolean isMoon = entity.type == EntityType.MOON;
		boolean isStation = entity.type == EntityType.STATION;
		
		int sizeForBase = 6;
		if (isMoon) sizeForBase = 5;
		else if (isStation) sizeForBase = 5;
		if (isPirate) sizeForBase -= 1;
		
		// add military base if needed
		boolean haveBase = market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND);
		if (!haveBase && marketSize >= sizeForBase)
		{
			float roll = (random.nextFloat() + random.nextFloat())*0.5f;
			float req = MILITARY_BASE_CHANCE;
			if (isPirate) req = MILITARY_BASE_CHANCE_PIRATE;
			if (roll > req)
			{
				market.addIndustry(Industries.MILITARYBASE);
				haveBase = true;
			}
				
		}
		
		int sizeForPatrol = 5;
		if (isMoon || isStation) sizeForPatrol = 4;
		
		// add patrol HQ if needed
		if (!haveBase && marketSize >= sizeForPatrol)
		{
			float roll = (random.nextFloat() + random.nextFloat())*0.5f;
			float req = MILITARY_BASE_CHANCE;
			if (isPirate) req = MILITARY_BASE_CHANCE_PIRATE;
			if (roll > req)
				market.addIndustry(Industries.PATROLHQ);
		}
		
		// add ground defenses
		int sizeForHeavyGun = 7, sizeForGun = 4;
		if (isMoon || isStation)
		{
			sizeForHeavyGun -=1;
			sizeForGun -=1;
		}
		if (haveBase)
		{
			sizeForHeavyGun -=1;
			sizeForGun -=1;
		}
		if (marketSize > sizeForHeavyGun)
			market.addIndustry(Industries.HEAVYBATTERIES);
		else if (marketSize > sizeForGun)
			market.addIndustry(Industries.GROUNDDEFENSES);
		
		// add stations
		// TODO: investigate if this breaks if we add the station industry before the custom station entity
		if (!entity.isHQ)	// already added for HQs
		{
			int size1 = 4, size2 = 6, size3 = 8;
			if (isStation)
			{
				size1 -= 2; 
				size2 -= 2; 
				size3 -= 2;
			}
			else if (isMoon)
			{
				size1 -= 1; 
				size2 -= 1; 
				size3 -= 1;
			}
			if (haveBase)
			{
				size1 -= 1; 
				size2 -= 1; 
				size3 -= 1;
			}
			
			int sizeIndex = -1;
			if (marketSize > size3)
				sizeIndex = 2;
			else if (marketSize > size2)
				sizeIndex = 1;
			else if (marketSize > size1)
				sizeIndex = 0;
			
			if (sizeIndex > 0)
				market.addIndustry(ExerelinConfig.getExerelinFactionConfig(market.getFactionId())
						.getRandomDefenceStation(random, sizeIndex));
		}
	}
	
	// =========================================================================
	// main market adding methods
	
	protected MarketAPI initMarket(ProcGenEntity data, String factionId)
	{
		return initMarket(data, factionId, getWantedMarketSize(data, factionId));
	}
	
	protected MarketAPI initMarket(ProcGenEntity data, String factionId, int marketSize)
	{
		log.info("Creating market for " + data.name + " (" + data.type + "), faction " + factionId);
		
		SectorEntityToken entity = data.entity;
		// don't make the markets too big; they'll screw up the economy big time
		
		String planetType = data.planetType;
		boolean isStation = data.type == EntityType.STATION; 
		boolean isMoon = data.type == EntityType.MOON;
		
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
		
		market.addCondition("population_" + marketSize);
		if (market.hasCondition(Conditions.DECIVILIZED))
		{
			market.removeCondition(Conditions.DECIVILIZED);
			market.addCondition(Conditions.DECIVILIZED_SUBPOP);
		}
		
		// add basic industries
		market.addIndustry(Industries.POPULATION);
		addSpaceportOrMegaport(market, data.type, marketSize);
		
		if (data.isHQ)
		{
			market.addIndustry(Industries.HIGHCOMMAND);
			market.addIndustry(Industries.WAYSTATION);
			ExerelinConfig.getExerelinFactionConfig(factionId).getRandomDefenceStation(random, 2);
			if (data == procGen.getHomeworld()) 
			{
				//market.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
				//newMarket.addCondition(Conditions.SHIPBREAKING_CENTER);
				//market.addCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION);
			}
			
			if (factionId.equals(Factions.DIKTAT))
				market.addIndustry("lionsguard");
		}
		
		addMilitaryStructures(data, marketSize);
		
		// planet/terrain type stuff
		if (!isStation)
		{
			if (planetType.equals("terran-eccentric") && !isMoon)
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
		else {
			SectorEntityToken token = data.primary;
			if (token instanceof PlanetAPI)
			{
				PlanetAPI planet = (PlanetAPI) token;
				if (planet.isGasGiant())
				{
					MarketAPI planetMarket = planet.getMarket();
					if (planetMarket.hasCondition(Conditions.VOLATILES_TRACE))
						market.addCondition(Conditions.VOLATILES_TRACE);
					if (planetMarket.hasCondition(Conditions.VOLATILES_DIFFUSE))
						market.addCondition(Conditions.VOLATILES_ABUNDANT);
					if (planetMarket.hasCondition(Conditions.VOLATILES_ABUNDANT))
						market.addCondition(Conditions.VOLATILES_ABUNDANT);
					if (planetMarket.hasCondition(Conditions.VOLATILES_PLENTIFUL))
						market.addCondition(Conditions.VOLATILES_PLENTIFUL);
				}
			}
			
			if (data.terrain.getType().equals(Terrain.ASTEROID_BELT))
				market.addCondition(Conditions.ORE_SPARSE);
			if (data.terrain.getType().equals(Terrain.ASTEROID_FIELD))
				market.addCondition(Conditions.ORE_MODERATE);
		}
				
		// free port status, tariffs
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (config.freeMarket)
		{
			market.addCondition(Conditions.FREE_PORT);
		}
		
		market.getTariff().modifyFlat("generator", Global.getSector().getFaction(factionId).getTariffFraction());
		ExerelinUtilsMarket.setTariffs(market);
					
		// submarkets
		SectorManager.updateSubmarkets(market, Factions.NEUTRAL, factionId);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		Global.getSector().getEconomy().addMarket(market, true);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
				
		procGen.pickEntityInteractionImage(data.entity, market, planetType, data.type);
		
		if (!data.isHQ)
		{
			if (isStation) numStations++;
			else if (isMoon) numMoons++;
			else numPlanets++;
		}
		
		markets.add(data);
		if (!marketsByFactionId.containsKey(factionId))
			marketsByFactionId.put(factionId, new ArrayList<ProcGenEntity>());
		marketsByFactionId.get(factionId).add(data);
		
		return market;
	}
	
	/**
	 * Adds industries specified in the faction config to the most suitable markets of that faction.
	 * @param factionId
	 */
	public void addKeyIndustriesForFaction(String factionId)
	{
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		List<ProcGenEntity> entities = marketsByFactionId.get(factionId);
		
		// for each seed, add N industries to factions' markets
		for (IndustrySeed seed : conf.industrySeeds)
		{
			float countRaw = seed.mult * entities.size();
			int count = (int)(seed.roundUp ? Math.ceil(countRaw) : Math.floor(countRaw));
			
			if (count == 0) continue;
			
			IndustryClassDef def = industryDefsByIndustryId.get(seed.industryId);			
			
			// order entities by reverse priority, highes priority markets get the industries
			List<Pair<ProcGenEntity, Float>> ordered = new ArrayList<>();	// float is priority value
			for (ProcGenEntity entity : entities)
			{
				// this industry isn't usable on this market
				if (!def.generator.canApply(factionId, entity))
					continue;
				ordered.add(new Pair<>(entity, def.generator.getPriority(entity)));
			}
			
			Collections.sort(ordered, new Comparator<Pair<ProcGenEntity, Float>>() {
				public int compare(Pair<ProcGenEntity, Float> p1, Pair<ProcGenEntity, Float> p2) {
					return p2.two.compareTo(p1.two);
				}
			});
			
			for (int i = 0; i < count; i++)
			{
				if (ordered.isEmpty()) break;
				Pair<ProcGenEntity, Float> highest = ordered.remove(ordered.size() - 1);
				log.info("Adding key industry " + def.name + " to market " + highest.one.name);
				highest.one.market.addIndustry(seed.industryId);
				highest.one.numProductiveIndustries += 1;
			}
		}
	}
	
	/**
	 * Fills the market with productive industries, up to the permitted number depending on size.
	 * Added industries depend on local market conditions.
	 * @param entity
	 */
	public void addIndustriesToMarket(ProcGenEntity entity)
	{
		int max = getMaxProductiveIndustries(entity);
		if (entity.numProductiveIndustries >= max)
			return;
		
		String factionId = entity.market.getFactionId();
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		List<Pair<IndustryClassDef, Float>> industries = new ArrayList<>();
		
		// order compatible indsutries by priority
		for (IndustryClassDef def : industryDefs)
		{
			if (def.special) continue;
			if (!def.generator.canApply(factionId, entity))
				continue;
			
			float priority = def.generator.getPriority(entity);
			priority *= conf.getIndustryTypeMult(def.id);
			if (priority <= 0) continue;
			industries.add(new Pair<>(def, priority));
		}
		
		Collections.sort(industries, new Comparator<Pair<IndustryClassDef, Float>>() {
			public int compare(Pair<IndustryClassDef, Float> p1, Pair<IndustryClassDef, Float> p2) {
				return p2.two.compareTo(p1.two);
			}
		});
		
		// add as many industries as we're allowed to
		while (entity.numProductiveIndustries < max)
		{
			if (industries.isEmpty()) break;
			if (entity.market.getIndustries().size() >= 16)
				break;
			
			IndustryClassDef def = industries.remove(industries.size() - 1).one;
			log.info("Adding industry " + def.name + " to market " + entity.name);
			def.generator.apply(entity);
		}
		
		// add special industries
		if (entity.market.getIndustries().size() >= 16) return;
		float specialChance = getSpecialIndustryChance(entity);
		if (random.nextFloat() > specialChance) return;
		
		WeightedRandomPicker<IndustryClassDef> specialPicker = new WeightedRandomPicker<>();
		for (IndustryClassDef def : specialIndustryClassDefs)
		{
			if (!def.generator.canApply(factionId, entity))
				continue;
			
			float weight = def.generator.getSpecialWeight(entity);
			if (weight <= 0) continue;
			industries.add(new Pair<>(def, weight));
		}
		IndustryClassDef picked = specialPicker.pick();
		if (picked != null)
			picked.generator.apply(entity);
	}
	
	public void addIndustriesToMarkets()
	{
		for (ProcGenEntity ent : markets)
			addIndustriesToMarket(ent);
	}
	
	// TODO
	public void addFactionSpecials(String factionId)
	{
		
	}
		
	// =========================================================================
	// static classes
	public static class IndustryClassDef
	{
		public final String id;
		public final String name;
		public IndustryClassGen generator;
		public final boolean special;

		public IndustryClassDef(String id, String name, String generatorClass, boolean special)
		{
			this.id = id;
			this.name = name;
			this.special = special;
			
			try {
				Class<?> clazz = Class.forName(generatorClass);
				Constructor<?> ctor = clazz.getConstructor(String.class);
				generator = (IndustryClassGen)ctor.newInstance();
			} catch (Exception ex) {
				log.error("Failed to set generator for industry def " + name + ": " + ex);
			}
		}
	}
}