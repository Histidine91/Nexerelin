package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.procgen.NameAssigner;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantStationFleetManager;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidBeltTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.AL_ChaosCrackFleetManager;
import org.magiclib.terrain.MagicAsteroidBeltTerrainPlugin;
import data.scripts.world.templars.TEM_Antioch;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.ExerelinSetupData.HomeworldPickMode;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.colony.ColonyTargetValuator;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

/*
import data.scripts.campaign.ExigencyCommRelayAdder;
import data.scripts.world.exipirated.ExipiratedAvestaFleetManager;
import data.scripts.world.exipirated.ExipiratedAvestaMovement;
import data.scripts.world.exipirated.ExipiratedCollectorFleetManager;
import data.scripts.world.exipirated.ExipiratedPatrolFleetManager;
*/


public class ExerelinProcGen {
	
	public static final boolean DEBUG_MODE = false;
	
	public static final float CORE_WIDTH = 24000;	// for comparison vanilla sector is 30,100 across from Tyle to Naraka
	public static final float CORE_HEIGHT = 12500;	// vanilla is 17,700 from Zagan to Penelope's Star
	public static final int CORE_RECURSION_MAX_DEPTH = 6;
	public static final float NEARBY_ENTITY_CHECK_DIST_SQ = 500 * 500;
	public static final Set<String> ALLOWED_STATION_TERRAIN = new HashSet<>(Arrays.asList(
		Terrain.ASTEROID_BELT, Terrain.ASTEROID_FIELD, Terrain.RING
	));
	public static final Set<String> DEFAULT_TERRAIN_NAMES = new HashSet<>(Arrays.asList(
		"Asteroid Belt", "Asteroid Field", "Ring System"
	));
	protected static final String PLANET_NAMES_FILE = "data/config/exerelin/planetNames.json";
	protected static List<String> stationNames = new ArrayList<>();
	public static final String RANDOM_CORE_SYSTEM_TAG = "nex_random_core_system";
	
	public static final List<String> STATION_IMAGES = new ArrayList<>(Arrays.asList(new String[] {
		"station_side00", "station_side02", "station_side04", "station_side06", "station_side07", "station_jangala_type"
	}));
	public static final Set<String> TAGS_TO_REMOVE = new HashSet<>(Arrays.asList(new String[] {
		Tags.THEME_DERELICT, Tags.THEME_DERELICT_MOTHERSHIP, Tags.THEME_DERELICT_PROBES, Tags.THEME_DERELICT_SURVEY_SHIP,
		Tags.THEME_REMNANT, Tags.THEME_REMNANT_DESTROYED, Tags.THEME_REMNANT_MAIN, Tags.THEME_REMNANT_RESURGENT, Tags.THEME_REMNANT_SECONDARY, Tags.THEME_REMNANT_SUPPRESSED,
		"theme_breakers", "theme_breakers_main", "theme_breakers_secondary", "theme_breakers_destroyed", "theme_breakers_suppressed", "theme_breakers_resurgent"
	}));
		
	public static Logger log = Global.getLogger(ExerelinProcGen.class);
	
	protected Set<String> factionIds = new HashSet<>();
	protected List<StarSystemAPI> systems = new ArrayList<>();	
	protected Set<StarSystemAPI> systemsWithGates = new HashSet<>();
	protected List<StarSystemAPI> maybePopulatedSystems = new ArrayList<>();
	protected Set<StarSystemAPI> populatedSystems = new HashSet<>();
	protected Map<StarSystemAPI, Float> positiveDesirabilityBySystem = new HashMap<>();
	protected Map<StarSystemAPI, List<ProcGenEntity>> marketsBySystem = new HashMap<>();
	protected Map<StarSystemAPI, ProcGenEntity> capitalsBySystem = new HashMap<>();
	protected List<ProcGenEntity> planets = new ArrayList<>();
	protected Set<ProcGenEntity> habitablePlanets = new HashSet<>();
	protected Set<ProcGenEntity> desirablePlanets = new HashSet<>();
	protected Set<ProcGenEntity> populatedPlanets = new HashSet<>();
	protected List<ProcGenEntity> stations = new ArrayList<>();
	protected Map<SectorEntityToken, ProcGenEntity> procGenEntitiesByToken = new HashMap<>();
	protected List<String> alreadyUsedStationNames = new ArrayList<>();
	
	protected Map<PlanetAPI, Float> planetDesirabilityCache = new HashMap<>();
	
	protected ProcGenEntity homeworld;
	
	protected ExerelinSetupData setupData;
	protected NexMarketBuilder marketSetup;
	protected boolean homeworldOnlyMode;
	
	protected Random random;
	
	
	
	static {
		loadData();
	}
	
	protected static void loadData()
	{
		try {
			JSONObject planetConfig = Global.getSettings().getMergedJSONForMod(PLANET_NAMES_FILE, ExerelinConstants.MOD_ID);
			
			JSONArray stationNames = planetConfig.getJSONArray("stations");
			ExerelinProcGen.stationNames = NexUtils.JSONArrayToArrayList(stationNames);
		} catch (JSONException | IOException ex) {
			log.error(ex);
		}
	}
		
	protected Set<String> getStartingFactions()
	{
		Set<String> factions = new HashSet<>();
		ExerelinSetupData setup = ExerelinSetupData.getInstance();
		for (Map.Entry<String, Boolean> tmp : setup.factions.entrySet())
		{
			String factionId = tmp.getKey();
			if (tmp.getValue())
			{
				factions.add(factionId);
				log.info("Added starting faction: " + factionId);
			}
		}
		String playerFaction = PlayerFactionStore.getPlayerFactionIdNGC();
		if (!playerFaction.equals(Factions.PLAYER) && !setup.freeStart)
		{
			factions.add(playerFaction);
			log.info("Added player starting faction: " + playerFaction);
		}
		
		log.info("Number of starting factions: " + factions.size());
		return factions;
	}
	
	public Random getRandom() {
		return random;
	}
	
	// =========================================================================
	// =========================================================================
	
	protected void pickEntityInteractionImage(SectorEntityToken entity, MarketAPI market, String planetType, EntityType entityType)
	{
		WeightedRandomPicker<String[]> allowedImages = new WeightedRandomPicker<>(random);
		allowedImages.add(new String[]{"illustrations", "cargo_loading"} );
		allowedImages.add(new String[]{"illustrations", "hound_hangar"} );
		allowedImages.add(new String[]{"illustrations", "space_bar"} );
		
		String factionId = entity.getFaction().getId();
		boolean isStation = (entityType == EntityType.STATION);
		boolean isMoon = (entityType == EntityType.MOON); 
		int size = market.getSize();
		boolean largeMarket = size >= 5;
		if (isStation) largeMarket = size >= 4;
		
		if (market.hasCondition(Conditions.URBANIZED_POLITY) || largeMarket)
		{
			if (!factionId.equals(Factions.LUDDIC_PATH))
			{
				allowedImages.add(new String[]{"illustrations", "urban00"} );
				allowedImages.add(new String[]{"illustrations", "urban01"} );
				allowedImages.add(new String[]{"illustrations", "urban02"} );
				allowedImages.add(new String[]{"illustrations", "urban03"} );
				allowedImages.add(new String[]{"illustrations", "corporate_lobby"} );
			}
			
			if (NexUtilsFaction.doesFactionExist("citadeldefenders"))
			{
				allowedImages.add(new String[]{"illustrationz", "streets"} );
				if (!isStation) allowedImages.add(new String[]{"illustrationz", "twin_cities"} );
			}
			if (!isStation && planetType.contains(Planets.TUNDRA))
			{
				allowedImages.add(new String[]{"illustrations", "eochu_bres"} );
			}
		}
		if (!isStation && market.hasIndustry(Industries.MINING))
		{
			allowedImages.add(new String[]{"illustrations", "mine"} );
		}
		
		if (largeMarket)
		{
			allowedImages.add(new String[]{"illustrations", "industrial_megafacility"} );
			allowedImages.add(new String[]{"illustrations", "city_from_above"} );
		}
		if (isStation && largeMarket)
		{
			allowedImages.add(new String[]{"illustrations", "jangala_station"} );
			allowedImages.add(new String[]{"illustrations", "orbital"} );
		}
		if (factionId.equals(Factions.PIRATES))
			allowedImages.add(new String[]{"illustrations", "pirate_station"} );
		if (!isStation && (planetType.equals("rocky_metallic") || planetType.equals("rocky_barren") || planetType.equals("barren-bombarded")) )
			allowedImages.add(new String[]{"illustrations", "vacuum_colony"} );
		if (factionId.equals(Factions.LUDDIC_CHURCH)) {
			allowedImages.add(new String[]{"illustrations", "luddic_shrine"} );
			if (planetType.contains(Planets.PLANET_TERRAN))
				allowedImages.add(new String[]{"illustrations", "gilead"} );
		}
		if (isStation && NexUtilsFaction.doesFactionExist("blackrock_driveyards"))
			allowedImages.add(new String[]{"illustrations", "blackrock_vigil_station"} );
		
		//if (isMoon)
		//	allowedImages.add(new String[]{"illustrations", "asteroid_belt_moon"} );


		// note: some of these planet type checks are 'equals', some are just 'contains'
		if (planetType.contains(Planets.DESERT) && isMoon)
			allowedImages.add(new String[]{"illustrations", "desert_moons_ruins"} );

		if (planetType.equals(Planets.PLANET_TERRAN_ECCENTRIC))
			allowedImages.add(new String[]{"illustrations", "eventide"} );

		if (planetType.equals(Planets.PLANET_WATER))
			allowedImages.add(new String[]{"illustrations", "volturn"} );

		if (planetType.contains(Planets.ARID))
			allowedImages.add(new String[]{"illustrations", "mazalot"} );

		if (planetType.contains(Planets.BARREN))
			allowedImages.add(new String[]{"illustrations", "kazeron"} );

		if (planetType.contains("cryovolcanic") || planetType.contains("frozen"))
			allowedImages.add(new String[]{"illustrations", "ilm"} );

		String[] illustration = allowedImages.pick();
		entity.setInteractionImage(illustration[0], illustration[1]);
	}
	
	protected ProcGenEntity getHomeworld() 	{
		return homeworld;
	}
	
	/**
	 * Checks if the system contains markets added by another mod, e.g. AE's IX Battlegroup.
	 * @param system
	 * @return 
	 */
	protected boolean hasForeignMarkets(StarSystemAPI system) {
		return !Global.getSector().getEconomy().getMarkets(system).isEmpty();
	}
	
	/**
	 * Returns whether the "core systems" are sufficient in number and have enough desirable planets
	 * @param systems List of current core systems
	 * @return
	 */
	protected boolean validateCoreSystems(List<StarSystemAPI> systems)
	{
		if (!homeworldOnlyMode && systems.size() < setupData.numSystems)
			return false;
		
		int numDesirables = 0;
		int numSystems = 0;
		float req = homeworldOnlyMode ? 2 : 0.01f;
		for (StarSystemAPI system : systems)
		{
			int countThisSystem = 0;
			for (PlanetAPI planet : system.getPlanets())
			{
				if (planet.getMarket() == null) continue;
				if (getDesirability(planet) < req) continue;
				if (homeworldOnlyMode && planet.getMarket().getHazardValue() > 1.75f)
					continue;
				countThisSystem++;
				numDesirables++;
				if (!homeworldOnlyMode && countThisSystem >= setupData.maxPlanetsPerSystem) break; 
			}
			if (countThisSystem > 0) numSystems++;
		}
				
		boolean result;
		if (homeworldOnlyMode) result = numDesirables >= 1;
		else result = numDesirables >= setupData.numPlanets && numSystems > setupData.numSystems;
		log.info(String.format("%s core system validation with %s usable planets in %s systems", 
				(result ? "Passed" : "Failed"), numDesirables, numSystems));
		return result;
	}
	
	/**
	 * Get systems close to the Sector's center. 
	 * Widens search if number of systems/planets contained is less than number of systems/planets wanted.
	 * @param width Width of search area
	 * @param height Height of search area
	 * @param recursionDepth How many times this method has recursed; if too high, stop expanding search area
	 * @return
	 */
	protected List<StarSystemAPI> getCoreSystems(float width, float height, int recursionDepth)
	{
		log.info(String.format("Searching for core systems in %s width, %s height", width, height));
		
		List<StarSystemAPI> list = new ArrayList<>();
		PlanetAPI redPlanet = (PlanetAPI)Global.getSector().getMemoryWithoutUpdate().get(MiscellaneousThemeGenerator.PLANETARY_SHIELD_PLANET_KEY);
		boolean canPickPopulatedSystems = homeworldOnlyMode;
		
		boolean canPickCore = setupData.homeworldPickMode.canPickCore();
		boolean canPickNonCore = setupData.homeworldPickMode.canPickNonCore();
				
		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{			
			//log.info("Trying " + system.getBaseName());
			
			// go ahead and spawn in Remnant systems, we'll clean them out later?
			// actually no, I don't know what'll happen if we clear out the system with SEEKER's Nova
			// also in general people might wanna use the Remnant systems			
			if (system.hasTag(Tags.THEME_REMNANT_MAIN)) {
				//log.info("  Skipping Remnant system");
				continue;
			}
			if (system.hasTag(Tags.THEME_DERELICT_SURVEY_SHIP) || 
					system.hasTag(Tags.THEME_DERELICT_MOTHERSHIP) || 
					system.hasTag(Tags.THEME_DERELICT_CRYOSLEEPER)) 
			{
				//log.info("  Skipping Derelict system");
				continue;
			}
			if (system.hasTag(Tags.THEME_UNSAFE)) {
				//log.info("  Skipping unsafe system");
				continue;
			}
			if (system.hasTag("theme_plaguebearers")) {
				//log.info("  Skipping plaguebearer system");
				continue;
			}
			if (system.hasPulsar()) {
				//log.info("  Skipping pulsar system");
				continue;
			}
			if (system.getStar() != null && system.getStar().getSpec().isBlackHole()) {
				//log.info("  Skipping black hole system");
				continue;
			}
			
			
			boolean hasForeign = hasForeignMarkets(system);
			// random sector: don't seed procgen markets in non-procgen systems
			if (!canPickPopulatedSystems && hasForeign) {
				//log.info("Skipping system " + system.getBaseName() + " due to existing population");
				continue;
			}
			
			/*
				Note: tagging systems as core or non-core is done afterEconomyLoad,
				while populating systems in random sector is done afterProcGen.
				The significance of this is that any systems generated in random sector
				but not populated will be considered non-core at this stage, but will be
				considered a core system once the player is ingame (including for the debug intel).
			*/			
			boolean core = system.hasTag(Tags.THEME_CORE);
			if (homeworldOnlyMode) {
				if (!canPickCore && core) {
					//log.info("Skipping system " + system.getBaseName() + " as core");
					continue;
				}
				if (!canPickNonCore && !core) {
					//log.info("Skipping system " + system.getBaseName() + " as non-core");
					continue;
				}	
			}
			
			
			Vector2f loc = system.getLocation();
			if (Math.abs(loc.x - ExerelinNewGameSetup.SECTOR_CENTER.x) > width/2) {
				//log.info("  System too far: " + Math.abs(loc.x - ExerelinNewGameSetup.SECTOR_CENTER.x));				
				continue;
			}
			if (Math.abs(loc.y - ExerelinNewGameSetup.SECTOR_CENTER.y) > height/2) {
				//log.info("  System too far: " + Math.abs(loc.y - ExerelinNewGameSetup.SECTOR_CENTER.y));				
				continue;
			}					
			
			if (system.getBaseName().equals("Styx")) continue;
			if (system.getBaseName().equals("Ascalon")) continue;
			if (redPlanet != null && redPlanet.getStarSystem() == system)
				continue;
			
			if (!core) {
				//log.info("Adding non-core system: " + system.getBaseName());
			}
			list.add(system);
			
			// while we're here, check if it has a gate
			for (SectorEntityToken token : system.getEntitiesWithTag(Tags.GATE)) {
				systemsWithGates.add(system);
				break;
			}
		}
		
		// not enough systems/planets, expand our search
		if (!validateCoreSystems(list) && recursionDepth < CORE_RECURSION_MAX_DEPTH) 
			return getCoreSystems(width * 1.5f, height * 1.5f, recursionDepth + 1);
		
		return list;
	}
	
	/**
	 * Increment the system's sum of desirability on its positive-desirability planets
	 * @param system
	 * @param amount
	 */
	protected void addDesirabilityForSystem(StarSystemAPI system, float amount)
	{
		if (positiveDesirabilityBySystem.containsKey(system))
			amount += positiveDesirabilityBySystem.get(system);
		positiveDesirabilityBySystem.put(system, amount);
	}
	
	protected float getDesirabilityForMarketCondition(MarketConditionAPI cond)
	{
		switch (cond.getId()) {
			case Conditions.HABITABLE:
				return 2;
			case Conditions.MILD_CLIMATE:
				return 0.5f;
			case Conditions.ORE_MODERATE:
			case Conditions.RARE_ORE_MODERATE:
			case Conditions.ORGANICS_COMMON:
				return 0.15f;
			case Conditions.ORE_ABUNDANT:
			case Conditions.RARE_ORE_ABUNDANT:
			case Conditions.ORGANICS_ABUNDANT:
			case Conditions.VOLATILES_TRACE:
				return 0.3f;
			case Conditions.ORE_RICH:
			case Conditions.RARE_ORE_RICH:
			case Conditions.ORGANICS_PLENTIFUL:
			case Conditions.VOLATILES_DIFFUSE:
				return 0.6f;
			case Conditions.ORE_ULTRARICH:
			case Conditions.RARE_ORE_ULTRARICH:
			case Conditions.VOLATILES_ABUNDANT:
				return 1f;
			case Conditions.VOLATILES_PLENTIFUL:
				return 1.5f;
			case Conditions.FARMLAND_ADEQUATE:
			case Conditions.WATER_SURFACE:
				return 0.2f;
			case Conditions.FARMLAND_RICH:
				return 0.35f;
			case Conditions.FARMLAND_BOUNTIFUL:
				return 0.6f;
			case Conditions.NO_ATMOSPHERE:
				return 0;	// higher than normal hazard conditions so we can have more synchrotron candidates
			case Conditions.HIGH_GRAVITY:
				return -0.5f;	// hate it
		}
		if (cond.getGenSpec() != null)
		{
			float hazard = cond.getGenSpec().getHazard();
			if (hazard >= 0.5f)
				return -0.3f;
			else if (hazard >= 0.25f)
				return -0.15f;
		}
		return 0;
	}
	
	/**
	 * Get how "desirable" the planet is based on its market conditions.
	 * @param planet
	 * @return
	 */
	protected float getDesirability(PlanetAPI planet)
	{
		if (planetDesirabilityCache.containsKey(planet))
			return planetDesirabilityCache.get(planet);
		
		MarketAPI market = planet.getMarket();
		if (market == null) return 0;
		
		float desirability = 0.5f;
		for (MarketConditionAPI cond : market.getConditions())
		{
			desirability += getDesirabilityForMarketCondition(cond);
		}
		
		if (isInCorona(planet)) desirability -= 1;
		if (systemsWithGates.contains(planet.getStarSystem())) desirability += 1;
		
		// cap desirability based on hazard
		//float hazard = market.getHazardValue();
		//desirability = Math.min(desirability, (3.25f - hazard) * 2);
		
		planetDesirabilityCache.put(planet, desirability);
		
		return desirability;
	}
	
	public static ProcGenEntity createEntityData(SectorEntityToken token)
	{
		ProcGenEntity data = new ProcGenEntity(token);
		PlanetAPI planet = null;
		if (token instanceof PlanetAPI) planet = (PlanetAPI)token;
		data.name = token.getName();
		data.type = planet != null ? (planet.isMoon() ? EntityType.MOON : EntityType.PLANET) : EntityType.STATION;
		data.market = token.getMarket();
		if (planet != null)
			data.planetType = planet.getTypeId();
		data.primary = token.getOrbitFocus();
		if (token.getStarSystem() != null)
			data.starSystem = (StarSystemAPI)token.getStarSystem();
		
		return data;
	}
	
	/**
	 * Creates ProcGenEntities for planets in the star system.
	 * @param system
	 */
	protected void createEntityDataForSystem(StarSystemAPI system)
	{
		//if (!system.isProcgen()) return;
		
		for (PlanetAPI planet : system.getPlanets())
		{
			if (planet.isStar()) continue;
			if (planet.getMarket() == null || !planet.getMarket().isPlanetConditionMarketOnly()) {
				//log.info(String.format("  Planet %s has no market or is already inhabited", planet.getName()));
				continue;
			}
			
			if (planet.getMarket().getMemoryWithoutUpdate().getBoolean(ColonyTargetValuator.MEM_KEY_NO_COLONIZE)) 
			{
				continue;
			}
				
			if (planet.getId().equals("ancyra")) continue;

			if (planet.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) continue;
			
			if (planet.isGasGiant()){
				// gas giants are of interest even though we won't populate them directly
				addDesirabilityForSystem(system, getDesirability(planet));
				continue;
			}
			
			log.info("Creating entity data for planet " + planet.getName());
			ProcGenEntity planetData = createEntityData(planet);
			planetData.desirability = getDesirability(planet);
			procGenEntitiesByToken.put(planet, planetData);
			planets.add(planetData);
			log.info("\tPlanet desirability: " + planetData.desirability);
			if (planetData.desirability >= 0)
			{
				desirablePlanets.add(planetData);
				addDesirabilityForSystem(system, planetData.desirability);
			}
			if (planet.getMarket().hasCondition(Conditions.HABITABLE))
			{
				//log.info("\tPlanet is habitable");
				habitablePlanets.add(planetData);
			}
		}
	}
	
	protected List<ProcGenEntity> getPopulatedMoons(ProcGenEntity planet)
	{
		List<ProcGenEntity> results = new ArrayList<>();
		for (ProcGenEntity maybeMoon : populatedPlanets)
		{
			if (maybeMoon.type != EntityType.MOON)
				continue;
			if (maybeMoon.primary == planet.entity)
				results.add(maybeMoon);
		}
		return results;
	}
	
	protected void removePopulatedPlanet(ProcGenEntity toRemove)
	{
		log.info("Depopulating entity " + toRemove.name);
		populatedPlanets.remove(toRemove);
		StarSystemAPI system = toRemove.starSystem;
		marketsBySystem.get(system).add(toRemove);
		if (marketsBySystem.get(system).isEmpty())
			populatedSystems.remove(system);
	}
	
	/**
	 * Fills the populatedPlanets list with planets. 
	 * Will skip a star system if it already has too many populated planets.
	 * @param picker Random picker
	 * @param planets List of candidate planets to be populated
	 */
	protected void pickPopulatedPlanets(WeightedRandomPicker<ProcGenEntity> picker, Collection<ProcGenEntity> planets)
	{
		picker.clear();
		for (ProcGenEntity planet: planets)
		{
			// already picked before?
			if (populatedPlanets.contains(planet)) {
				//log.info("Planet " + planet.name + " already populated");
				continue;
			}
			
			float weight = Math.max(planet.desirability + 1, 0.2f);
			if (populatedSystems.contains(planet.starSystem))
				weight *= 99;	// strongly prefer already inhabited systems
			picker.add(planet, weight);
			//log.info("  Added planet " + planet.name + " to picker");
		}
		
		while (populatedPlanets.size() < setupData.numPlanets && !picker.isEmpty())
		{
			ProcGenEntity candidate = picker.pickAndRemove();
			int numMarketsInSystem = marketsBySystem.get(candidate.starSystem).size();
			if (numMarketsInSystem >= setupData.maxPlanetsPerSystem)
			{
				log.info("Reached max markets for system " + candidate.starSystem.getBaseName());
				continue;
			}
			
			// don't populate a moon if our primary is already populated and nicer
			// if we're nicer than primary, remove that instead
			if (candidate.type == EntityType.MOON)
			{
				ProcGenEntity primary = procGenEntitiesByToken.get(candidate.primary);
				if (primary != null)
				{
					if (primary.desirability >= candidate.desirability) {
						log.info("Moon less attractive than primary, skipping: " + candidate.name + ", " + primary.name);
						continue;
					}
					else
						removePopulatedPlanet(primary);
				}
			}
			// don't populate a planet if any of our moons are nicer
			else {
				List<ProcGenEntity> populatedMoons = getPopulatedMoons(candidate);
				boolean shouldSkip = false;
				for (ProcGenEntity moon : populatedMoons)
				{
					if (moon.desirability > candidate.desirability)
					{
						log.info("Planet less attractive than its moon, skipping: " + candidate.name + ", " + moon.name);
						shouldSkip = true;
						break;
					}
				}
				
				if (shouldSkip) continue;
				else
					for (ProcGenEntity moon : populatedMoons) removePopulatedPlanet(moon);
			}
			
			log.info("Populating planet " + candidate.name + " (desirability " + candidate.desirability + ")");
			populatedPlanets.add(candidate);
			populatedSystems.add(candidate.starSystem);
			marketsBySystem.get(candidate.starSystem).add(candidate);
		}
	}
	
	/**
	 * Fills the populatedPlanets list with planets
	 * Picks habitable planets first, then non-habitable but otherwise desirable planets
	 */
	protected void pickPopulatedPlanets()
	{
		WeightedRandomPicker<ProcGenEntity> picker = new WeightedRandomPicker<>(random);
		List<ProcGenEntity> desirableNotHabitable = new ArrayList<>(desirablePlanets);
		desirableNotHabitable.removeAll(habitablePlanets);
		List<ProcGenEntity> notDesirable = new ArrayList<>();
		for (ProcGenEntity entity : planets)
		{
			PlanetAPI planet = (PlanetAPI)entity.entity;
			if (!planet.isGasGiant() && !desirablePlanets.contains(entity))
				notDesirable.add(entity);
		}
		
		log.info("Picking habitable planets: " + habitablePlanets.size());
		List<ProcGenEntity> habitablePlanetsList = new ArrayList<>(habitablePlanets);
		Collections.sort(habitablePlanetsList, DESIRABILITY_COMPARATOR);
		pickPopulatedPlanets(picker, habitablePlanetsList);
		
		log.info("Picking other desirable planets: " + desirableNotHabitable.size());
		Collections.sort(desirableNotHabitable, DESIRABILITY_COMPARATOR);
		pickPopulatedPlanets(picker, desirableNotHabitable);
		
		log.info("Picking undesirable planets: " + notDesirable.size());
		Collections.sort(notDesirable, DESIRABILITY_COMPARATOR);
		pickPopulatedPlanets(picker, notDesirable);
	}
	
	protected boolean isStationNameAlreadyUsed(String newName)
	{
		for (String name : alreadyUsedStationNames)
		{
			if (name.equals(newName)) return true;
		}
		return false;
	}
	
	public String getStationName(SectorEntityToken target, CampaignTerrainAPI terrain)
	{
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		picker.addAll(stationNames);
		String name = target.getName();
		
		// try to name station after the terrain it's in
		if (terrain != null && terrain.getName() != null && !terrain.getName().isEmpty()
				&& !DEFAULT_TERRAIN_NAMES.contains(terrain.getName()))
		{
			String[] terrainNames = terrain.getName().split(" ");
			boolean isL4OrL5 = terrainNames.length >= 2 && 
					(terrainNames[1].equalsIgnoreCase("L4") || terrainNames[1].equalsIgnoreCase("L5"));
			
			if (isL4OrL5) {
				name = terrainNames[0] + " " + terrainNames[1];
			}
			else {
				for (String terrainName : terrainNames) {
					if (terrainName.isEmpty()) continue;
					if (terrainName.equalsIgnoreCase("The")) continue;

					name = terrainName;
					break;
				}
			}
		}
		
		String ret;
		do
		{
			ret = name + " " + picker.pickAndRemove();
		} while (isStationNameAlreadyUsed(ret) && !picker.isEmpty());
		return ret;
	}
	
	/**
	 * Creates a ProcGenEntity for a station at the specified in-system location
	 * @param target The entity to orbit if terrain is null; can also be the terrain
	 * @return
	 */
	protected ProcGenEntity createEntityDataForStation(SectorEntityToken target)
	{
		ProcGenEntity data = new ProcGenEntity(null);
		data.type = EntityType.STATION;
		data.primary = target;
		if (target.getContainingLocation() instanceof StarSystemAPI)
			data.starSystem = (StarSystemAPI)target.getContainingLocation();
		if (target instanceof CampaignTerrainAPI)
		{
			data.terrain = (CampaignTerrainAPI)target;
			data.primary = target.getOrbitFocus();
		}
		data.name = getStationName(data.primary, data.terrain);
		if (target instanceof PlanetAPI)
		{
			data.planetType = ((PlanetAPI)target).getTypeId();
		}
		alreadyUsedStationNames.add(data.name);
		
		return data;
	}
	
	public static boolean isTerrainNearObstruction(SectorEntityToken entity, StarSystemAPI system) {
		List<SectorEntityToken> toCheck = new ArrayList<>();
		toCheck.addAll(system.getJumpPoints());
		toCheck.addAll(system.getPlanets());
		toCheck.addAll(system.getEntitiesWithTag(Tags.OBJECTIVE));
		toCheck.addAll(system.getEntitiesWithTag(Tags.STABLE_LOCATION));
		
		for (SectorEntityToken other : toCheck) {
			float distSq = MathUtils.getDistanceSquared(entity.getLocation(), other.getLocation());
			if (distSq < NEARBY_ENTITY_CHECK_DIST_SQ) {
				log.info(String.format("  Rejecting terrain %s due to nearby %s", entity.getName(), other.getName()));
				return true;
			}
				
		}
		
		return false;
	}
	
	/**
	 * Finds suitable locations for free stations and creates ProcGenEntities for them, so they may be created later
	 * Don't create them right now, as we haven't picked factions for them yet
	 */
	protected void prepFreeStations()
	{
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		
		List<StarSystemAPI> popSystemsList = new ArrayList<>(populatedSystems);
		Collections.sort(popSystemsList, new Comparator<StarSystemAPI>() {
			@Override
			public int compare(StarSystemAPI one, StarSystemAPI two) {
				return one.getName().compareTo(two.getName());
			}
		});
		
		for (StarSystemAPI system : popSystemsList)
		{
			for (PlanetAPI planet : system.getPlanets())
			{
				// no stations directly orbiting a star (we'll deal with asteroid belts and such later)
				if (planet.isStar()) continue;
				
				// don't put stations around any inhabited planets
				/*
				if (procGenEntitiesByToken.containsKey(planet))
				{
					ProcGenEntity entity = procGenEntitiesByToken.get(planet);
					if (populatedPlanets.contains(entity)) continue;
				}
				// ... nor any moons of inhabited planets
				SectorEntityToken primary = planet.getOrbitFocus();
				if (primary != null && procGenEntitiesByToken.containsKey(primary))
				{
					ProcGenEntity primary2 = procGenEntitiesByToken.get(primary);
					if (populatedPlanets.contains(primary2)) continue;
				}
				*/
				
				// you know what, just check all planets nearby to this one for markets
				boolean allow = true;
				for (PlanetAPI maybeNearbyPlanet : system.getPlanets())
				{
					ProcGenEntity entity = procGenEntitiesByToken.get(maybeNearbyPlanet);
					if (entity == null) continue;
					if (!populatedPlanets.contains(entity)) continue;
					
					if (maybeNearbyPlanet == planet)
					{
						//log.info("  Blocking populated planet " + planet.getName());
						allow = false;
						break;
					}
					if (MathUtils.isWithinRange(planet, maybeNearbyPlanet, 2000))
					{
						log.info("  Blocking " + planet.getName() + " due to nearby populated planet: " + maybeNearbyPlanet.getName());
						allow = false;
						break;
					}
				}
				if (!allow) continue;
				
				float weight = 0;
				if (planet.isGasGiant()) weight = 8;	// helps us get volatiles
				if (weight > 0)
					picker.add(planet, weight);
			}
			for (CampaignTerrainAPI terrain : system.getTerrainCopy())
			{
				if (terrain.getType().equals(Terrain.ASTEROID_FIELD) 
						&& isTerrainNearObstruction(terrain, system)) {
					continue;
				}
				
				SectorEntityToken orbitFocus = terrain.getOrbitFocus();
				if (orbitFocus == null || !orbitFocus.isStar()) continue;
				
				if (ALLOWED_STATION_TERRAIN.contains(terrain.getType()))
				{
					picker.add(terrain, 2);
				}
			}
		}
		log.info("  Picker length: " + picker.getItems().size());
		
		int count = 0;
		while (count < setupData.numStations && !picker.isEmpty())
		{
			SectorEntityToken target = picker.pickAndRemove();
			StarSystemAPI loc = (StarSystemAPI)target.getContainingLocation();
			if (marketsBySystem.get(loc).size() >= setupData.maxMarketsPerSystem)
				continue;
			
			ProcGenEntity station = createEntityDataForStation(target);
			stations.add(station);
			marketsBySystem.get(loc).add(station);
			count++;
		}
	}
	
	protected boolean isInCorona(PlanetAPI planet)
	{
		StarSystemAPI system = planet.getStarSystem();
		if (system == null) return false;
		
		return (HubMissionWithTriggers.isNearCorona(system, planet.getLocation()));
	}
	
	/**
	 * Picks the "homeworld" (player faction's HQ) from the most desirable planets.
	 * @return
	 */
	protected ProcGenEntity pickHomeworld()
	{		
		List<ProcGenEntity> candidates = new ArrayList<>(populatedPlanets);
		Collections.sort(candidates, DESIRABILITY_COMPARATOR);
		
		if (DEBUG_MODE || Global.getSettings().isDevMode()) {
			Global.getSector().getMemoryWithoutUpdate().set("$nex_randomSector_colonyCandidates", candidates);
			Global.getSector().getIntelManager().addIntel(new HomeworldPickerDebugIntel());
		}
		
		if (homeworldOnlyMode) {
			for (ProcGenEntity candidate : candidates) {
				if (!ExerelinSetupData.getInstance().homeworldAllowNeighbors && !Global.getSector().getEconomy().getMarkets(candidate.market.getContainingLocation()).isEmpty()) {
					//log.info("Excluding " + candidate.name + " due to having neighbors");
					continue;
				}
				if (candidate.market.getHazardValue() > 1.75f) continue;
				homeworld = candidate;
				break;
			}
			if (homeworld == null) {
				homeworld = candidates.get(0);
			}
			return homeworld;
		}
				
		WeightedRandomPicker<ProcGenEntity> picker = new WeightedRandomPicker<>(random);
		for (int i=0; i<candidates.size(); i++)
		{
			if (i == 5) break;
			ProcGenEntity candidate = candidates.get(i);
			log.info("Adding homeworld candidate " + candidate.name + ", desirability " + candidate.desirability);
			picker.add(candidate);
		}
		homeworld = picker.pick();
		return homeworld;
	}
	
	/**
	 * Spawns comm relays in each star system, or converts existing ones made by procgen
	 */
	protected void spawnCommRelays(Collection<StarSystemAPI> systems)
	{
		for (StarSystemAPI system : systems)
		{
			SectorEntityToken relay = null;
			String factionId = null;
			ProcGenEntity capital = capitalsBySystem.get(system);
			if (capital != null) factionId = capital.market.getFactionId();
			
			// see if there are existing relays or other objectives we can co-opt
			for (SectorEntityToken objective : system.getEntitiesWithTag(Tags.OBJECTIVE))
			{
				if (objective.hasTag(Tags.COMM_RELAY))
				{
					relay = objective;
					relay.setFaction(factionId);
				}
				
				objective.getMemoryWithoutUpdate().unset(MemFlags.OBJECTIVE_NON_FUNCTIONAL);
				continue;
			}
			
			// see if system has any stable locations that could be left for a relay
			if (!system.getEntitiesWithTag(Tags.STABLE_LOCATION).isEmpty())
			{
				continue;
			}
			
			// no relays nor stable locations; make our own relay
			if (relay == null)
			{
				log.info("Creating comm relay for system " + system.getName());
				
				relay = system.addCustomEntity(system.getId() + "_relay", // unique id
						system.getBaseName() + " " + StringHelper.getString("relay", true), // name - if null, defaultName from custom_entities.json will be used
						"comm_relay", // type of object, defined in custom_entities.json
						factionId); // faction
				
				int lp = 4;
				if (random.nextBoolean()) lp = 5;
				
				SectorEntityToken capEntity = capital.entity;
				if (capital.type == EntityType.STATION && capital.terrain == null)
					capEntity = capEntity.getOrbitFocus();
				if (capEntity instanceof PlanetAPI && ((PlanetAPI)capEntity).isMoon()) 
					capEntity = capEntity.getOrbitFocus();
				
				SectorEntityToken systemPrimary = capEntity.getOrbitFocus();
				if (systemPrimary != null)	// if null, maybe it's a nebula?
				{
					float orbitRadius = NexUtilsAstro.getCurrentOrbitRadius(capEntity, systemPrimary);
					float startAngle = NexUtilsAstro.getCurrentOrbitAngle(capEntity, systemPrimary);
					
					NexUtilsAstro.setLagrangeOrbit(relay, systemPrimary, capEntity, 
						lp, startAngle, orbitRadius, 0, capEntity.getOrbit().getOrbitalPeriod(), 
						false, 0, 1, 1, 0);
					log.info("Placing relay at Lagrange point of " + capEntity.getName() + ", distance " + orbitRadius);
					
					// check for overlap with other entities
					
					List<SectorEntityToken> toCheck = new ArrayList<>();
					toCheck.addAll(system.getPlanets());
					toCheck.addAll(system.getJumpPoints());
					
					for (SectorEntityToken ent : toCheck)
					{
						float distSq = MathUtils.getDistanceSquared(relay, ent);
						if (distSq < 200 * 200)
						{
							//log.info("Relay overlap with an entity detected, changing Lagrange point");
							lp = 9 - lp;
							NexUtilsAstro.setLagrangeOrbit(relay, systemPrimary, capEntity, 
								lp, startAngle, orbitRadius, 0, capEntity.getOrbit().getOrbitalPeriod(), 
								false, 0, 1, 1, 0);
							log.info("Placing relay at other Lagrange point of " + capEntity.getName());
							break;
						}
					}
				}
				
				if (MathUtils.getDistanceSquared(relay, system.getCenter()) < Math.pow(system.getCenter().getRadius(), 2))
					log.info("Warning: Relay in " + system.getName() + " is being cooked");
			}
		}
	}
	
	protected void surveyPlanets()
	{
		for (StarSystemAPI system : populatedSystems)
		{
			for (PlanetAPI planet : system.getPlanets())
			{
				if (planet.isStar()) continue;
				MarketAPI market = planet.getMarket();
				if (market == null || !market.isPlanetConditionMarketOnly())
					continue;
				market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
			}
		}
	}
	
	/**
	 * Sets capitals for each star system
	 * [deprecated] When the capital is captured, the relay changes owner
	 */
	protected void setCapitals()
	{
		for (StarSystemAPI system : populatedSystems)
		{
			ProcGenEntity capital = null;
			
			List<ProcGenEntity> markets = marketsBySystem.get(system);
			if (markets.size() == 1)
			{
				capital = markets.get(0);
			}
			else
			{
				for (ProcGenEntity market : markets)
				{
					if (capital == null)
						capital = market;
					else
					{
						if (market.isHQ && !capital.isHQ || market.market.getSize() > capital.market.getSize())
							capital = market;
					}
				}
			}
			
			if (capital == null) continue;
			
			capital.isCapital = true;
			capitalsBySystem.put(system, capital);
			/*
			if (!capital.market.hasCondition(Conditions.HEADQUARTERS) 
					&& capital.market.hasCondition(Conditions.REGIONAL_CAPITAL))
				capital.market.addCondition(Conditions.REGIONAL_CAPITAL);
			*/
		}
	}
	
	// TODO: this should not run ideally, pick systems that don't have derelicts in them
	public static void cleanupDerelicts(Collection<StarSystemAPI> systems)
	{
		LocationAPI hyper = Global.getSector().getHyperspace();
		List<SectorEntityToken> toRemove = new ArrayList<>();
		Set<SectorEntityToken> hyperAnchors = new HashSet<>();
		for (StarSystemAPI system : systems)
		{
			log.info("Cleaning up system " + system.getName());
			NexUtils.removeScriptAndListener(system, RemnantStationFleetManager.class, null);
			NexUtils.removeScriptAndListener(system, RemnantSeededFleetManager.class, null);
			
			for (SectorEntityToken token : system.getAllEntities())
			{
				if (token.getMarket() != null)
					continue;
				if (token.hasTag(Tags.GATE) || token.hasTag(Tags.DEBRIS_FIELD)
						|| token.hasTag(Tags.OBJECTIVE)) continue;
				if (token.getFaction().getId().equals(Factions.DERELICT) 
						|| token.getFaction().getId().equals(Factions.REMNANTS) 
						|| token.getFaction().getId().equals("blade_breakers"))
					toRemove.add(token);
				else if (token.hasTag(Tags.SALVAGEABLE))
					toRemove.add(token);
			}
			
			for (String tag : TAGS_TO_REMOVE)
			{
				system.removeTag(tag);
			}
			
			hyperAnchors.add(system.getHyperspaceAnchor());
		}
		for (SectorEntityToken beacon : hyper.getEntitiesWithTag(Tags.WARNING_BEACON))
		{
			SectorEntityToken orbitFocus = beacon.getOrbitFocus();
			if (orbitFocus != null && hyperAnchors.contains(orbitFocus))
			{
				StarSystemAPI nearest = Misc.getNearestStarSystem(orbitFocus);
				log.info("\tBeacon orbits a cleaned-up system's hyper anchor (nearest star system " + nearest.getName() + "), removing");
				toRemove.add(beacon);
			}
		}
		
		for (SectorEntityToken token : toRemove)
		{
			log.info("\tRemoving token " + token.getName() + "(faction " + token.getFaction().getDisplayName() + ")");
			token.getContainingLocation().removeEntity(token);
		}
	}
	
	protected void renameSystems()
	{
		for (StarSystemAPI system : populatedSystems)
		{
			if (!NameAssigner.isNameSpecial(system))
				NameAssigner.assignSpecialNames(system);
			for (SectorEntityToken entity : system.getPlanets()) {
				if (entity.getMarket() != null) {
					entity.getMarket().setName(entity.getName());
				}
			}
		}
	}
	
	//==========================================================================
	
	protected void init()
	{
		random = StarSystemGenerator.random;
		//random = new Random(ExerelinUtils.getStartingSeed());
		marketSetup = new NexMarketBuilder(this);
		setupData = ExerelinSetupData.getInstance();
		factionIds = getStartingFactions();
	}
	
	// runcode exerelin.world.ExerelinProcGen.debug();
	public static void debug() {
		ExerelinProcGen procgen = new ExerelinProcGen();
		procgen.generate(true, true);
	}
	
	public void generate(boolean homeworldOnlyMode) {
		generate(homeworldOnlyMode, false);
	}
	
	public void generate(boolean homeworldOnlyMode, boolean testOnly)
	{
		this.homeworldOnlyMode = homeworldOnlyMode;
		
		log.info("Running procedural generation, homeworld-only mode: " + homeworldOnlyMode);
		init();
		
		// ensure consistent output of random when using own faction start in non-random sector
		if (setupData.corvusMode) {
			random = new Random(NexUtils.getStartingSeed());
		}
		
		// process star systems
		float mult = homeworldOnlyMode ? 3 : 1;
		systems = getCoreSystems(CORE_WIDTH * mult, CORE_HEIGHT * mult, 1);
		for (StarSystemAPI system : systems)
		{
			positiveDesirabilityBySystem.put(system, 0f);
			marketsBySystem.put(system, new ArrayList<ProcGenEntity>());
			createEntityDataForSystem(system);
		}
						
		if (homeworldOnlyMode) {
			// just create our homeworld
			populatedPlanets.addAll(planets);
			pickHomeworld();
			
			if (testOnly) return;
			
			homeworld.isHQ = true;
			MarketAPI homeMarket = marketSetup.initMarket(homeworld, Factions.PLAYER);
			marketSetup.addPrimaryIndustriesToMarkets();
			marketSetup.addKeyIndustriesForFaction(Factions.PLAYER);
			marketSetup.addFurtherIndustriesToMarkets();
			homeMarket.setPlayerOwned(true);
			SectorManager.setHomeworld(homeworld.entity);
						
			StarSystemAPI system = homeworld.entity.getStarSystem();
			system.setEnteredByPlayer(true);
			Misc.setAllPlanetsSurveyed(system, true);
			for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system)) {
				market.setSurveyLevel(MarketAPI.SurveyLevel.FULL); // could also be a station, not a planet
			}
			if (!capitalsBySystem.containsKey(system))
				capitalsBySystem.put(system, homeworld);
			
			List<StarSystemAPI> sysList = Arrays.asList(new StarSystemAPI[] {system});
			spawnCommRelays(sysList);
			cleanupDerelicts(sysList);
			
			return;
		}
		
		Collections.sort(systems, new Comparator<StarSystemAPI>() {
				public int compare(StarSystemAPI sys1, StarSystemAPI sys2) {
					float desirability1 = positiveDesirabilityBySystem.get(sys1);
					float desirability2 = positiveDesirabilityBySystem.get(sys2);

					//if (desirability1 > desirability2) return -1;
					//else if (desirability2 > desirability1) return 1;
					//else return 0;
					
					if (desirability1 > 0 && desirability2 <= 0) return -1;
					else if (desirability2 > 0 && desirability1 <= 0) return 1;
					
					float dist1 = Misc.getDistance(sys1.getLocation(), ExerelinNewGameSetup.SECTOR_CENTER);
					float dist2 = Misc.getDistance(sys2.getLocation(), ExerelinNewGameSetup.SECTOR_CENTER);
					if (dist1 < dist2) return -1;
					else if (dist2 < dist1) return 1;
					else return 0;
					
				}});
		log.info("Ordered systems and their desirability: ");
		for (StarSystemAPI system : systems)
		{
			float desirability = positiveDesirabilityBySystem.get(system);
			log.info("\t" + system.getBaseName() + ": " + desirability);
		}
		
		for (int i=0; i<ExerelinSetupData.getInstance().numSystems; i++)
		{
			maybePopulatedSystems.add(systems.get(i));
		}
		
		log.info("Picking populated planets");
		pickPopulatedPlanets();
		renameSystems();
		
		List<String> popNames = new ArrayList<>();
		for (ProcGenEntity ent : populatedPlanets) popNames.add(ent.name);
		log.info("Populated planets (" + populatedPlanets.size() + "): " + popNames);
		
		log.info("Preparing stations");
		prepFreeStations();
		
		if (testOnly) return;
				
		log.info("Populating sector");
		populateSector(Global.getSector());
		
		setCapitals();
		spawnCommRelays(populatedSystems);
		surveyPlanets();
		marketSetup.addCabalSubmarkets();
		marketSetup.ensureHasSynchrotron();
		
		log.info("Cleaning up derelicts/Remnants");
		cleanupDerelicts(populatedSystems);
				
		log.info("Finishing");
		finish();
	}
		
	// =========================================================================
	// Utility functions
	
	protected SectorEntityToken createStation(ProcGenEntity station, String factionId, boolean freeStation)
	{
		float angle = NexUtilsAstro.getRandomAngle(random);
		int orbitRadius = (int)station.primary.getRadius();
		PlanetAPI planet = (PlanetAPI)station.primary;
		if (planet.isMoon())
			orbitRadius += 50;
		else if (planet.isGasGiant())
			orbitRadius += 150;
		else if (planet.isStar())
			orbitRadius += 1000;	// may be terrain; will likely be overriden
		else
			orbitRadius += 100;
		float orbitDays = NexUtilsAstro.getOrbitalPeriod(planet, orbitRadius);
		
		if (station.terrain != null) {
			switch (station.terrain.getType()) {
				case Terrain.ASTEROID_BELT:
					CampaignTerrainPlugin plugin = station.terrain.getPlugin();

					if (plugin == null) {
						log.error(String.format("Warning: Asteroid belt %s in %s has no plugin", 
								station.terrain.getName(), station.terrain.getContainingLocation()));
						break;
					}

					if (plugin instanceof AsteroidBeltTerrainPlugin) {
						AsteroidBeltTerrainPlugin abt = (AsteroidBeltTerrainPlugin)plugin;
						log.info(String.format("Creating station in asteroid belt %s in %s, has params: %s", 
								station.terrain.getName(), station.terrain.getContainingLocation(), abt.params != null));
						orbitRadius = (int)abt.params.middleRadius;
						orbitDays = (abt.params.minOrbitDays + abt.params.maxOrbitDays)/2;
					}
					else if (plugin instanceof MagicAsteroidBeltTerrainPlugin) {
						MagicAsteroidBeltTerrainPlugin abt = (MagicAsteroidBeltTerrainPlugin)plugin;
						if (abt.params == null) {
							log.error(String.format("Warning: Asteroid belt %s in %s has no params, this is caused by non-fixed versions of MagicLib", 
								station.terrain.getName(), station.terrain.getContainingLocation()));
							break;
						}
						log.info(String.format("Creating station in Magic asteroid belt %s in %s, has params: %s", 
								station.terrain.getName(), station.terrain.getContainingLocation(), abt.params != null));
						orbitRadius = (int)abt.params.middleRadius;
						orbitDays = (abt.params.minOrbitDays + abt.params.maxOrbitDays)/2;
					}
					break;
				case Terrain.RING:
					BaseRingTerrain brt = (BaseRingTerrain)station.terrain.getPlugin();
					orbitRadius = (int)brt.params.middleRadius;
					orbitDays = NexUtilsAstro.getOrbitalPeriod(planet, orbitRadius);
					break;
				case Terrain.ASTEROID_FIELD:
					orbitRadius = (int)Misc.getDistance(station.primary, station.terrain);
					orbitDays = station.terrain.getCircularOrbitPeriod();
					angle = Misc.getAngleInDegrees(station.primary.getLocation(), station.terrain.getLocation());
					break;
			}
			//log.info("Trying orbit radius for " + station.terrain.getType() + ": " + orbitRadius);
		}

		String name = station.name;
		String id = name.replace(' ','_');
		id = id.toLowerCase();
		
		int size = freeStation ? marketSetup.getWantedMarketSize(station, factionId) : planet.getMarket().getSize();
		String stationImage = NexConfig.getFactionConfig(factionId).getRandomCustomStation(size, random);
		if (stationImage == null)
			stationImage = NexUtils.getRandomListElement(STATION_IMAGES);
		
		log.info("Trying station " + station.name + " for " + factionId + ", image " + stationImage);
		SectorEntityToken newStation = station.starSystem.addCustomEntity(id, name, stationImage, factionId);
		newStation.setCircularOrbitPointingDown(planet, angle, orbitRadius, orbitDays);
		station.entity = newStation;
		
		if (!freeStation)
		{
			MarketAPI existingMarket = planet.getMarket();
			//existingMarket.addCondition("nex_recycling_plant");
			newStation.setMarket(existingMarket);
			existingMarket.getConnectedEntities().add(newStation);
			station.market = existingMarket;
			pickEntityInteractionImage(newStation, newStation.getMarket(), planet.getTypeId(), EntityType.STATION);
		}
		else
		{
			log.info("Added free station " + station.name + " for " + factionId);
			station.market = marketSetup.initMarket(station, factionId, size);
			//standaloneStations.add(data);
		}
		newStation.setCustomDescriptionId("orbital_station_default");
		
		station.entity = newStation;
		procGenEntitiesByToken.put(newStation, station);
		return newStation;
	}
	
	@Deprecated
	protected void addAvestaStation(SectorAPI sector, StarSystemAPI system)
	{
		/*
		SectorEntityToken avesta;
		
		if (ExerelinSetupData.getInstance().numSystems == 1)
		{
			SectorEntityToken toOrbit = system.getStar();
			float radius = toOrbit.getRadius();
			float orbitDistance = radius + 2000 + random.nextFloat() * 500;
			avesta = toOrbit.getContainingLocation().addCustomEntity(ExerelinConstants.AVESTA_ID, "Avesta Station", "exipirated_avesta_station", "exipirated");
			avesta.setCircularOrbitPointingDown(toOrbit, ExerelinUtilsAstro.getRandomAngle(random), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		}
		else
		{
			LocationAPI hyperspace = sector.getHyperspace();
			avesta = hyperspace.addCustomEntity(ExerelinConstants.AVESTA_ID, "Avesta Station", "exipirated_avesta_station", "exipirated");
			
			// The hyperspace station has a custom movement system
			ExipiratedAvestaMovement avestaMovementScript = new ExipiratedAvestaMovement(avesta, 60f, 3f);
			sector.getPersistentData().put("exipirated_movementScript", avestaMovementScript);
			avesta.addScript(avestaMovementScript);
		}
		avesta.setInteractionImage("illustrations", "pirate_station");

		// make sure it appends "market" for Avesta's custom interaction image handling
		MarketAPI market = Global.getFactory().createMarket("exipirated_avesta" + "_market", "Avesta Station", 5);
		market.setFactionId("exipirated");
		market.addCondition(Conditions.POPULATION_5);
		market.addIndustry(Industries.POPULATION);
		market.addIndustry(Industries.HIGHCOMMAND);
		market.addIndustry(Industries.WAYSTATION);
		market.addIndustry(Industries.STARFORTRESS_HIGH);
		market.addIndustry(Industries.HEAVYBATTERIES);
		market.addIndustry(Industries.ORBITALWORKS);
		market.setFreePort(true);
		//market.addCondition("nex_recycling_plant");
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket("exipirated_avesta_market");
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		//ExerelinMarketBuilder.addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("generator", 0.2f);
		market.getTariff().modifyMult("isFreeMarket", 0.5f);
		market.setPrimaryEntity(avesta);
		avesta.setMarket(market);
		avesta.setFaction("exipirated");
		sector.getEconomy().addMarket(market, true);
		
		avesta.addScript(new ExipiratedAvestaFleetManager(market));
		avesta.addScript(new ExipiratedPatrolFleetManager(market));
		avesta.addScript(new ExipiratedCollectorFleetManager(market));
		avesta.addScript(new ExigencyCommRelayAdder());
		*/
	}
	
	protected void addShanghai(MarketAPI market)
	{
		SectorEntityToken toOrbit = market.getPrimaryEntity();
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		SectorEntityToken shanghaiEntity = toOrbit.getContainingLocation().addCustomEntity("tiandong_shanghai", "Shanghai", "tiandong_shanghai", "tiandong");
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, NexUtilsAstro.getRandomAngle(random), orbitDistance, NexUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		
		shanghaiEntity.setMarket(market);
		market.getConnectedEntities().add(shanghaiEntity);
		// TODO: may need to add orbital station industry
		market.addSubmarket("tiandong_retrofit");
		market.removeIndustry(Industries.PATROLHQ, null, false);
		market.removeIndustry(Industries.MILITARYBASE, null, false);
		market.removeIndustry(Industries.HIGHCOMMAND, null, false);
		market.addIndustry("tiandong_merchq");
		
		toOrbit.addTag("shanghai");
		shanghaiEntity.addTag("shanghai");
		shanghaiEntity.addTag("shanghaiStation");
		shanghaiEntity.setInteractionImage("illustrations", "urban01");
		shanghaiEntity.setCustomDescriptionId("tiandong_shanghai");
	}
	
	protected void addChaosRift(StarSystemAPI system)
	{
		SectorEntityToken chaosCrack = system.addCustomEntity("chaosCrack", null, "Chaos_Crack_type", Factions.NEUTRAL);
		chaosCrack.getLocation().set(-10000f, 12000f);
		chaosCrack.addScript(new AL_ChaosCrackFleetManager(chaosCrack));
		SectorEntityToken prime_field1 = system.addTerrain(Terrain.MAGNETIC_FIELD,
		  new MagneticFieldTerrainPlugin.MagneticFieldParams(chaosCrack.getRadius() + 1000f, // terrain effect band width 
			chaosCrack.getRadius() + 1600f, // terrain effect middle radius
			chaosCrack, // entity that it's around
			chaosCrack.getRadius() + 1400f, // visual band start
			chaosCrack.getRadius() + 2400f, // visual band end
			new Color(50, 20, 100, 130), // base color
			1f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
			new Color(140, 100, 235),
			new Color(225, 255, 90),
			new Color(150, 140, 190),
			new Color(140, 190, 210),
			new Color(90, 200, 170), 
			new Color(65, 230, 160),
			new Color(20, 220, 70)
		  ));
		prime_field1.setCircularOrbit(chaosCrack, 0, 0, 100);
		SectorEntityToken prime_field2 = system.addTerrain(Terrain.MAGNETIC_FIELD,
		  new MagneticFieldTerrainPlugin.MagneticFieldParams(chaosCrack.getRadius() + 1400f, // terrain effect band width 
			chaosCrack.getRadius() + 1800f, // terrain effect middle radius
			chaosCrack, // entity that it's around
			chaosCrack.getRadius() + 3000f, // visual band start
			chaosCrack.getRadius() + 4400f, // visual band end
			new Color(50, 20, 100, 180), // base color
			1f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
			new Color(140, 100, 235),
			new Color(225, 255, 90),
			new Color(150, 140, 190),
			new Color(140, 190, 210),
			new Color(90, 200, 170), 
			new Color(65, 230, 160),
			new Color(20, 220, 70)
		  ));
		prime_field2.setCircularOrbit(chaosCrack, 0, 0, 100);
		system.addAsteroidBelt(chaosCrack, 50, 800, 200, 120, 180, Terrain.ASTEROID_BELT,null);
		system.addAsteroidBelt(chaosCrack, 300, 3600, 1200, -150, -130, Terrain.ASTEROID_BELT,null);
		system.addAsteroidBelt(chaosCrack, 800, 5500, 2400, -120, -300, Terrain.ASTEROID_BELT,null);
		system.addRingBand(chaosCrack, "misc", "rings_dust0", 256f, 3, Color.white, 256f, 800, 360f);
		system.addRingBand(chaosCrack, "misc", "rings_dust0", 256f, 3, Color.white, 256f, 1200, 360f);
		system.addRingBand(chaosCrack, "misc", "rings_dust0", 256f, 2, Color.white, 1024f, 3000, 360f);
		system.addRingBand(chaosCrack, "misc", "rings_dust0", 256f, 3, Color.white, 512f, 2000, 360f);
		system.addRingBand(chaosCrack, "misc", "rings_dust0", 256f, 2, Color.white, 512f, 4000, 360f);
		system.addRingBand(chaosCrack, "misc", "rings_dust0", 256f, 2, Color.white, 512f, 6000, 360f);
        SectorEntityToken primeNebula = Misc.addNebulaFromPNG("data/campaign/terrain/agustin_prime_nebula.png",
          chaosCrack.getLocation().x, chaosCrack.getLocation().y,
                system,
                "terrain", "AL_primenebula",
                4, 4, "AL_primenebula", StarAge.ANY);
        primeNebula.addTag("radar_nebula");
	}

	// if this doesn't compile, try getting ApproLight 1.10 or later
	protected void addUnos(MarketAPI market)
	{
		SectorEntityToken toOrbit = market.getPrimaryEntity();
		SectorEntityToken unosStation = toOrbit.getContainingLocation().addCustomEntity("AL_unosStation", null, "station_unos_type", "approlight");
		unosStation.setCircularOrbitPointingDown(toOrbit, 225.0F, 500.0F, 60.0F);
		unosStation.setCustomDescriptionId("AL_unosStation");
		unosStation.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "ApproLight_unos_music");
	}
	
	protected void addAntiochPart2(SectorAPI sector)
	{
		TEM_Antioch.generatePt2(sector);
	}
	
	protected void handleHQSpecials(SectorAPI sector, String factionId, ProcGenEntity data)
	{
		if (factionId.equals("exipirated") && NexConfig.enableAvesta)
			addAvestaStation(sector, data.starSystem);
		if (factionId.equals("tiandong"))
			addShanghai(data.market);
		if (factionId.equals("approlight"))
		{
			if (NexConfig.enableUnos)
				addUnos(data.market);
			addChaosRift(data.starSystem);	// TODO: give it its own option?
			data.market.removeSubmarket(Submarkets.GENERIC_MILITARY);
			data.market.addSubmarket("AL_militaryMarket");
		}
	}
	
	protected int countMarketsInSystemForFaction(LocationAPI loc, String factionId)
	{
		int count = 0;
		for (PlanetAPI planet : loc.getPlanets())
		{
			if (planet.getFaction().getId().equals(factionId))
				count++;
		}
		for (SectorEntityToken station : loc.getEntitiesWithTag(Tags.STATION))
		{
			if (station.getFaction().getId().equals(factionId))
				count++;
		}
		
		return count;
	}
	
	protected float getDistScore(ProcGenEntity candidate, List<ProcGenEntity> existingHQs)
	{
		float score = 0;
		SectorEntityToken ent = candidate.entity;
		if (ent == null) ent = candidate.primary;
		for (ProcGenEntity hq : existingHQs)
		{
			if (ent.getContainingLocation() == hq.entity.getContainingLocation())
				return 0.0001f;
			
			float distSq = MathUtils.getDistanceSquared(ent.getLocationInHyperspace(), hq.entity.getLocationInHyperspace());
			score += Math.pow(distSq, 0.25f);
		}
		return score;
	}
	
	/**
	 * Picks a planet that is as far from existing HQ planets as possible.
	 * Uses most-square-roots approach, and applies a minimum score for planets 
	 * sharing a system with an existing HQ.
	 * @param candidates Possible planets for a HQ
	 * @param existingHQs
	 * @return
	 */
	protected ProcGenEntity pickHQ(List<ProcGenEntity> candidates, List<ProcGenEntity> existingHQs)
	{
		if (existingHQs.isEmpty())
			return candidates.get(0);
		List<ProcGenEntity> sorted = new ArrayList<>(candidates);		
		final Map<ProcGenEntity, Float> distScores = new HashMap<>();
		
		for (ProcGenEntity candidate : candidates)
		{
			distScores.put(candidate, getDistScore(candidate, existingHQs));
		}
		Collections.sort(sorted, new Comparator<ProcGenEntity>()
            {
                @Override
                public int compare(ProcGenEntity ent1, ProcGenEntity ent2)
                {
                    return distScores.get(ent1).compareTo(distScores.get(ent2));
                }
            });
		return sorted.get(sorted.size() - 1);
	}
	
	/**
	 * Pick a random market-to-be from the available list. 
	 * Prefers markets close to the faction's HQ, and those in star systems where faction already has a presence.
	 * Just picks the first market in the list if this is a pirate or independent faction.
	 * @param factionId
	 * @param candidates
	 * @param hq The faction's current HQ planet
	 * @param alreadyPresent List of star systems where we already have a presence
	 * @return
	 */
	@Deprecated
	protected ProcGenEntity pickRandomMarketCloseToHQ(String factionId, List<ProcGenEntity> candidates, 
			ProcGenEntity hq, Collection<LocationAPI> alreadyPresent)
	{
		if (NexConfig.getFactionConfig(factionId).pirateFaction || factionId.equals(Factions.INDEPENDENT))
			return candidates.get(0);
		
		WeightedRandomPicker<ProcGenEntity> picker = new WeightedRandomPicker<>(random);
		
		// crash safety
		Vector2f hqLoc;
		if (hq == null)
			hqLoc = new Vector2f(0, 0);
		else 
			hqLoc = hq.entity.getContainingLocation().getLocation();
		for (ProcGenEntity candidate : candidates)
		{
			Vector2f loc = candidate.starSystem.getLocation();
			
			float weight = 10000 - MathUtils.getDistance(loc, hqLoc);
			if (weight > 4900) weight = 4900;
			if (weight < 100) weight = 100;
			weight = (float)Math.sqrt(weight);
			
			if (alreadyPresent.contains(candidate.starSystem))
			{
				float existingCount = countMarketsInSystemForFaction(candidate.starSystem, factionId);
				//log.info("System " + candidate.starSystem.getBaseName() + " already has " + existingCount + " markets for " + factionId);
				float weightMult = 4 - existingCount;
				if (weightMult < 0.5) weightMult = 0.5f;
				weight *= weightMult;
			}
			picker.add(candidate, weight);
		}
		return picker.pick();
	}
	
	protected ProcGenEntity pickMarketCloseToHQ(String factionId, List<ProcGenEntity> candidates, 
			ProcGenEntity hq, Collection<LocationAPI> alreadyPresent)
	{
		if (NexConfig.getFactionConfig(factionId).pirateFaction || factionId.equals(Factions.INDEPENDENT))
			return candidates.get(0);
		
		// crash safety
		Vector2f hqLoc;
		if (hq == null)
			hqLoc = new Vector2f(0, 0);
		else 
			hqLoc = hq.entity.getContainingLocation().getLocation();
		
		
		ProcGenEntity best = null;
		float bestScore = 0;
		
		for (ProcGenEntity candidate : candidates)
		{
			Vector2f loc = candidate.starSystem.getLocation();
			
			float score = 20000/MathUtils.getDistance(loc, hqLoc);
			score = (float)Math.sqrt(score);
			
			if (alreadyPresent.contains(candidate.starSystem))
			{
				// code to stop trying to fill a system with a faction's markets if we already have a lot of those
				// don't do it, go ahead and pool, most vanilla systems are like this
				/*
				float existingCount = countMarketsInSystemForFaction(candidate.starSystem, factionId);
				//log.info("System " + candidate.starSystem.getBaseName() + " already has " + existingCount + " markets for " + factionId);
				float existingMult = 4 - existingCount;
				if (existingMult < 0.5) existingMult = 0.5f;
				score *= existingMult;
				*/
			}
			
			if (score > bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}
	
	/**
	 * Assigns markets to factions
	 * @param sector
	 */
	public void populateSector(SectorAPI sector)
	{
		// faction picker
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(random);
		List<String> factions = new ArrayList<>(factionIds);
		boolean antioch = ExerelinSetupData.getInstance().randomAntiochEnabled;
		if (antioch)
		{
			factions.remove("templars");
		}
		factionPicker.addAll(factions);
		
		if (factionPicker.isEmpty()) {
			throw new RuntimeException("No factions available for sector generation");
		}
		
		// various Collections we'll be using
		Map<String, Integer> factionPlanetCount = new HashMap<>();
		Map<String, Integer> factionStationCount = new HashMap<>();
		List<ProcGenEntity> populatedPlanetsCopy = new LinkedList<>(populatedPlanets);
		List<ProcGenEntity> stationsCopy = new LinkedList<>(stations);
		List<String> pirateFactions = new ArrayList<>();
		
		List<StarSystemAPI> systemsWithPirates = new ArrayList<>();
		List<ProcGenEntity> existingHQs = new ArrayList<>();
		Map<String, ProcGenEntity> existingHQsByFaction = new HashMap<>();
		Map<String, Set<LocationAPI>> populatedSystemsByFaction = new HashMap<>();
		
		for (String factionId : factions) {
			// check for markets the faction already has
			int planetCount = 0, stationCount = 0;
			for (MarketAPI market : NexUtilsFaction.getFactionMarkets(factionId, true)) {
				if (market.getPlanetEntity() == null) stationCount++;
				else planetCount++;
			}
			
			factionPlanetCount.put(factionId, planetCount);
			factionStationCount.put(factionId, stationCount);
			populatedSystemsByFaction.put(factionId, new HashSet<LocationAPI>());
			
			if (NexUtilsFaction.isPirateFaction(factionId))
				pirateFactions.add(factionId);
		}
		
		
		/*
		if (ExerelinSetupData.getInstance().freeStart)
		{
			// give the homeworld to a random faction while in free start mode, to avoid desyncing the RNG
			// doesn't seem to actually work...
			alignedFactionId = factionPicker.pick();
		}
		factionPicker.remove(alignedFactionId);
		*/
		
		String alignedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		NexFactionConfig factionConf = NexConfig.getFactionConfig(alignedFactionId);
		String spawnAsFactionId = factionConf.spawnAsFactionId;
		if (spawnAsFactionId != null) alignedFactionId = spawnAsFactionId;
		
		// before we do anything else give the "homeworld" to our faction
		// don't pick homeworld now if we require player to start in a non-populated system
		if (setupData.homeworldPickMode != HomeworldPickMode.NON_CORE) {
			pickHomeworld();
		}		
		if (!ExerelinSetupData.getInstance().freeStart)	// (true)
		{
			if (alignedFactionId.equals("templars") && antioch)
			{
				// do nothing, Ascalon will be our only world
			}
			else if (homeworld != null)
			{
				homeworld.isHQ = true;
				MarketAPI homeMarket = marketSetup.initMarket(homeworld, alignedFactionId);
				//SectorEntityToken relay = sector.getEntityById(systemToRelay.get(homeworld.starSystem.getId()));
				//relay.setFaction(alignedFactionId);
				populatedPlanetsCopy.remove(homeworld);

				handleHQSpecials(sector, alignedFactionId, homeworld);

				if (pirateFactions.contains(alignedFactionId))
					systemsWithPirates.add(homeworld.starSystem);
				NexUtils.modifyMapEntry(factionPlanetCount, alignedFactionId, 1);

				existingHQs.add(homeworld);
				existingHQsByFaction.put(alignedFactionId, homeworld);
				
				if (alignedFactionId.equals(Factions.PLAYER)) {
					homeMarket.setPlayerOwned(true);
				}
			}
		}
		
		// Antioch
		if (antioch && factionIds.contains("templars"))
		{
			addAntiochPart2(sector);
			NexUtils.modifyMapEntry(factionPlanetCount,"templars", 1);
		}
		
		Collections.sort(populatedPlanetsCopy, DESIRABILITY_COMPARATOR);
		Collections.shuffle(populatedPlanetsCopy, random);
		Collections.shuffle(stationsCopy, random);
		
		// This is the list of entities to add markets to
		// needs to be a List instead of a Set for shuffling
		List<ProcGenEntity> unassignedEntities = new LinkedList<>(populatedPlanetsCopy);
		for (ProcGenEntity station : stationsCopy) {
			unassignedEntities.add(station);
		}
		
		Set<ProcGenEntity> toRemove = new HashSet<>();
		
		// assign HQ worlds
		for (String factionId : factions)
		{
			if (factionId.equals(alignedFactionId)) continue;
			if (factionPlanetCount.get(factionId) > 0) continue;	// faction that spawns its own planets even in random sector
			if (populatedPlanetsCopy.size() <= 0) {
				log.info("No populated planets remaining, break");
				break;
			}
			log.info("Processing faction HQ for " + factionId);
			
			ProcGenEntity hq = pickHQ(populatedPlanetsCopy, existingHQs);
			populatedPlanetsCopy.remove(hq);
			
			NexFactionConfig config = NexConfig.getFactionConfig(factionId);
			if (!config.noHomeworld)
				hq.isHQ = true;
			
			marketSetup.initMarket(hq, factionId);
			handleHQSpecials(sector, factionId, hq);
			
			if (pirateFactions.contains(factionId))
				systemsWithPirates.add(hq.starSystem);
			NexUtils.modifyMapEntry(factionPlanetCount, factionId, 1);
			
			unassignedEntities.remove(hq);
			existingHQs.add(hq);
			existingHQsByFaction.put(factionId, hq);
			
			if (factionId.equals(spawnAsFactionId))
				homeworld = hq;
		}
		
		// ensure pirate presence in most star systems
		if (!pirateFactions.isEmpty())
		{
			WeightedRandomPicker<String> piratePicker = new WeightedRandomPicker<>(random);

			Collections.shuffle(unassignedEntities, random);
			for (ProcGenEntity entity : unassignedEntities)
			{
				if (systemsWithPirates.size() == populatedSystems.size())	// all systems already have pirates
					break;
				
				if (systemsWithPirates.contains(entity.starSystem))
					continue;
				
				if (random.nextFloat() > NexConfig.forcePiratesInSystemChance) {
					systemsWithPirates.add(entity.starSystem);	// don't actually have pirates, but pretend we do to skip over it
					continue;
				}

				if (piratePicker.isEmpty())
					piratePicker.addAll(pirateFactions);

				String factionId = piratePicker.pickAndRemove();
				
				if (entity.type == EntityType.PLANET || entity.type == EntityType.MOON)
				{
					marketSetup.initMarket(entity, factionId);
					populatedPlanetsCopy.remove(entity);
					NexUtils.modifyMapEntry(factionPlanetCount, factionId, 1);
				}
				else
				{
					createStation(entity, factionId, true);
					stationsCopy.remove(entity);
					NexUtils.modifyMapEntry(factionStationCount, factionId, 1);
				}
				toRemove.add(entity);
				systemsWithPirates.add(entity.starSystem);
				populatedSystemsByFaction.get(factionId).add(entity.starSystem);
			}
			unassignedEntities.removeAll(toRemove);
		}
		
		// assign remaining planets, with shares based on spawn weight
		Map<String, Float> factionShare = new HashMap<>();
		float totalShare = 0;
		for (String factionId : factions) {
			float share = 1;
			if (setupData.useFactionWeights) {
				if (setupData.randomFactionWeights) {
					share = (float)(0.5f + 0.5f * random.nextFloat() + 0.5f * random.nextFloat());
				} else {
					share = NexConfig.getFactionConfig(factionId).marketSpawnWeight;
				}
			}
			
			totalShare += share;
			factionShare.put(factionId, share);
		}
		
		int remainingPlanets = populatedPlanetsCopy.size();
		for (String factionId : factions) {
			int numPlanets = (int)(remainingPlanets * (factionShare.get(factionId)/totalShare) + 0.5);
			for (int i=factionPlanetCount.get(factionId); i<numPlanets; i++)
			{
				if (populatedPlanetsCopy.isEmpty()) break;
				
				ProcGenEntity habitable = pickMarketCloseToHQ(factionId, populatedPlanetsCopy, 
						existingHQsByFaction.get(factionId), populatedSystemsByFaction.get(factionId));
				populatedPlanetsCopy.remove(habitable);
				unassignedEntities.remove(habitable);
				marketSetup.initMarket(habitable, factionId);
				NexUtils.modifyMapEntry(factionPlanetCount, factionId, 1);
				
				populatedSystemsByFaction.get(factionId).add(habitable.starSystem);
			}
			if (populatedPlanetsCopy.isEmpty()) {
				break;
			}
		}
		
		// dole out any unassigned planets at random, with Tetris rotation for factions
		for (ProcGenEntity planet : populatedPlanetsCopy)
		{
			if (planet.market != null && !planet.market.isPlanetConditionMarketOnly())
			{
				log.error("Unassigned entity " + planet.name + " already has market!");
				continue;
			}
			
			if (factionPicker.isEmpty())
				factionPicker.addAll(factions);
			String factionId = factionPicker.pickAndRemove();
			
			marketSetup.initMarket(planet, factionId);
			unassignedEntities.remove(planet);
			populatedSystemsByFaction.get(factionId).add(planet.starSystem);
		}
		
		// assign stations		
		int remainingStations = stationsCopy.size();
		for (String factionId : factions) {
			int numStations = (int)(remainingStations * (factionShare.get(factionId)/totalShare) + 0.5);
			for (int i=factionStationCount.get(factionId); i<numStations; i++)
			{
				ProcGenEntity station = pickMarketCloseToHQ(factionId, stationsCopy, 
						existingHQsByFaction.get(factionId), populatedSystemsByFaction.get(factionId));
				stationsCopy.remove(station);
				unassignedEntities.remove(station);
				createStation(station, factionId, true);
				NexUtils.modifyMapEntry(factionStationCount, factionId, 1);
				populatedSystemsByFaction.get(factionId).add(station.starSystem);
				
				if (stationsCopy.isEmpty()) break;
			}
			if (stationsCopy.isEmpty()) break;
		}
		
		// dole out any unassigned stations
		for (ProcGenEntity station : stationsCopy)
		{
			if (station.market != null)
			{
				log.error("Unassigned entity " + station.name + " already has market!");
				continue;
			}
			
			if (factionPicker.isEmpty())
				factionPicker.addAll(factions);
			String factionId = factionPicker.pickAndRemove();
			createStation(station, factionId, true);
			populatedSystemsByFaction.get(factionId).add(station.starSystem);
		}
		
		// add basic industries to each entity
		marketSetup.addPrimaryIndustriesToMarkets();
		
		// add key industries for each faction
		for (String factionId : factions)
		{
			marketSetup.addKeyIndustriesForFaction(factionId);
		}
		
		// more industries
		marketSetup.addFurtherIndustriesToMarkets();
		
		// add faction bonus items
		for (String factionId : factions)
		{
			marketSetup.addFactionBonuses(factionId);
		}
		
		// over-industry warnings
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			int num = Misc.getNumIndustries(market), max = Misc.getMaxIndustries(market);
			if (num > max) {
				log.warn(market.getName() + " exceeding industry limit (" + num + "/" + max + ")");
			}
		}
		
		// tags
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			StarSystemAPI sys = market.getStarSystem();
			if (sys == null) continue;
			sys.addTag(ExerelinProcGen.RANDOM_CORE_SYSTEM_TAG);
			sys.addTag(Tags.THEME_CORE_POPULATED);
			sys.removeTag(Tags.THEME_CORE_UNPOPULATED);
			sys.addTag(Tags.THEME_CORE);
		}
		
		// end distribution of markets and stations
	}
	
	protected void finish()
	{
		if (homeworld != null) SectorManager.setHomeworld(homeworld.entity);
		
		SectorManager.reinitLiveFactions();
		DiplomacyManager.initFactionRelationships(false);
	}
	
	public static class ProcGenEntity {
		public String name = "";
		public SectorEntityToken entity;
		public String planetType = "";
		public float desirability = 0;
		public boolean inhabited = true;
		public boolean isCapital = false;	// merely used for internal tagging; it's not set at the time market generation occurs
		public boolean isHQ = false;
		public EntityType type = EntityType.PLANET;
		public StarSystemAPI starSystem;
		public SectorEntityToken primary;
		public CampaignTerrainAPI terrain;	// for stations
		public MarketAPI market;
		public int forceMarketSize = -1;
		//public float orbitRadius = 0;
		//public float orbitPeriod = 0;
		@Deprecated public int marketPoints = 0;
		@Deprecated public int marketPointsSpent = 0;
		@Deprecated public int bonusMarketPoints = 0;
		public int numProductiveIndustries = 0;
		public int numBonuses = 0;
		
		public ProcGenEntity(SectorEntityToken entity) 
		{
			this.entity = entity;
		}
	}
	
	public enum EntityType {
		STAR, PLANET, MOON, STATION
	}
	
	protected float divideWithDiv0Protection(float f1, float f2)
	{
		if (f2 == 0)
			return 0;
		return f1/f2;
	}
	
	protected static final Comparator<ProcGenEntity> DESIRABILITY_COMPARATOR = new Comparator<ProcGenEntity>() {
		public int compare(ProcGenEntity e1, ProcGenEntity e2) {
			float desirability1 = e1.desirability;
			float desirability2 = e2.desirability;

			if (desirability1 > desirability2) return -1;
			else if (desirability2 > desirability1) return 1;
			else return e1.name.compareTo(e2.name);
		}};
}
