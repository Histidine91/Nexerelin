package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.ColonyManager.QueuedIndustry.QueueType;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import exerelin.utilities.NexFactionConfig.BonusSeed;
import exerelin.utilities.NexFactionConfig.DefenceStationSet;
import exerelin.utilities.NexFactionConfig.IndustrySeed;
import exerelin.world.ExerelinProcGen.EntityType;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.industry.IndustryClassGen;
import exerelin.world.industry.bonus.BonusGen;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * How it works.
 * Stage 1: init markets (add to economy, set size, add population industry), etc.
 *	Add defenses & starport based on size
 * Stage 2: for each market, for all possible industries, get priority, add to market in order of priority till max industries reached
 *	Priority is based on local resources and hazard rating
 * Stage 3: add key manufacturing from config to randomly picked planet
 * Stage 4: distribute bonus items to markets
 * 
 * Notes:
 *	Free stations in asteroid belt should have ore mining,
 *	free stations in ring or orbiting gas giant should have gas extraction
*/

@SuppressWarnings("unchecked")
public class NexMarketBuilder
{
	public static Logger log = Global.getLogger(NexMarketBuilder.class);
	//private List possibleMarketConditions;
	
	public static final String INDUSTRY_CONFIG_FILE = "data/config/exerelin/industryClassDefs.csv";
	public static final String BONUS_CONFIG_FILE = "data/config/exerelin/industry_bonuses.csv";
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
	
	protected static final int[] PLANET_SIZE_ROTATION = new int[] {4, 5, 6, 7, 6, 5};
	protected static final int[] MOON_SIZE_ROTATION = new int[] {3, 4, 5, 6, 5, 4};
	protected static final int[] STATION_SIZE_ROTATION = new int[] {3, 4, 5, 4, 3};
	
	protected static final List<IndustryClassGen> industryClasses = new ArrayList<>();
	protected static final List<IndustryClassGen> industryClassesOrdered = new ArrayList<>();	// sorted by priority
	protected static final Map<String, IndustryClassGen> industryClassesById = new HashMap<>();
	@Getter protected static final Map<String, IndustryClassGen> industryClassesByIndustryId = new HashMap<>();
	protected static final List<IndustryClassGen> specialIndustryClasses = new ArrayList<>();
	protected static final List<BonusGen> bonuses = new ArrayList<>();
	protected static final Map<String, BonusGen> bonusesById = new HashMap<>();
	
	// not a literally accurate count since it disregards HQs
	// only used to handle the market size rotation
	protected int numStations = 0;
	protected int numPlanets = 0;
	protected int numMoons = 0;
	
	protected List<ProcGenEntity> markets = new ArrayList<>();
	protected Map<String, List<ProcGenEntity>> marketsByFactionId = new HashMap<>();
	
	protected Map<String, Integer> currIndustryCounts = new HashMap<>();
	
	protected final ExerelinProcGen procGen;
	@Getter	protected final Random random;
	
	static {
		loadIndustries();
		loadBonuses();
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
				float priority = (float)row.optDouble("priority", 0);
				boolean special = row.optBoolean("special", false);
				String requiredMod = row.optString("requiredMod", "");
				
				if (!requiredMod.isEmpty() && !Global.getSettings().getModManager().isModEnabled(requiredMod))
					continue;
				
				IndustryClassGen gen = IndustryClassGen.loadIndustryClassGen(id, name, priority, classDef, special);
				
				industryClasses.add(gen);
				industryClassesById.put(id, gen);
				if (special) {
					log.info("Added special industry class: " + gen.getId());
					specialIndustryClasses.add(gen);
				}
				Set<String> industryIds = gen.getIndustryIds();
				for (String industryId : industryIds)
				{
					industryClassesByIndustryId.put(industryId, gen);
					//log.info("Adding industry ID key " + industryId + " for industry class def " + def.name);
				}
			}
			industryClassesOrdered.addAll(industryClasses);
			Collections.sort(industryClassesOrdered);
			
		} catch (IOException | JSONException ex) {	// fail-deadly to make sure errors don't go unnoticed
			log.error(ex);
			throw new IllegalStateException("Error loading industries for procgen: " + ex);
		}	
	}
	
	protected static void loadBonuses()
	{
		try {			
			JSONArray configJson = Global.getSettings().getMergedSpreadsheetDataForMod("id", BONUS_CONFIG_FILE, ExerelinConstants.MOD_ID);
			for(int i=0; i<configJson.length(); i++)
			{
				JSONObject row = configJson.getJSONObject(i);
				String id = row.getString("id");
				String name = row.getString("name");
				String classDef = row.getString("class");
				String requiredMod = row.optString("requiredMod", "");
				
				if (!requiredMod.isEmpty() && !Global.getSettings().getModManager().isModEnabled(requiredMod))
					continue;
				
				BonusGen bonus = BonusGen.loadBonusGen(id, name, classDef);
				bonuses.add(bonus);
				bonusesById.put(id, bonus);
			}
			
		} catch (IOException | JSONException ex) {	// fail-deadly to make sure errors don't go unnoticed
			log.error(ex);
			throw new IllegalStateException("Error loading bonus items for procgen: " + ex);
		}	
	}
	
	public NexMarketBuilder(ExerelinProcGen procGen)
	{
		this.procGen = procGen;
		random = procGen.getRandom();
		Global.getSector().getMemoryWithoutUpdate().set("$nex_marketBuilder", this, 0);
	}
	
	public static NexMarketBuilder getInstance() {
		return (NexMarketBuilder)Global.getSector().getMemoryWithoutUpdate().get("$nex_marketBuilder");
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
			Boolean enabled = false;
			try {
				String execute = "return data.scripts.UnderworldModPlugin.isStarlightCabalEnabled();";
				enabled = (Boolean)NexUtils.runCode(execute, null, Boolean.class);
			} catch (Exception ex) {
				log.error("Failed to read Starlight Cabal enabled state", ex);
			}
			log.info("Cabal is enabled?: " + enabled);

			if (!Boolean.TRUE.equals(enabled)) return;
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
			
			Collections.sort(cabalCandidates, NexUtilsMarket.marketSizeComparator);
			
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
			if (factionId.equals(Factions.PLAYER)) size = 5;
			else size = 7;
		}
		else {
			if (isStation) size = getSizeFromRotation(STATION_SIZE_ROTATION, numStations);
			else if (isMoon) size = getSizeFromRotation(MOON_SIZE_ROTATION, numMoons);
			else size = getSizeFromRotation(PLANET_SIZE_ROTATION, numPlanets);
		}
		
		if (NexUtilsFaction.isPirateFaction(factionId)) {
			size--;
			if (size < 3) size = 3;
		}
		
		return size;
	}

	/**
	 * Limiter for how many industries a market should have. 
	 * Complements but does not replace the base game's max industry system.
	 * One significant difference is that the market builder implementation
	 * also includes a "null industry" being counted as a productive industry,
	 * thereby lowering the number of industries on the market.
	 * @param ent
	 * @return
	 */
	public static int getMaxProductiveIndustries(ProcGenEntity ent)
	{
		// disallow going over industry limit when taking both Heavy Industry and Military Base upgrades
		// nah, since there's now a stability penalty for over-industry, we can let the player worry about it
		if (ent.isHQ && ent.entity.getFaction().isPlayerFaction()) return 2;
		
		int max = 4;
		int size = ent.market.getSize();
		if (size <= 4) max = 1;
		if (size <= 6) max = 2;
		if (size <= 8) max = 3;
		if (ent.isHQ) max += 1;
		
		return max;
	}
	
	protected float getSpecialIndustryChance(ProcGenEntity ent)
	{
		return ent.market.getSize() * 0.08f;
	}
	
	public static boolean haveStation(MarketAPI market) {
		for (Industry ind : market.getIndustries()) {
			if (ind.getSpec().hasTag(Industries.TAG_STATION))
				return true;
		}
		return false;
	}
	
	/**
	 * Gets the number of industries with the specified generator ID present in the sector or generated thus far.
	 * @param id The {@code IndustryClassGen} ID of the industries to count.
	 * @return
	 */
	public static int countIndustries(String id) {
		NexMarketBuilder instance = getInstance();
		if (instance != null) {
			Integer count = instance.currIndustryCounts.get(id);
			if (count == null) count = 0;
			return count;
		}
		else {
			int count = 0;
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
			{
				for (Industry ind : market.getIndustries()) {
					IndustryClassGen gen = industryClassesByIndustryId.get(ind.getSpec().getId());
					if (gen != null && gen.getId().equals(id))
						count++;
				}
			}
			return count;
		}
	}
	
	protected void incrementIndustryCount(String id) {
		int count = countIndustries(id);
		currIndustryCounts.put(id, count + 1);
	}
	
	public static void addIndustry(MarketAPI market, String id, String idForIncrement, boolean instant) {
		if (instant) {
			market.addIndustry(id);
		}
		else {
			ColonyManager.getManager().queueIndustry(market, id, QueueType.NEW);
		}
		if (getInstance() != null && idForIncrement != null) {
			getInstance().incrementIndustryCount(idForIncrement);
		}
	}
	
	public static void addIndustry(MarketAPI market, String id, boolean instant) {
		addIndustry(market, id, null, instant);
	}
		
	public static void addSpaceportOrMegaport(MarketAPI market, EntityType type, boolean instant, Random random)
	{
		int size = market.getSize();
		if (type == EntityType.STATION) size +=1;
		if (random.nextBoolean()) size += 1;
		
		if (instant) {
			if (size > 6) market.addIndustry(Industries.MEGAPORT);
			else market.addIndustry(Industries.SPACEPORT);
		} else {
			ColonyManager.getManager().queueIndustry(market, Industries.SPACEPORT, QueueType.NEW);
			if (size > 6) ColonyManager.getManager().queueIndustry(market, Industries.SPACEPORT, QueueType.UPGRADE);
		}
	}
	
	/**
	 * Gets the market's current orbital station and its level.
	 * @param market
	 * @return {@code Pair} of the station's {@code Industry} and its level (1-3).
	 */
	public static Pair<Industry, Integer> getCurrentStationAndTier(MarketAPI market) {
		Industry best = null;
		int bestLevel = 0;
		for (Industry ind : market.getIndustries()) {
			if (!ind.getSpec().hasTag(Industries.TAG_STATION))
				continue;
			
			int level = 1;
			if (ind.getSpec().hasTag(Industries.TAG_STARFORTRESS))
				level = 3;
			else if (ind.getSpec().hasTag(Industries.TAG_BATTLESTATION))
				level = 2;
			
			if (level > bestLevel) {
				best = ind;
				bestLevel = level;
			}
		}
		if (best == null) return null;
		return new Pair<>(best, bestLevel);
	}

	public static void addOrQueueHeavyBatteries(MarketAPI market, ColonyManager colMan, boolean instant) {
		if (instant) {
			Industry groundDef = market.getIndustry(Industries.GROUNDDEFENSES);
			if (groundDef != null) {
				NexUtilsMarket.upgradeIndustryIfCan(groundDef, instant);
				return;
			}
			addIndustry(market, Industries.HEAVYBATTERIES, instant);
		}
		else {
			colMan.queueIndustry(market, Industries.GROUNDDEFENSES, QueueType.NEW);
			colMan.queueIndustry(market, Industries.GROUNDDEFENSES, QueueType.UPGRADE);
		}
	}

	/**
	 * Adds a station of the specified size to market.
	 * @param market
	 * @param wantedSizeIndex Must not be outside the station sizes actually defined for the faction.
	 *                         Exception: negative number means add 1 to current station level (if possible), or add a level 1 station if none exists.
	 * @param instant
	 * @param colMan
	 * @param random
	 */
	public static void addOrUpgradeStation(MarketAPI market, int wantedSizeIndex, boolean instant, ColonyManager colMan, @Nullable Random random)
	{
		if (random == null) random = new Random();

		// look for an existing station and see if we should upgrade it
		Pair<Industry, Integer> currStation = getCurrentStationAndTier(market);
		if (wantedSizeIndex < 0) {
			if (currStation != null) wantedSizeIndex = currStation.two;
			else wantedSizeIndex = 0;
		}

		if (currStation != null) {
			//log.info("Size index " + sizeIndex + ", curent size " + currStation.two);
			boolean wantUpgrade = wantedSizeIndex + 1 > currStation.two;
			if (wantUpgrade)
			{
				Industry station = currStation.one;
				NexUtilsMarket.upgradeIndustryIfCan(station, instant);
			}
		}
		// no station, add one
		else {
			NexFactionConfig conf = NexConfig.getFactionConfig(market.getFactionId());
			if (instant) {
				String station = conf.getRandomDefenceStation(random, wantedSizeIndex);
				if (station != null) {
					//log.info("Adding station: " + station);
					addIndustry(market, station, instant);
				}
			}
			else {
				DefenceStationSet set = conf.getRandomDefenceStationSet(random);
				if (set != null) {
					colMan.queueIndustry(market, set.industryIds.get(0), QueueType.NEW);
					for (int i=0; i<wantedSizeIndex;i++) {
						colMan.queueIndustry(market, set.industryIds.get(i), QueueType.UPGRADE);
					}
				}
			}
		}
	}
	
	/**
	 * Adds patrol/military bases, ground defenses and defense stations as appropriate to the market.
	 * @param entity
	 * @param instant
	 * @param random
	 */
	public static void addMilitaryStructures(ProcGenEntity entity, boolean instant, Random random)
	{
		ColonyManager colMan = ColonyManager.getManager();
		if (colMan == null && !instant) {
			// queue system broken, we can do nothing
			log.error("Attempt to queue structure construction without a colony manager");
			return;
		}
		
		MarketAPI market = entity.market;
		int marketSize = market.getSize();
		
		boolean isPirate = NexUtilsFaction.isPirateFaction(market.getFactionId());
		boolean isMoon = entity.type == EntityType.MOON;
		boolean isStation = entity.type == EntityType.STATION;
		boolean newNPCColony = market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY);
		
		int sizeForBase = 6;
		if (isMoon) sizeForBase = 5;
		else if (isStation) sizeForBase = 5;
		if (isPirate) sizeForBase -= 1;
		
		// add military base if needed
		boolean haveBase = market.hasIndustry(Industries.MILITARYBASE) 
				|| market.hasIndustry(Industries.HIGHCOMMAND)
				|| market.hasIndustry("tiandong_merchq");
				//|| market.hasIndustry("prv_rb_pirate_2")
				//|| market.hasIndustry("prv_rb_pirate_3");
		boolean havePatrol = market.hasIndustry(Industries.PATROLHQ);
		//|| market.hasIndustry("prv_rb_pirate_1");

		boolean wantBase = !haveBase && marketSize >= sizeForBase && Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
		if (entity.isHQ && market.getFaction().isPlayerFaction()) wantBase = false;	// don't add base for player homeworld at start
		
		if (wantBase)
		{
			float roll = (random.nextFloat() + random.nextFloat())*0.5f;
			float req = MILITARY_BASE_CHANCE;
			if (isPirate) req = MILITARY_BASE_CHANCE_PIRATE;
			if (roll > req)
			{
				if (instant) {
					if (havePatrol) NexUtilsMarket.upgradeIndustryIfCan(market.getIndustry(Industries.PATROLHQ), true);
					else addIndustry(market, Industries.MILITARYBASE, true);
				}
				else {
					colMan.queueIndustry(market, Industries.PATROLHQ, QueueType.NEW);
					colMan.queueIndustry(market, Industries.PATROLHQ, QueueType.UPGRADE);
				}
				haveBase = true;
			}
		}
		
		int sizeForPatrol = 5;
		if (newNPCColony)
			sizeForPatrol = 3;
		else if (isMoon || isStation) 
			sizeForPatrol = 4;
		
		// add patrol HQ if needed
		
		if (!haveBase && !havePatrol && marketSize >= sizeForPatrol)
		{
			float roll = (random.nextFloat() + random.nextFloat())*0.5f;
			float req = MILITARY_BASE_CHANCE;
			if (newNPCColony) req = -1;
			else if (isPirate) req = MILITARY_BASE_CHANCE_PIRATE;
			// new game seeding: if we have the size for base but didn't get one, enforce patrol HQ at least
			else if (Global.getSector().isInNewGameAdvance() && marketSize >= sizeForBase) req -=1;
			if (roll > req) {
				addIndustry(market, Industries.PATROLHQ, instant);
			}
		}
		
		// add ground defenses
		if (!market.hasIndustry(Industries.HEAVYBATTERIES))
		{
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
			if (marketSize > sizeForHeavyGun) {
				addOrQueueHeavyBatteries(market, colMan, instant);
			}
			else if (marketSize > sizeForGun)
				addIndustry(market, Industries.GROUNDDEFENSES, instant);
		}
		
		// add stations
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
			//log.info(String.format("Size %s, needed: %s, %s, %s", marketSize, size1, size2, size3));
			
			int sizeIndex = -1;
			if (marketSize > size3)
				sizeIndex = 2;
			else if (marketSize > size2)
				sizeIndex = 1;
			else if (marketSize > size1)
				sizeIndex = 0;
			
			if (sizeIndex >= 0)
			{
				addOrUpgradeStation(market, sizeIndex, instant, colMan, random);
			}
		}
		
		if (haveBase)
			entity.numProductiveIndustries += 1;
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
		market.getMemoryWithoutUpdate().set("$nex_randomMarket", true);
		market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, factionId);
		
		market.addCondition("population_" + marketSize);
		if (market.hasCondition(Conditions.DECIVILIZED))
		{
			market.removeCondition(Conditions.DECIVILIZED);
			market.addCondition(Conditions.DECIVILIZED_SUBPOP);
		}
		
		// add basic industries
		market.addIndustry(Industries.POPULATION);
		addSpaceportOrMegaport(market, data.type, true, random);
		
		if (data.isHQ)
		{
			int stationTier = 4;
			if (factionId.equals(Factions.PLAYER)) {
				market.addIndustry(Industries.PATROLHQ);
				stationTier = 1;
			}
			else if (NexUtilsFaction.isPirateFaction(factionId)) {
				market.addIndustry(Industries.MILITARYBASE);
				stationTier = 2;
			}
			else {
				market.addIndustry(Industries.HIGHCOMMAND);
				List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
				if (!traits.contains(DiplomacyTraits.TraitIds.DISLIKES_AI) && !traits.contains(DiplomacyTraits.TraitIds.HATES_AI))
				{
					market.getIndustry(Industries.HIGHCOMMAND).setAICoreId(Commodities.GAMMA_CORE);
				}
			}
			String stationId = NexConfig.getFactionConfig(factionId).getRandomDefenceStation(random, stationTier);
			if (stationId != null)
				market.addIndustry(stationId);
			
			if (data == procGen.getHomeworld()) 
			{
				market.addIndustry(Industries.WAYSTATION);
			}

			if (factionId.equals(Factions.LUDDIC_CHURCH)) {
				market.addTag(Tags.LUDDIC_SHRINE);
			}
			else if (factionId.equals(Factions.DIKTAT))
				market.addIndustry("lionsguard");
			else if (factionId.equals("dassault_mikoyan"))
				market.addIndustry("6emebureau");
			else if (factionId.equals("interstellarimperium")) {
				//market.addIndustry("ii_interstellarbazaar");	// put somewhere else
				market.addIndustry("ii_imperialguard");
			}
			//else if (factionId.equals("shadow_industry"))	// now done in industry seeds
			//	market.addIndustry("ms_redwingsCommand");
			else if (factionId.equals("blackrock_driveyards"))
				market.addIndustry("brdy_defhq");
			else if (factionId.equals("scalartech"))
				market.addSubmarket("tahlan_stdfmarket");
			
			market.getMemoryWithoutUpdate().set("$nex_procgen_hq", true);
		}
		
		addMilitaryStructures(data, true, random);
		
		// planet/terrain type stuff
		if (!isStation)
		{
			if (planetType.equals("terran-eccentric") && !isMoon)
			{
				// add mirror/shade
				LocationAPI system = entity.getContainingLocation();
				SectorEntityToken mirror = system.addCustomEntity(entity.getId() + "_mirror", null, "stellar_mirror", factionId);
				mirror.setCircularOrbitPointingDown(entity, NexUtilsAstro.getCurrentOrbitAngle(entity.getOrbitFocus(), entity) + 180, 
						entity.getRadius() + 150, data.entity.getOrbit().getOrbitalPeriod());
				mirror.setCustomDescriptionId("stellar_mirror");
				SectorEntityToken shade = system.addCustomEntity(entity.getId() + "_shade", null, "stellar_shade", factionId);
				shade.setCircularOrbitPointingDown(entity, NexUtilsAstro.getCurrentOrbitAngle(entity.getOrbitFocus(), entity), 
						entity.getRadius() + 150, data.entity.getOrbit().getOrbitalPeriod());		
				shade.setCustomDescriptionId("stellar_shade");
				market.addCondition(Conditions.SOLAR_ARRAY);
				
				//((PlanetAPI)entity).getSpec().setRotation(0);	// planet don't spin
			}
		}
		else {
			SectorEntityToken token = data.primary;
			if (token instanceof PlanetAPI)
			{
				PlanetAPI planet = (PlanetAPI) token;
				if (planet.isGasGiant())
				{
					log.info(market.getName() + " orbits gas giant " + planet.getName() + ", checking for volatiles");
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
			
			if (data.terrain != null)
			{
				if (data.terrain.getType().equals(Terrain.ASTEROID_BELT))
				{
					log.info(market.getName() + " is in asteroid belt, adding ore condition");
					market.addCondition(Conditions.ORE_MODERATE);
				}
				else if (data.terrain.getType().equals(Terrain.ASTEROID_FIELD))
				{
					log.info(market.getName() + " is in asteroid field, adding ore condition");
					market.addCondition(Conditions.ORE_MODERATE);
				}
				else if (data.terrain.getType().equals(Terrain.RING))
				{
					log.info(market.getName() + " is in ring, adding volatiles condition");
					// would be trace, but that produces zero volatiles at size 3
					market.addCondition(Conditions.VOLATILES_DIFFUSE);
				}
			}
		}
		
		if (factionId.equals("interstellarimperium")) {
			market.addCondition("ii_imperialdoctrine");
		}
				
		// free port status, tariffs
		NexFactionConfig config = NexConfig.getFactionConfig(factionId);
		if (config.freeMarket)
		{
			market.setFreePort(true);
			//market.addCondition(Conditions.FREE_PORT);
		}
		
		market.getTariff().modifyFlat("generator", Global.getSector().getFaction(factionId).getTariffFraction());
		NexUtilsMarket.setTariffs(market);
					
		// submarkets
		SectorManager.updateSubmarkets(market, Factions.NEUTRAL, factionId);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		Global.getSector().getEconomy().addMarket(market, true);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		// only do this when game has finished loading, so it doesn't mess with our income/debts
		//if (factionId.equals(Factions.PLAYER))
		//	market.setPlayerOwned(true);
				
		procGen.pickEntityInteractionImage(data.entity, market, planetType, data.type);
		
		if (!data.isHQ)
		{
			if (isStation) numStations++;
			else if (isMoon) numMoons++;
			else numPlanets++;
		}
		
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
		for (MarketConditionAPI cond : market.getConditions())
		{
			cond.setSurveyed(true);
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
		if (!marketsByFactionId.containsKey(factionId))
			return;
		
		log.info("Adding key industries for faction " + factionId);
		
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		List<ProcGenEntity> entities = marketsByFactionId.get(factionId);
		
		// for each seed, add N industries to factions' markets
		for (IndustrySeed seed : conf.industrySeeds)
		{
			int count = seed.count;
			if (seed.mult > 0) {
				float fromMult = seed.mult * entities.size();
				count += (int)(seed.roundUp ? Math.ceil(fromMult) : Math.floor(fromMult));
			}
			
			if (count == 0) continue;
			
			IndustryClassGen gen = industryClassesByIndustryId.get(seed.industryId);
			if (gen == null) {
				//throw new RuntimeException("Failed to find industry class generator for " + seed.industryId 
				//		+ " added by faction " + factionId);
				log.info(String.format("Industry class generator for %s (added by faction %s) not found, skipping",
						seed.industryId, factionId));
				continue;
			}
			gen.setRandom(random);
			
			// order entities by reverse priority, highest priority markets get the industries
			List<Pair<ProcGenEntity, Float>> ordered = new ArrayList<>();	// float is priority value
			for (ProcGenEntity entity : entities)
			{
				if (entity.market.getIndustries().size() >= 12)
					continue;
				// commented out: can lead to no planets being eligible
				//if (entity.numProductiveIndustries >= getMaxProductiveIndustries(entity))
				//	continue;
				if (Misc.getNumIndustries(entity.market) >= Misc.getMaxIndustries(entity.market))
					continue;
				
				// already present?
				if (entity.market.hasIndustry(seed.industryId))
				{
					count -= 1;
					continue;
				}
				
				// this industry isn't usable on this market
				if (!gen.canApply(entity))
					continue;
				
				float weight = gen.getWeight(entity);
				//log.info("  Entity " + entity.name + " has weight " + weight);
				ordered.add(new Pair<>(entity, weight));
			}
			
			Collections.sort(ordered, new Comparator<Pair<ProcGenEntity, Float>>() {
				public int compare(Pair<ProcGenEntity, Float> p1, Pair<ProcGenEntity, Float> p2) {
					return p1.two.compareTo(p2.two);
				}
			});
			
			for (int i = 0; i < count; i++)
			{
				if (ordered.isEmpty()) break;
				Pair<ProcGenEntity, Float> highest = ordered.remove(ordered.size() - 1);
				log.info("  Adding key industry " + gen.getName() + " to market " + highest.one.name
						+ " (priority " + highest.two + ")");
				String industryId = seed.industryId;
				MarketAPI market = highest.one.market;
				addIndustry(market, industryId, gen.getId(), true);
				if (industryId.equals(Industries.ORBITALWORKS)) {
					market.removeIndustry(Industries.HEAVYINDUSTRY, null, false);
					highest.one.numProductiveIndustries -= 1;
				}
				else if (industryId.equals("ms_massIndustry") 
						|| industryId.equals("ms_militaryProduction")
						|| industryId.equals("ms_orbitalShipyard")) 
				{
					market.removeIndustry("ms_modularFac", null, false);
					highest.one.numProductiveIndustries -= 1;
				}
				
				highest.one.numProductiveIndustries += 1;
			}
		}
	}
	
	/**
	 * Removes {@code IndustryClassGen}s in reverse order from {@code from},
	 * and adds them to {@code picker} with the appropriate weight, 
	 * until {@code from} is empty or we've reached the next highest priority.
	 * @param ent
	 * @param picker
	 * @param from
	 */
	protected static void loadPicker(ProcGenEntity ent, WeightedRandomPicker<IndustryClassGen> picker, List<IndustryClassGen> from, Random random)
	{
		Float currPriority = null;
		while (true)
		{
			if (from.isEmpty()) break;
			int nextIndex = from.size() - 1;
			float priority = from.get(nextIndex).getPriority();
			if (currPriority == null)
				currPriority = priority;
			else if (priority < currPriority)
				break;
			
			IndustryClassGen next = from.remove(from.size() - 1);
			next.setRandom(random);
			float weight = next.getWeight(ent);
			if (weight <= 0) continue;
			log.info("\tAdding industry class to picker: " + next.getName() 
					+ ", priority " + priority + ", weight " + weight);
			picker.add(next, weight);
		}
	}
	
	public static void addIndustriesToMarket(ProcGenEntity entity, boolean instant, Random random) {
		addIndustriesToMarket(entity, instant, Integer.MIN_VALUE, Integer.MAX_VALUE, random);
	}
	
	/**
	 * Fills the market with productive industries, up to the permitted number depending on size.Added industries depend on local market conditions.
	 * @param entity
	 * @param instant
	 * @param minPriority Minimum priority level of the industries that will be eligible to add.
	 * @param maxPriority Maximum priority level of the industries that will be eligible to add.
	 * @param random
	 */
	public static void addIndustriesToMarket(ProcGenEntity entity, boolean instant, 
			float minPriority, float maxPriority, Random random)
	{
		log.info("Adding industries to market " + entity.name);
		int max = getMaxProductiveIndustries(entity);
		if (entity.numProductiveIndustries >= max)
			return;
				
		List<IndustryClassGen> availableIndustries = new ArrayList<>();
		for (IndustryClassGen gen : industryClassesOrdered)
		{
			//log.info("\tTesting industry for availability: " + gen.getName());
			if (gen.isSpecial()) {
				//log.info("\t- Special, cancel");
				continue;
			}
			if (!gen.canAutogen()) {
				//log.info("\t- Cannot autogen, cancel");
				continue;
			}
			if (!gen.canApply(entity)) {
				//log.info("\t- Cannot apply, cancel");
				continue;
			}
			if (gen.getPriority() < minPriority) {
				//log.info("\t- Priority too low, cancel");
				continue;
			}
			if (gen.getPriority() > maxPriority) {
				//log.info("\t- Priority too high, cancel");
				continue;
			}
			
			availableIndustries.add(gen);
		}
		
		WeightedRandomPicker<IndustryClassGen> picker = new WeightedRandomPicker<>(random);
		// add as many industries as we're allowed to
		int tries = 0;
		while (Misc.getNumIndustries(entity.market) < Misc.getMaxIndustries(entity.market)
				&& entity.numProductiveIndustries < max)
		{
			tries++;
			if (tries > 50) break;
			
			if (entity.market.getIndustries().size() >= 12)
				break;
			
			if (availableIndustries.isEmpty())
				break;
			
			if (picker.isEmpty())
				loadPicker(entity, picker, availableIndustries, random);
			
			IndustryClassGen gen = picker.pickAndRemove();
			if (gen == null)
			{
				log.info("Picker is empty, skipping");
				continue;
			}
			
			log.info("Adding industry " + gen.getName() + " to market " + entity.name);
			gen.apply(entity, instant);
		}
	}
	
	public void addSpecialIndustries(ProcGenEntity entity)
	{
		if (entity.market.getIndustries().size() >= 12) return;
		float specialChance = getSpecialIndustryChance(entity);
		if (random.nextFloat() > specialChance) return;
		
		WeightedRandomPicker<IndustryClassGen> specialPicker = new WeightedRandomPicker<>(random);
		for (IndustryClassGen gen : specialIndustryClasses)
		{
			if (!gen.canAutogen()) continue;
			if (!gen.canApply(entity)) continue;
			gen.setRandom(random);
			
			float weight = gen.getWeight(entity);
			if (weight <= 0) continue;
			specialPicker.add(gen, weight);
		}
		IndustryClassGen picked = specialPicker.pick();
		if (picked != null)
		{
			log.info("Adding special industry " + picked.getName() + " to market " + entity.name);
			picked.apply(entity, true);
		}
	}
	
	// Separate primary industries (farms and mines, which are priority 3 and 2 respectively) from the rest
	// this is because we always want a farm or mine to be built where available,
	// but we don't want other industries to be built and potentially use up the industry limit
	// before we've added heavy industry and fuel production to the best planets
	
	public void addPrimaryIndustriesToMarkets()
	{
		List<ProcGenEntity> marketsCopy = new ArrayList<>(markets);
		Collections.shuffle(marketsCopy, random);
		
		for (ProcGenEntity ent : marketsCopy)
		{
			addIndustriesToMarket(ent, true, 2, Integer.MAX_VALUE, random);
		}
	}
	
	public void addFurtherIndustriesToMarkets()
	{
		List<ProcGenEntity> marketsCopy = new ArrayList<>(markets);
		Collections.shuffle(marketsCopy, random);
		
		for (ProcGenEntity ent : marketsCopy)
		{
			addIndustriesToMarket(ent, true, Integer.MIN_VALUE, 2, random);
			addSpecialIndustries(ent);
		}
	}
	
	/**
	 * Add bonus items (AI cores, synchrotrons, etc.) to a faction's industries.
	 * @param factionId
	 */
	public void addFactionBonuses(String factionId)
	{
		if (!marketsByFactionId.containsKey(factionId))
			return;
		
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		List<Industry> industries = new ArrayList<>();
		for (ProcGenEntity ent : marketsByFactionId.get(factionId))
		{
			industries.addAll(ent.market.getIndustries());
		}
		
		for (BonusSeed bonusEntry : conf.bonusSeeds)
		{
			int count = bonusEntry.count;
			if (bonusEntry.mult > 0)
				count += Math.round(marketsByFactionId.get(factionId).size() * bonusEntry.mult);
			
			if (count <= 0)
				continue;
			
			BonusGen bonus = bonusesById.get(bonusEntry.id);
			if (bonus == null) {
				log.error(String.format("Bonus %s for faction %s does not exist", bonusEntry.id, factionId));
				continue;
			}
			bonus.setMarketBuilder(this);
			
			// order industries by reverse priority, highest priority markets get the bonuses
			List<Pair<Industry, Float>> ordered = new ArrayList<>();	// float is priority value
			for (Industry ind : industries)
			{
				ProcGenEntity ent = procGen.procGenEntitiesByToken.get(ind.getMarket().getPrimaryEntity());
				// this bonus isn't usable for this industry
				if (!bonus.canApply(ind, ent))
					continue;
				
				ordered.add(new Pair<>(ind, bonus.getPriority(ind, ent)));
			}
			
			Collections.sort(ordered, BONUS_COMPARATOR);
			
			for (int i = 0; i < count; i++)
			{
				if (ordered.isEmpty()) break;
				Pair<Industry, Float> highest = ordered.remove(ordered.size() - 1);
				Industry ind = highest.one;
				ProcGenEntity ent = procGen.procGenEntitiesByToken.get(ind.getMarket().getPrimaryEntity());
				
				log.info("Adding bonus " + bonus.getName() + " to industry " + ind.getNameForModifier() 
						+ " on " + ind.getMarket().getName());
				bonus.apply(ind, ent);
			}
		}		
	}
	
	public boolean hasSynchrotron() {
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			Industry fuelprod = market.getIndustry(Industries.FUELPROD);
			if (fuelprod == null) continue;
			if (fuelprod.getSpecialItem() == null) continue;
			if (fuelprod.getSpecialItem().getId().equals(Items.SYNCHROTRON))
				return true;
		}
		return false;
	}
	
	/**
	 * Gives a synchrotron to the fuel producer on the "most suitable" market in the sector, 
	 * if the procgenned sector does not already have a synchrotron somewhere.
	 */
	public void ensureHasSynchrotron() {
		BonusGen bonus = bonusesById.get("synchrotron");
		bonus.setMarketBuilder(this);
		Pair<Industry, ProcGenEntity> best = null;
		float bestScore = 0;
		
		for (ProcGenEntity market : markets) 
		{
			Industry fuelprod = market.market.getIndustry(Industries.FUELPROD);
			if (fuelprod == null) continue;
			
			// someone already has a synchrotron
			if (fuelprod.getSpecialItem() != null 
					&& fuelprod.getSpecialItem().getId().equals(Items.SYNCHROTRON))
			{
				return;
			}
			
			if (!bonus.canApply(fuelprod, market))
				continue;
			
			float score = bonus.getPriority(fuelprod, market);
			if (score > bestScore) {
				bestScore = score;
				best = new Pair<>(fuelprod, market);
			}
		}
		if (best != null) {
			log.info("Guaranteeing synchrotron on market " + best.two.name);
			bonus.apply(best.one, best.two);
		}
				
	}
	
	public static final Comparator BONUS_COMPARATOR = new NexUtils.PairWithFloatComparator(false);
}