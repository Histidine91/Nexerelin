package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NameAssigner;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantStationFleetManager;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.AL_ChaosCrackFleetManager;
import data.scripts.campaign.ExigencyCommRelayAdder;
import data.scripts.world.exipirated.ExipiratedAvestaFleetManager;
import data.scripts.world.exipirated.ExipiratedAvestaMovement;
import data.scripts.world.exipirated.ExipiratedCollectorFleetManager;
import data.scripts.world.exipirated.ExipiratedPatrolFleetManager;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import static exerelin.world.ExerelinNewGameSetup.log;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;


public class ExerelinProcGen {
	
	public static final float CORE_WIDTH = 15000;
	public static final float CORE_HEIGHT = 12000;
	public static final int CORE_RECURSION_MAX_DEPTH = 6;
	public static final Set<String> ALLOWED_STATION_TERRAIN = new HashSet<>(Arrays.asList(new String[] {
		Terrain.ASTEROID_BELT, Terrain.ASTEROID_FIELD, Terrain.RING
	}));
	
	protected static final String PLANET_NAMES_FILE = "data/config/exerelin/planetNames.json";
	protected static List<String> stationNames = new ArrayList<>();
	
	public static final List<String> stationImages = new ArrayList<>(Arrays.asList(new String[] {
		"station_side00", "station_side02", "station_side04", "station_jangala_type"
	}));
	public static final Set<String> TAGS_TO_REMOVE = new HashSet<>(Arrays.asList(new String[] {
		Tags.THEME_DERELICT, Tags.THEME_DERELICT_MOTHERSHIP, Tags.THEME_DERELICT_PROBES, Tags.THEME_DERELICT_SURVEY_SHIP,
		Tags.THEME_REMNANT, Tags.THEME_REMNANT_DESTROYED, Tags.THEME_REMNANT_MAIN, Tags.THEME_REMNANT_RESURGENT, Tags.THEME_REMNANT_SECONDARY, Tags.THEME_REMNANT_SUPPRESSED
	}));
	
	protected Set<String> factionIds = new HashSet<>();
	protected List<StarSystemAPI> systems = new ArrayList<>();
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
	
	protected Map<String, String> systemToRelay = new HashMap<>();
	protected Map<String, String> planetToRelay = new HashMap<>();
	
	protected ProcGenEntity homeworld;
	
	protected ExerelinSetupData setupData;
	protected ExerelinMarketBuilder marketSetup;
	
	protected Random random;
	
	
	static {
		loadData();
	}
	
	protected static void loadData()
	{
		try {
			JSONObject planetConfig = Global.getSettings().loadJSON(PLANET_NAMES_FILE);
			
			JSONArray stationNames = planetConfig.getJSONArray("stations");
			ExerelinProcGen.stationNames = ExerelinUtils.JSONArrayToArrayList(stationNames);
		} catch (JSONException | IOException ex) {
			log.error(ex);
		}
	}
		
	protected Set<String> getStartingFactions()
	{
		Set<String> factions = new HashSet<>();
		for (Map.Entry<String, Boolean> tmp : ExerelinSetupData.getInstance().factions.entrySet())
		{
			String factionId = tmp.getKey();
			if (tmp.getValue())
			{
				factions.add(factionId);
				log.info("Added starting faction: " + factionId);
			}
		}
		String playerFaction = PlayerFactionStore.getPlayerFactionIdNGC();
		if (!playerFaction.equals(ExerelinConstants.PLAYER_NPC_ID))
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
		
		if(market.hasCondition(Conditions.URBANIZED_POLITY) || largeMarket)
		{
			if (!factionId.equals(Factions.LUDDIC_PATH))
			{
				allowedImages.add(new String[]{"illustrations", "urban00"} );
				allowedImages.add(new String[]{"illustrations", "urban01"} );
				allowedImages.add(new String[]{"illustrations", "urban02"} );
				allowedImages.add(new String[]{"illustrations", "urban03"} );
				allowedImages.add(new String[]{"illustrations", "corporate_lobby"} );
			}
			
			if (ExerelinUtilsFaction.doesFactionExist("citadeldefenders"))
			{
				allowedImages.add(new String[]{"illustrationz", "streets"} );
				if (!isStation) allowedImages.add(new String[]{"illustrationz", "twin_cities"} );
			}
			if (!isStation)
			{
				allowedImages.add(new String[]{"illustrations", "eochu_bres"} );
			}
		}
		if (!isStation && market.hasCondition(Conditions.ORE_COMPLEX))
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
		if (market.hasCondition(Conditions.LUDDIC_MAJORITY))
			allowedImages.add(new String[]{"illustrations", "luddic_shrine"} );
		
		//if (isMoon)
		//	allowedImages.add(new String[]{"illustrations", "asteroid_belt_moon"} );
		if (planetType.equals("desert") && isMoon)
			allowedImages.add(new String[]{"illustrations", "desert_moons_ruins"} );

		String[] illustration = allowedImages.pick();
		entity.setInteractionImage(illustration[0], illustration[1]);
	}
	
	protected ProcGenEntity getHomeworld()
	{
		return homeworld;
	}
	
	/**
	 * Returns whether the "core systems" are sufficient in number and have enough desirable planets
	 * @param systems List of current core systems
	 * @return
	 */
	protected boolean validateCoreSystems(List<StarSystemAPI> systems)
	{
		if (systems.size() < setupData.numSystems)
			return false;
		
		int numDesirables = 0;
		for (StarSystemAPI system : systems)
		{
			int count = 0;
			for (PlanetAPI planet : system.getPlanets())
			{
				if (getDesirability(planet) <= 0) continue;
				count++;
				numDesirables++;
				if (count >= setupData.maxPlanetsPerSystem) break; 
			}
		}
		return numDesirables >= setupData.numPlanets;
	}
	
	/**
	 * Get systems close to the Sector's center
	 * Widens search if number of systems/planets contained is less than number of systems/planets wanted
	 * @param width Width of search area
	 * @param height Height of search area
	 * @param recursionDepth How many times this method has recursed; if too high, stop expanding search area
	 * @return
	 */
	protected List<StarSystemAPI> getCoreSystems(float width, float height, int recursionDepth)
	{
		List<StarSystemAPI> list = new ArrayList<>();
		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{
			Vector2f loc = system.getLocation();
			if (Math.abs(loc.x - ExerelinNewGameSetup.SECTOR_CENTER.x) > width) continue;
			if (Math.abs(loc.y - ExerelinNewGameSetup.SECTOR_CENTER.y) > height) continue;
			if (system.hasPulsar()) continue;
			if (system.getStar().getSpec().isBlackHole()) continue;
			if (system.getBaseName().equals("Styx")) continue;
			
			list.add(system);
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
			case Conditions.ORE_ABUNDANT:
			case Conditions.RARE_ORE_ABUNDANT:
			case Conditions.ORGANICS_ABUNDANT:
			case Conditions.VOLATILES_ABUNDANT:
				return 0.3f;
			case Conditions.ORE_RICH:
			case Conditions.RARE_ORE_RICH:
			case Conditions.ORGANICS_PLENTIFUL:
			case Conditions.VOLATILES_PLENTIFUL:
				return 0.6f;
			case Conditions.ORE_ULTRARICH:
			case Conditions.RARE_ORE_ULTRARICH:
				return 1f;
			case Conditions.FARMLAND_ADEQUATE:
				return 0.2f;
			case Conditions.FARMLAND_RICH:
				return 0.35f;
			case Conditions.FARMLAND_BOUNTIFUL:
				return 0.6f;
		}
		if (cond.getGenSpec() != null)
		{
			float hazard = cond.getGenSpec().getHazard();
			if (hazard >= 0.5f)
				return -0.4f;
			else if (hazard >= 0.25f)
				return -0.2f;
		}
		return 0;
	}
	
	/**
	 * Get how "desirable" the planet is based on its market conditions
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
		planetDesirabilityCache.put(planet, desirability);
		
		return desirability;
	}
	
	/**
	 * Creates a ProcGenEntity for the specified planet
	 * @param planet
	 * @return
	 */
	protected ProcGenEntity createEntityDataForPlanet(PlanetAPI planet)
	{
		ProcGenEntity data = new ProcGenEntity(planet);
		data.name = planet.getName();
		data.type = planet.isMoon() ? EntityType.MOON : EntityType.PLANET;
		data.market = planet.getMarket();
		data.desirability = getDesirability(planet);
		data.planetType = planet.getTypeId();
		data.primary = planet.getOrbitFocus();
		data.starSystem = (StarSystemAPI)planet.getContainingLocation();
		
		return data;
	}
	
	/**
	 * Creates ProcGenEntities for planets in the star system
	 * @param system
	 */
	protected void createEntityDataForSystem(StarSystemAPI system)
	{
		if (!system.isProcgen()) return;
		for (PlanetAPI planet : system.getPlanets())
		{
			if (planet.isStar()) continue;
			if (planet.isGasGiant()) continue;
			
			//log.info("Creating entity data for planet " + planet.getName());
			ProcGenEntity planetData = createEntityDataForPlanet(planet);
			procGenEntitiesByToken.put(planet, planetData);
			planets.add(planetData);
			//log.info("\tPlanet desirability: " + planetData.desirability);
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
	 * Fills the populatedPlanets list with planets
	 * Will skip a star system if it already has too many populated planets
	 * @param picker Random picker
	 * @param planets List of candidate planets to be populated
	 */
	protected void pickPopulatedPlanets(WeightedRandomPicker<ProcGenEntity> picker, Collection<ProcGenEntity> planets)
	{
		picker.clear();
		for (ProcGenEntity planet: planets)
		{
			// already picked before?
			if (populatedPlanets.contains(planet))
				continue;
			
			float weight = Math.max(planet.desirability + 1, 0.2f);
			if (populatedSystems.contains(planet.starSystem))
				weight *= 99;	// strongly prefer already inhabited systems
			picker.add(planet, weight);
		}
		
		while (populatedPlanets.size() < setupData.numPlanets && !picker.isEmpty())
		{
			ProcGenEntity candidate = picker.pickAndRemove();
			int numMarketsInSystem = marketsBySystem.get(candidate.starSystem).size();
			if (numMarketsInSystem >= setupData.maxPlanetsPerSystem)
			{
				continue;
			}
			
			// don't populate a moon if our primary is already populated and nicer
			// if we're nicer than primary, remove that instead
			if (candidate.type == EntityType.MOON)
			{
				ProcGenEntity primary = procGenEntitiesByToken.get(candidate.primary);
				if (primary != null)
				{
					if (primary.desirability >= candidate.desirability)
						continue;
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
						shouldSkip = true;
						continue;
					}
				}
				
				if (shouldSkip) continue;
				else
					for (ProcGenEntity moon : populatedMoons) removePopulatedPlanet(moon);
			}
			
			//log.info("Populating planet " + candidate.name + "(desirability " + candidate.desirability + ")");
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
		pickPopulatedPlanets(picker, habitablePlanets);
		log.info("Picking other desirable planets: " + desirableNotHabitable.size());
		pickPopulatedPlanets(picker, desirableNotHabitable);
		//pickPopulatedPlanets(picker, desirablePlanets);
		log.info("Picking undesirable planets: " + notDesirable.size());
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
	
	public String getStationName(SectorEntityToken target)
	{
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		picker.addAll(stationNames);
		String name = target.getName();
		if (target instanceof CampaignTerrainAPI)
		{
			//CampaignTerrainAPI terrain = (CampaignTerrainAPI) target;
			name = target.getContainingLocation().getName();
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
		data.name = getStationName(data.primary);
		data.archetype = marketSetup.pickArchetypeForStation(data);
		if (target instanceof PlanetAPI)
		{
			data.planetType = ((PlanetAPI)target).getTypeId();
		}
		alreadyUsedStationNames.add(data.name);
		
		return data;
	}
	
	/**
	 * Finds suitable locations for free stations and creates ProcGenEntities for them, so they may be created later
	 * Don't create them right now, as we haven't picked factions for them yet
	 */
	protected void prepFreeStations()
	{
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		for (StarSystemAPI system : populatedSystems)
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
					if (entity != null && populatedPlanets.contains(entity) && 
							MathUtils.isWithinRange(planet, maybeNearbyPlanet, 2000))
					{
						allow = false;
						break;
					}
				}
				if (allow) picker.add(planet);
			}
			for (CampaignTerrainAPI terrain : system.getTerrainCopy())
			{
				if (ALLOWED_STATION_TERRAIN.contains(terrain.getId()))
				{
					log.info(terrain);
					picker.add(terrain);
				}
			}
		}
		
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
		}
	}
	
	/**
	 * Picks the "homeworld" (player faction's HQ) from the most desirable planets
	 * @return
	 */
	protected ProcGenEntity pickHomeworld()
	{
		List<ProcGenEntity> candidates = new ArrayList<>(populatedPlanets);
		Collections.sort(candidates, new Comparator<ProcGenEntity>() {
				public int compare(ProcGenEntity e1, ProcGenEntity e2) {
					float desirability1 = e1.desirability;
					float desirability2 = e2.desirability;

					if (desirability1 > desirability2) return -1;
					else if (desirability2 > desirability1) return 1;
					else return 0;
				}});
		
		WeightedRandomPicker<ProcGenEntity> picker = new WeightedRandomPicker<>(random);
		for (int i=0; i<candidates.size(); i++)
		{
			if (i == 5) break;
			picker.add(candidates.get(i));
		}
		homeworld = picker.pick();
		return homeworld;
	}
	
	/**
	 * Spawns comm relays in each star system, or converts existing ones made by procgen
	 */
	protected void spawnCommRelays()
	{
		for (StarSystemAPI system : populatedSystems)
		{
			SectorEntityToken relay = null;
			ProcGenEntity capital = capitalsBySystem.get(system);
			if (capital == null) continue;
			
			// see if there are existing relays we can co-opt
			for (SectorEntityToken relayCandidate : system.getEntitiesWithTag(Tags.COMM_RELAY))
			{
				// only one relay per system
				if (relay != null)
					system.removeEntity(relayCandidate);
				else
				{
					relay = relayCandidate;
					relay.setFaction(capital.market.getFactionId());
					relay.getMemoryWithoutUpdate().unset(MemFlags.COMM_RELAY_NON_FUNCTIONAL);
				}
			}
			
			// else make our own relay
			if (relay == null)
			{
				log.info("Creating comm relay for system " + system.getName());
				relay = system.addCustomEntity(system.getId() + "_relay", // unique id
					system.getBaseName() + " Relay", // name - if null, defaultName from custom_entities.json will be used
					"comm_relay", // type of object, defined in custom_entities.json
					capital.entity.getFaction().getId()); // faction
				
				List<SectorEntityToken> jumpPoints = system.getJumpPoints();

				int lp = 4;
				if (random.nextBoolean()) lp = 5;
				
				SectorEntityToken capEntity = capital.entity;
				if (capital.type == EntityType.STATION)
					capEntity = capEntity.getOrbitFocus();
				if (capEntity instanceof PlanetAPI && ((PlanetAPI)capEntity).isMoon()) 
					capEntity = capEntity.getOrbitFocus();
				
				SectorEntityToken systemPrimary = capEntity.getOrbitFocus();
				if (systemPrimary != null)	// if null, maybe it's a nebula?
				{
					float orbitRadius = ExerelinUtilsAstro.getCurrentOrbitRadius(capEntity, systemPrimary);
					float startAngle = ExerelinUtilsAstro.getCurrentOrbitAngle(capEntity, systemPrimary);
					
					ExerelinUtilsAstro.setLagrangeOrbit(relay, systemPrimary, capEntity, 
						lp, startAngle, orbitRadius, 0, capEntity.getOrbit().getOrbitalPeriod(), 
						false, 0, 1, 1, 0);
					
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
							ExerelinUtilsAstro.setLagrangeOrbit(relay, systemPrimary, capEntity, 
								lp, startAngle, orbitRadius, 0, capEntity.getOrbit().getOrbitalPeriod(), 
								false, 0, 1, 1, 0);
							break;
						}
					}
				}
			}
			
			systemToRelay.put(system.getId(), relay.getId());
			planetToRelay.put(capital.entity.getId(), relay.getId());
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
	 * When the capital is captured, the relay changes owner
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
			capitalsBySystem.put(system, capital);
		}
	}
	
	public static void cleanupDerelicts(Collection<StarSystemAPI> systems)
	{
		LocationAPI hyper = Global.getSector().getHyperspace();
		List<SectorEntityToken> toRemove = new ArrayList<>();
		for (StarSystemAPI system : systems)
		{
			log.info("Cleaning up system " + system.getName());
			ExerelinUtils.removeScriptAndListener(system, RemnantStationFleetManager.class, null);
			ExerelinUtils.removeScriptAndListener(system, RemnantSeededFleetManager.class, null);
			
			for (SectorEntityToken token : system.getAllEntities())
			{
				if (token.hasTag(Tags.GATE) || token.hasTag(Tags.DEBRIS_FIELD)) continue;
				if (token.getFaction().getId().equals(Factions.DERELICT) || token.getFaction().getId().equals(Factions.REMNANTS))
					toRemove.add(token);
				else if (token.hasTag(Tags.SALVAGEABLE))
					toRemove.add(token);
			}
			
			for (String tag : TAGS_TO_REMOVE)
			{
				system.removeTag(tag);
			}
			system.addTag(Tags.THEME_CORE_POPULATED);
		}
		for (SectorEntityToken beacon : hyper.getEntitiesWithTag(Tags.WARNING_BEACON))
		{
			StarSystemAPI nearest = Misc.getNearestStarSystem(beacon);
			if (systems.contains(nearest))
			{
				//log.info("\tBeacon has nearest star system " + nearest.getBaseName() + ", removing");
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
		random = new Random(ExerelinUtils.getStartingSeed());
		marketSetup = new ExerelinMarketBuilder(this);
		setupData = ExerelinSetupData.getInstance();
		factionIds = getStartingFactions();
	}
	
	public void generate()
	{
		log.info("Running procedural generation");
		init();
		
		// process star systems
		systems = getCoreSystems(CORE_WIDTH, CORE_HEIGHT, 1);
		for (StarSystemAPI system : systems)
		{
			positiveDesirabilityBySystem.put(system, 0f);
			marketsBySystem.put(system, new ArrayList<ProcGenEntity>());
			createEntityDataForSystem(system);
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
		log.info("Preparing stations");
		prepFreeStations();
		
		log.info("Populating sector");
		populateSector(Global.getSector());
		setCapitals();
		spawnCommRelays();
		surveyPlanets();
		marketSetup.addCabalSubmarkets();
		
		log.info("Cleaning up derelicts/Remnants");
		cleanupDerelicts(populatedSystems);
		
		log.info("Balancing economy");
		balanceMarkets();
		
		log.info("Finishing");
		finish();
	}
		
	// =========================================================================
	// Utility functions
	
	protected SectorEntityToken createStation(ProcGenEntity station, String factionId, boolean freeStation)
	{
		float angle = ExerelinUtilsAstro.getRandomAngle(random);
		int orbitRadius = (int)station.primary.getRadius();
		PlanetAPI planet = (PlanetAPI)station.primary;
		if (planet.isMoon())
			orbitRadius += 50;
		else if (planet.isGasGiant())
			orbitRadius += 150;
		else if (planet.isStar())
			orbitRadius = (int)station.terrain.getOrbit().computeCurrentLocation().length();
		else
			orbitRadius += 100;

		float orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(planet, orbitRadius);
		if (planet.isStar())
			orbitDays = station.terrain.getOrbit().getOrbitalPeriod();

		String name = station.name;
		String id = name.replace(' ','_');
		id = id.toLowerCase();
		List<String> images = stationImages;
		ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (factionConf != null && !factionConf.customStations.isEmpty())
			images = factionConf.customStations;
		
		String image = (String) ExerelinUtils.getRandomListElement(images, random);
		
		SectorEntityToken newStation = station.starSystem.addCustomEntity(id, name, image, factionId);
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
			log.info("Adding free station " + station.name + " for " + factionId);
			station.market = marketSetup.addMarket(station, factionId);
			//standaloneStations.add(data);
		}
		newStation.setCustomDescriptionId("orbital_station_default");
		
		station.entity = newStation;
		procGenEntitiesByToken.put(newStation, station);
		return newStation;
	}
	
	protected void addAvestaStation(SectorAPI sector, StarSystemAPI system)
	{
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
		market.addCondition(Conditions.ORBITAL_STATION);
		market.addCondition(Conditions.URBANIZED_POLITY);
		market.addCondition(Conditions.ORGANIZED_CRIME);
		market.addCondition(Conditions.STEALTH_MINEFIELDS);
		market.addCondition(Conditions.HEADQUARTERS);
		market.addCondition(Conditions.OUTPOST);
		market.addCondition(Conditions.TRADE_CENTER);
		market.addCondition(Conditions.FREE_PORT);
		//market.addCondition("nex_recycling_plant");
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket("exipirated_avesta_market");
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		
		ExerelinMarketBuilder.addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("generator", 0.2f);
		market.getTariff().modifyMult("isFreeMarket", 0.5f);
		market.setPrimaryEntity(avesta);
		avesta.setMarket(market);
		avesta.setFaction("exipirated");
		sector.getEconomy().addMarket(market);
		
		SharedData.getData().getMarketsWithoutPatrolSpawn().add(market.getId());
		avesta.addScript(new ExipiratedAvestaFleetManager(market));
		avesta.addScript(new ExipiratedPatrolFleetManager(market));
		avesta.addScript(new ExipiratedCollectorFleetManager(market));
		avesta.addScript(new ExigencyCommRelayAdder());
	}
	
	protected void addShanghai(MarketAPI market)
	{
		SectorEntityToken toOrbit = market.getPrimaryEntity();
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		SectorEntityToken shanghaiEntity = toOrbit.getContainingLocation().addCustomEntity("tiandong_shanghai", "Shanghai", "tiandong_shanghai", "tiandong");
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, ExerelinUtilsAstro.getRandomAngle(random), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		
		shanghaiEntity.setMarket(market);
		market.getConnectedEntities().add(shanghaiEntity);
		if (!market.hasCondition(Conditions.ORBITAL_STATION) && !market.hasCondition(Conditions.SPACEPORT))
		{
			market.addCondition(Conditions.ORBITAL_STATION);
		}
		market.addSubmarket("tiandong_retrofit");
		toOrbit.addTag("shanghai");
		shanghaiEntity.addTag("shanghai");
		shanghaiEntity.addTag("shanghaiStation");
		shanghaiEntity.setInteractionImage("illustrations", "urban01");
		shanghaiEntity.setCustomDescriptionId("tiandong_shanghai");
	}
	
	protected void addChaosCrack(StarSystemAPI system)
	{
		SectorEntityToken chaosCrack = system.addCustomEntity("chaosCrack", StringHelper.getString("Agustin", "chaosCrack"), "Chaos_Crack_type", "approlight");
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
	
	protected void addUnos(MarketAPI market)
	{
		SectorEntityToken toOrbit = market.getPrimaryEntity();
		SectorEntityToken hegemonyforALStation = toOrbit.getContainingLocation().addCustomEntity("unosStation",
			"Unos Station", "station_unos_type", "approlight");
		  hegemonyforALStation.setCircularOrbitPointingDown(toOrbit, 45 + 180, 400, 50);  
		  hegemonyforALStation.setCustomDescriptionId("station_approlight01");
	}
	
	protected void handleHQSpecials(SectorAPI sector, String factionId, ProcGenEntity data)
	{
		if (factionId.equals("exipirated") && ExerelinConfig.enableAvesta)
			addAvestaStation(sector, data.starSystem);
		if (factionId.equals("tiandong") && ExerelinConfig.enableShanghai)
			addShanghai(data.market);
		if (factionId.equals("approlight"))
		{
			if (ExerelinConfig.enableUnos)
				addUnos(data.market);
			addChaosCrack(data.starSystem);	// TODO: give it its own option?
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
			float distSq = MathUtils.getDistanceSquared(ent.getLocationInHyperspace(), hq.entity.getLocationInHyperspace());
			score += Math.pow(distSq, 0.25f);
		}
		return score;
	}
	
	/**
	 * Picks a planet that is as far from existing HQ planets as possible.
	 * Uses most-square-roots approach.
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
	protected ProcGenEntity pickRandomMarketCloseToHQ(String factionId, List<ProcGenEntity> candidates, 
			ProcGenEntity hq, Collection<LocationAPI> alreadyPresent)
	{
		if (ExerelinConfig.getExerelinFactionConfig(factionId).pirateFaction || factionId.equals(Factions.INDEPENDENT))
			return candidates.get(0);
		
		WeightedRandomPicker<ProcGenEntity> picker = new WeightedRandomPicker<>();
		picker.setRandom(random);
		
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
	
	/**
	 * Assigns markets to factions
	 * @param sector
	 */
	public void populateSector(SectorAPI sector)
	{
		// initial setup
		// first add planet type conditions so archetype picker knows about them
		for (ProcGenEntity entity : populatedPlanets)
		{
			marketSetup.addMarketConditionForPlanetType(entity);
		}
		
		marketSetup.pickMarketArchetypes(populatedPlanets);
		
		// faction picker
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(random);
		List<String> factions = new ArrayList<>(factionIds);
		factionPicker.addAll(factions);
		
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
			factionPlanetCount.put(factionId, 0);
			factionStationCount.put(factionId, 0);
			populatedSystemsByFaction.put(factionId, new HashSet<LocationAPI>());
			
			if (ExerelinUtilsFaction.isPirateFaction(factionId))
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
		ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(alignedFactionId);
		String spawnAsFactionId = factionConf.spawnAsFactionId;
		if (spawnAsFactionId != null) alignedFactionId = spawnAsFactionId;
		
		// before we do anything else give the "homeworld" to our faction
		pickHomeworld();
		if (!ExerelinSetupData.getInstance().freeStart)	// (true)
		{
			homeworld.isHQ = true;
			MarketAPI homeMarket = marketSetup.addMarket(homeworld, alignedFactionId);
			//SectorEntityToken relay = sector.getEntityById(systemToRelay.get(homeworld.starSystem.getId()));
			//relay.setFaction(alignedFactionId);
			populatedPlanetsCopy.remove(homeworld);
			
			handleHQSpecials(sector, alignedFactionId, homeworld);
			
			if (pirateFactions.contains(alignedFactionId))
				systemsWithPirates.add(homeworld.starSystem);
			factionPlanetCount.put(alignedFactionId, 1);
			
			existingHQs.add(homeworld);
			existingHQsByFaction.put(alignedFactionId, homeworld);
		}
		
		Collections.shuffle(populatedPlanetsCopy, random);
		Collections.shuffle(stationsCopy, random);
		List<ProcGenEntity> unassignedEntities = new ArrayList<>(populatedPlanetsCopy);	// needs to be a List instead of a Set for shuffling
		for (ProcGenEntity station : stationsCopy) {
			unassignedEntities.add(station);
		}
		
		Set<ProcGenEntity> toRemove = new HashSet<>();
		
		// assign HQ worlds
		for (String factionId : factions)
		{
			if (factionId.equals(alignedFactionId)) continue;
			if (populatedPlanetsCopy.size() <= 0) break;
			ProcGenEntity hq = pickHQ(populatedPlanetsCopy, existingHQs);
			populatedPlanetsCopy.remove(hq);
			
			ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
			if (!(config != null && config.noHomeworld == true))
				hq.isHQ = true;
			
			marketSetup.addMarket(hq, factionId);
			handleHQSpecials(sector, factionId, hq);
			
			if (hq.isCapital)
			{
				SectorEntityToken relay = sector.getEntityById(systemToRelay.get(hq.starSystem.getId()));
				relay.setFaction(factionId);
			}
			
			if (pirateFactions.contains(factionId))
				systemsWithPirates.add(hq.starSystem);
			factionPlanetCount.put(factionId, factionPlanetCount.get(factionId) + 1);
			
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
				
				if (random.nextFloat() > ExerelinConfig.forcePiratesInSystemChance) {
					systemsWithPirates.add(entity.starSystem);	// don't actually have pirates, but pretend we do to skip over it
					continue;
				}

				if (piratePicker.isEmpty())
					piratePicker.addAll(pirateFactions);

				String factionId = piratePicker.pickAndRemove();
				
				if (entity.type == EntityType.PLANET || entity.type == EntityType.MOON)
				{
					marketSetup.addMarket(entity, factionId);
					populatedPlanetsCopy.remove(entity);
					factionPlanetCount.put(factionId, factionPlanetCount.get(factionId) + 1);
				}
				else
				{
					createStation(entity, factionId, true);
					stationsCopy.remove(entity);
					factionStationCount.put(factionId, factionStationCount.get(factionId) + 1);
				}
				toRemove.add(entity);
				systemsWithPirates.add(entity.starSystem);
				populatedSystemsByFaction.get(factionId).add(entity.starSystem);
			}
			unassignedEntities.removeAll(toRemove);
		}
		
		// assign remaining planets
		Map<String, Float> factionShare = new HashMap<>();
		float totalShare = 0;
		for (String factionId : factions) {
			float share = 1;
			if (setupData.useFactionWeights)
				share = ExerelinConfig.getExerelinFactionConfig(factionId).marketSpawnWeight;
			totalShare += share;
			factionShare.put(factionId, share);
		}
		
		int remainingPlanets = populatedPlanetsCopy.size();
		for (String factionId : factions) {
			int numPlanets = (int)(remainingPlanets * (factionShare.get(factionId)/totalShare) + 0.5);
			for (int i=factionPlanetCount.get(factionId);i<numPlanets;i++)
			{
				if (populatedPlanetsCopy.isEmpty()) break;
				
				ProcGenEntity habitable = pickRandomMarketCloseToHQ(factionId, populatedPlanetsCopy, 
						existingHQsByFaction.get(factionId), populatedSystemsByFaction.get(factionId));
				populatedPlanetsCopy.remove(habitable);
				unassignedEntities.remove(habitable);
				marketSetup.addMarket(habitable, factionId);
				factionPlanetCount.put(factionId, factionPlanetCount.get(factionId) + 1);
				
				if (habitable.isCapital)
				{
					SectorEntityToken relay = sector.getEntityById(systemToRelay.get(habitable.starSystem.getId()));
					relay.setFaction(factionId);
				}
				populatedSystemsByFaction.get(factionId).add(habitable.starSystem);
			}
			if (populatedPlanetsCopy.isEmpty()) break;
		}
		
		// dole out any unassigned planets
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
			
			marketSetup.addMarket(planet, factionId);
			unassignedEntities.remove(planet);
			populatedSystemsByFaction.get(factionId).add(planet.starSystem);
		}
		
		// assign stations		
		int remainingStations = stationsCopy.size();
		for (String factionId : factions) {
			int numStations = (int)(remainingStations * (factionShare.get(factionId)/totalShare) + 0.5);
			for (int i=factionStationCount.get(factionId);i<numStations;i++)
			{
				ProcGenEntity station = pickRandomMarketCloseToHQ(factionId, stationsCopy, 
						existingHQsByFaction.get(factionId), populatedSystemsByFaction.get(factionId));
				stationsCopy.remove(station);
				unassignedEntities.remove(station);
				createStation(station, factionId, true);
				factionStationCount.put(factionId, factionStationCount.get(factionId) + 1);
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
		
		// end distribution of markets and stations
	}
	
	protected void balanceMarkets()
	{
		log.info("INITIAL SUPPLY/DEMAND");
		marketSetup.balancer.reportSupplyDemand();
		
		List<ProcGenEntity> haveMarkets = new ArrayList<>(populatedPlanets);
		haveMarkets.addAll(stations);
		Collections.sort(haveMarkets, new Comparator<ProcGenEntity>() {	// biggest markets first
			@Override
			public int compare(ProcGenEntity data1, ProcGenEntity data2)
			{
				//log.warn ("lol, " + data1.name + ", " + data2.name);
				int size1 = data1.market.getSize();
				int size2 = data2.market.getSize();
				if (size1 == size2) return 0;
				else if (size1 > size2) return 1;
				else return -1;
			}
		});
		
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceFood(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceDomesticGoods(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceFuel(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceRareMetal(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceMachinery(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceSupplies(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceOrganics(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceVolatiles(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceMetal(haveMarkets);
		Collections.sort(haveMarkets, sortByMarketPointsUsed);
		marketSetup.balancer.balanceOre(haveMarkets);
		
		// second pass
		/*
		marketSetup.balancer.balanceMachinery(haveMarkets);
		//marketSetup.balancer.balanceSupplies(haveMarkets);
		marketSetup.balancer.balanceOrganics(haveMarkets);
		marketSetup.balancer.balanceVolatiles(haveMarkets);
		marketSetup.balancer.balanceMetal(haveMarkets);
		marketSetup.balancer.balanceOre(haveMarkets);
		*/
				
		log.info("FINAL SUPPLY/DEMAND");
		marketSetup.balancer.reportSupplyDemand();
		
		
		// give any market without something useful a recycling station
		for (ProcGenEntity ent : haveMarkets)
		{
			if (!ExerelinMarketBuilder.hasProductiveCondition(ent.market))
			{
				log.info("Adding recycling plant to market " + ent.market.getName());
				ent.market.addCondition("nex_recycling_plant");
			}
		}		
		
		for (ProcGenEntity entity : haveMarkets)
			ExerelinMarketBuilder.addStartingMarketCommodities(entity.market);
	}
	
	protected void finish()
	{
		SectorManager.setHomeworld(homeworld.entity);
		
		SectorManager.setSystemToRelayMap(systemToRelay);
		SectorManager.setPlanetToRelayMap(planetToRelay);
		
		SectorManager.reinitLiveFactions();
		DiplomacyManager.initFactionRelationships(false);
	}
	
	public static class ProcGenEntity {
		String name = "";
		SectorEntityToken entity;
		String planetType = "";
		float desirability = 0;
		boolean inhabited = true;
		boolean isCapital = false;
		boolean isHQ = false;
		EntityType type = EntityType.PLANET;
		StarSystemAPI starSystem;
		SectorEntityToken primary;
		CampaignTerrainAPI terrain;	// for stations
		MarketAPI market;
		ExerelinMarketBuilder.Archetype archetype = ExerelinMarketBuilder.Archetype.MISC;
		int forceMarketSize = -1;
		//float orbitRadius = 0;
		//float orbitPeriod = 0;
		int marketPoints = 0;
		int marketPointsSpent = 0;
		int bonusMarketPoints = 0;
		
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
	
	protected Comparator<ProcGenEntity> sortByMarketPointsUsed = new Comparator<ProcGenEntity>() {
		public int compare(ProcGenEntity e1, ProcGenEntity e2) {
			//if (e1.marketPoints == 0)
			//	throw new ArithmeticException("Fuck you, entity " + e1.name + " has zero market points");
			//if (e2.marketPoints == 0)
			//	throw new ArithmeticException("Fuck you, entity " + e2.name + " has zero market points");
			
			float spendPercent1 = divideWithDiv0Protection(e1.marketPointsSpent, e1.marketPoints);
			float spendPercent2 = divideWithDiv0Protection(e2.marketPointsSpent, e2.marketPoints);

			if (spendPercent1 > spendPercent2) return -1;
			else if (spendPercent2 > spendPercent1) return 1;
			else return 0;
		}};
}
