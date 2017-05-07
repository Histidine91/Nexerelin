package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
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
import exerelin.plugins.ExerelinModPlugin;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;


public class ExerelinProcGen {
	
	public static final float REVERSE_ORBIT_CHANCE = 0.2f;
	public static final float LAGRANGE_ASTEROID_CHANCE = 0.5f;
	public static final float BINARY_STAR_DISTANCE = 13000;
	public static final float BINARY_SYSTEM_PLANET_MULT = 1.25f;
	public static final float MIN_RADIUS_FOR_BINARY = 400f;
	public static final float NEBULA_CHANCE = 0.35f;
	public static final float MAGNETIC_FIELD_CHANCE = 0.5f;
	public static final float STELLAR_RING_CHANCE = 0.3f;
	public static final float STELLAR_BELT_CHANCE = 0.5f;	// for systems with nebulae; will always spawn asteroids if nebula is absent
	public static final float UNINHABITED_RELAY_CHANCE = 0.25f;
	public static final float STAR_RANDOM_OFFSET = 100;
	public static final float STAR_SIZE_VARIATION = 0.2f;
	
	// this proportion of TT markets with no military bases will have Cabal submarkets (Underworld)
	public static final float CABAL_MARKET_MULT = 0.4f;	
	// this is the chance a market with a military base will still be a candidate for Cabal markets
	public static final float CABAL_MILITARY_MARKET_CHANCE = 0.5f;
	
	// NOTE: system names and planet names are overriden by planetNames.json
	protected static final String PLANET_NAMES_FILE = "data/config/exerelin/planetNames.json";
	// don't specify names here to make sure it crashes instead of failing silently if planetNames.json is broken

	protected List<String> possibleSystemNames = new ArrayList<>();
	protected List<String> possiblePlanetNames = new ArrayList<>();
	protected List<String> possibleStationNames = new ArrayList<>();
	
	public static final List<String> stationImages = new ArrayList<>(Arrays.asList(
			new String[] {"station_side00", "station_side02", "station_side04", "station_jangala_type"}));
	
	protected List<String> factionIds = new ArrayList<>();
	protected List<EntityData> habitablePlanets;
	protected List<EntityData> stations;
	protected List<EntityData> standaloneStations;
	protected Map<String, String> systemToRelay = new HashMap();
	protected Map<String, String> planetToRelay = new HashMap();
	
	protected EntityData homeworld;
	protected ExerelinMarketSetup marketSetup;
	
	protected void loadData()
	{
		try {
			JSONObject planetConfig = Global.getSettings().loadJSON(PLANET_NAMES_FILE);
			
			JSONArray systemNames = planetConfig.getJSONArray("stars");
			possibleSystemNames = ExerelinUtils.JSONArrayToArrayList(systemNames);
			
			JSONArray planetNames = planetConfig.getJSONArray("planets");
			possiblePlanetNames = ExerelinUtils.JSONArrayToArrayList(planetNames);
			
			JSONArray stationNames = planetConfig.getJSONArray("stations");
			possibleStationNames = ExerelinUtils.JSONArrayToArrayList(stationNames);
		} catch (JSONException | IOException ex) {
			log.error(ex);
		}
				
		
		
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean corvusMode = setupData.corvusMode;
		
		if (!corvusMode)
		{
			
		}
	}
	
	protected void resetVars()
	{
		ExerelinSetupData.getInstance().resetAvailableFactions();
		factionIds = getStartingFactions();
		//numOmnifacs = 0;
	}
	
	protected List<String> getStartingFactions()
	{
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		List<String> availableFactions = setupData.getPlayableFactions();
		int wantedFactionNum = setupData.numStartFactions;
		if (wantedFactionNum <= 0) {
			if (ExerelinConfig.enableIndependents)
				availableFactions.add(Factions.INDEPENDENT);
			if (!ExerelinConfig.enablePirates)
				availableFactions.remove(Factions.PIRATES);
			return availableFactions;
		}
		
		int numFactions = 0;
		Set<String> factions = new HashSet<>();
		
		if (!ExerelinSetupData.getInstance().freeStart)
		{
			String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
			factions.add(alignedFactionId);
			availableFactions.remove(alignedFactionId);
			numFactions++;
		}
		
		if (ExerelinConfig.enablePirates) {
			factions.add(Factions.PIRATES);
		}
		availableFactions.remove(Factions.PIRATES);
			
		if (ExerelinConfig.enableIndependents) {
			factions.add(Factions.INDEPENDENT);
		}
		availableFactions.remove(Factions.INDEPENDENT);	// note: normally independents can't appear as they're not a playable faction
		
		availableFactions.remove(ExerelinConstants.PLAYER_NPC_ID);
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		picker.addAll(availableFactions);
		
		while (numFactions < wantedFactionNum)
		{
			if (picker.isEmpty()) break;
			String factionId = picker.pickAndRemove();
			factions.add(factionId);
			log.info("Adding starting faction: " + factionId);
			numFactions++;
		}
		log.info("Number of starting factions: " + numFactions);
		return new ArrayList<>(factions);
	}
	
	protected void pickEntityInteractionImage(SectorEntityToken entity, MarketAPI market, String planetType, EntityType entityType)
	{
		WeightedRandomPicker<String[]> allowedImages = new WeightedRandomPicker();
		allowedImages.add(new String[]{"illustrations", "cargo_loading"} );
		allowedImages.add(new String[]{"illustrations", "hound_hangar"} );
		allowedImages.add(new String[]{"illustrations", "space_bar"} );

		boolean isStation = (entityType == EntityType.STATION);
		boolean isMoon = (entityType == EntityType.MOON); 
		int size = market.getSize();
		boolean largeMarket = size >= 5;
		if (isStation) largeMarket = size >= 4;

		if(market.hasCondition(Conditions.URBANIZED_POLITY) || largeMarket)
		{
			allowedImages.add(new String[]{"illustrations", "urban00"} );
			allowedImages.add(new String[]{"illustrations", "urban01"} );
			allowedImages.add(new String[]{"illustrations", "urban02"} );
			allowedImages.add(new String[]{"illustrations", "urban03"} );
			
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
		if (entity.getFaction().getId().equals("pirates"))
			allowedImages.add(new String[]{"illustrations", "pirate_station"} );
		if (!isStation && (planetType.equals("rocky_metallic") || planetType.equals("rocky_barren") || planetType.equals("barren-bombarded")) )
			allowedImages.add(new String[]{"illustrations", "vacuum_colony"} );
		//if (isMoon)
		//	allowedImages.add(new String[]{"illustrations", "asteroid_belt_moon"} );
		if (planetType.equals("desert") && isMoon)
			allowedImages.add(new String[]{"illustrations", "desert_moons_ruins"} );

		String[] illustration = allowedImages.pick();
		entity.setInteractionImage(illustration[0], illustration[1]);
	}
	
	// TBD
	protected EntityData getHomeworld()
	{
		return homeworld;
	}
	
	protected void addCabalSubmarkets()
	{
		// add Cabal submarkets
		if (ExerelinUtils.isSSPInstalled(true) || ExerelinModPlugin.HAVE_UNDERWORLD)
		{
			List<MarketAPI> cabalCandidates = new ArrayList<>();
			List<MarketAPI> cabalCandidatesBackup = new ArrayList<>();
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
			{
				if (!market.getFactionId().equals(Factions.TRITACHYON)) continue;
				if (market.hasCondition(Conditions.MILITARY_BASE) && Math.random() > CABAL_MILITARY_MARKET_CHANCE) 
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
					if (ExerelinModPlugin.HAVE_UNDERWORLD)
						market.addSubmarket("uw_cabalmarket");
					else
						market.addSubmarket("ssp_cabalmarket");
					market.addCondition("cabal_influence");
					log.info("Added Cabal submarket to " + market.getName() + " (size " + market.getSize() + ")");
				}
			} catch (RuntimeException rex) {
				// old SS+ version, do nothing
			}
		}
	}
	
	protected void buildSystem(SectorAPI sector, int systemIndex, boolean inhabited)
	{
		// First we make a star system with random name
		int systemNameIndex = MathUtils.getRandomNumberInRange(0, possibleSystemNames.size() - 1);
		if (systemIndex == 0) systemNameIndex = 0;	// there is always a starSystem named Exerelin
		StarSystemAPI system = sector.createStarSystem(possibleSystemNames.get(systemNameIndex));
		possibleSystemNames.remove(systemNameIndex);
		String systemName = system.getName();
		String systemId = system.getId();
		EntityData capital = null;
		
		// Build stations
		// Note: to enable special faction stations, we don't actually generate the stations until much later,
		// when we're dealing out planets to factions
		
		/*
		if (inhabited)
		{
			int numStations;
			int maxStations = ExerelinSetupData.getInstance().maxStations;
			if(ExerelinSetupData.getInstance().numSystems != 1)
			{
				int minStations = ExerelinConfig.minimumStations;
				if (minStations > maxStations) maxStations = minStations;
				numStations = MathUtils.getRandomNumberInRange(minStations, Math.min(maxStations, numBasePlanets*2));
			}
			else
				numStations = maxStations;

			// create random picker for our station locations
			WeightedRandomPicker<EntityData> picker = new WeightedRandomPicker<>();
			//addListToPicker(entities, picker);
			for (EntityData entityData : entities)
			{
				float weight = 1f;
				if (entityData.type == EntityType.STAR) weight = 3.5f;
				else if (entityData.habitable == false) weight = 2f;
				picker.add(entityData, weight);
			}

			int k = 0;
			List alreadyUsedStationNames = new ArrayList();
			while(k < numStations)
			{
				if (picker.isEmpty()) break;	//picker.add(starData);
				EntityData primaryData = picker.pickAndRemove();
				if (primaryData.entity == star && starBelts1.isEmpty()) {
					continue;
				}
				if (primaryData.entity == star2 && starBelts2.isEmpty()) {
					continue;
				}
				
				EntityData stationData = new EntityData("", system);
				stationData.primary = primaryData;
				stationData.type = EntityType.STATION;
				stationData.archetype = marketSetup.pickMarketArchetype(true);
				
				// name our station
				boolean nameOK = false;
				String name = "";
				while(!nameOK)
				{
					name = primaryData.name + " " + ExerelinUtils.getRandomListElement(possibleStationNames);
					if (!alreadyUsedStationNames.contains(name))
						nameOK = true;
				}
				alreadyUsedStationNames.add(name);
				stationData.name = name;			
				stations.add(stationData);
				log.info("Prepping station " + name);

				k = k + 1;
			}
		}
		*/
		
		// Build comm relay
		/*
		if (inhabited || Math.random() < UNINHABITED_RELAY_CHANCE)
		{
			SectorEntityToken relay = system.addCustomEntity(system.getId() + "_relay", // unique id
					system.getBaseName() + " Relay", // name - if null, defaultName from custom_entities.json will be used
					"comm_relay", // type of object, defined in custom_entities.json
					"neutral"); // faction
			float distance = getRandomOrbitRadiusBetweenPlanets(entities, 1200 + star.getRadius(), 3000 + star.getRadius());
			
			if (Math.random() < 0.5)	// random orbit around star
				relay.setCircularOrbitPointingDown(star, ExerelinUtilsAstro.getRandomAngle(), distance, ExerelinUtilsAstro.getOrbitalPeriod(star, distance));
			else	// lagrange orbit with capital
			{
				EntityData relayOrbitTarget = capital;
				if (relayOrbitTarget.type == EntityType.MOON) relayOrbitTarget = relayOrbitTarget.primary;	// L4/L5 of the planet instead of the moon
				ExerelinUtilsAstro.setLagrangeOrbit(relay, relayOrbitTarget.primary.entity, relayOrbitTarget.entity, 
					9 - jumpPointLNum, relayOrbitTarget.startAngle, relayOrbitTarget.orbitRadius, 0, relayOrbitTarget.orbitPeriod, 
					!isBinary, ellipseAngle, ellipseMult, 1, 0);
			}
			//ExerelinUtilsAstro.setOrbit(relay, star, distance, !isBinary, ellipseAngle, ExerelinUtilsAstro.getOrbitalPeriod(star, distance));
			systemToRelay.put(system.getId(), system.getId() + "_relay");
			planetToRelay.put(capital.entity.getId(), system.getId() + "_relay");
		}
		*/
	}
	
	// =========================================================================
	// Utility functions
	
	protected SectorEntityToken makeStation(EntityData data, String factionId)
	{
		float angle = ExerelinUtilsAstro.getRandomAngle();
		int orbitRadius = 200;
		PlanetAPI planet = (PlanetAPI)data.primary.entity;
		if (data.primary.type == EntityType.MOON)
			orbitRadius = 150;
		else if (planet.isGasGiant())
			orbitRadius = 500;
		else if (planet.isStar())
			orbitRadius = (int)data.orbitRadius;
		if (!planet.isStar())	// don't do for belter stations, else they spawn outside the belt
			orbitRadius += planet.getRadius();
		
		data.orbitRadius = orbitRadius;

		float orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(planet, orbitRadius);
		if (planet.isStar() && orbitDays < 0)
			orbitDays *= -1;	// prevents weirdness of station orbiting in reverse direction to belt
		data.orbitPeriod = orbitDays;

		String name = data.name;
		String id = name.replace(' ','_');
		id = id.toLowerCase();
		List<String> images = stationImages;
		ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (factionConf != null && !factionConf.customStations.isEmpty())
			images = factionConf.customStations;
		
		String image = (String) ExerelinUtils.getRandomListElement(images);
		
		SectorEntityToken newStation = data.starSystem.addCustomEntity(id, name, image, factionId);
		newStation.setCircularOrbitPointingDown(planet, angle, orbitRadius, orbitDays);
		data.entity = newStation;
		
		MarketAPI existingMarket = planet.getMarket();
		if (existingMarket != null)
		{
			if (!existingMarket.hasCondition(Conditions.SPACEPORT))
			{
				existingMarket.addCondition("orbital_station");
				marketSetup.modifyCommodityDemand(Commodities.SUPPLIES, ConditionData.ORBITAL_STATION_SUPPLIES * 0.6f);
			}
			//existingMarket.addCondition("exerelin_recycling_plant");
			newStation.setMarket(existingMarket);
			existingMarket.getConnectedEntities().add(newStation);
			data.market = existingMarket;
		}
		else
		{	
			data.market = marketSetup.addMarketToEntity(data, factionId);
			//standaloneStations.add(data);
		}
		pickEntityInteractionImage(newStation, newStation.getMarket(), planet.getTypeId(), EntityType.STATION);
		newStation.setCustomDescriptionId("orbital_station_default");
		
		data.entity = newStation;		
		return newStation;
	}
	
	protected void addAvestaStation(SectorAPI sector, StarSystemAPI system)
	{
		SectorEntityToken avesta;
		
		if (ExerelinSetupData.getInstance().numSystems == 1)
		{
			SectorEntityToken toOrbit = system.getStar();
			float radius = toOrbit.getRadius();
			float orbitDistance = radius + MathUtils.getRandomNumberInRange(2000, 2500);
			avesta = toOrbit.getContainingLocation().addCustomEntity(ExerelinConstants.AVESTA_ID, "Avesta Station", "exipirated_avesta_station", "exipirated");
			avesta.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
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
		market.addCondition("exerelin_recycling_plant");
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket("exipirated_avesta_market");
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		
		marketSetup.addStartingMarketCommodities(market);
		
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
	
	/*
	protected void addShanghai(SectorEntityToken toOrbit)
	{	
		float radius = toOrbit.getRadius();
		float orbitRadius = radius + 150;
		SectorEntityToken shanghaiEntity = toOrbit.getContainingLocation().addCustomEntity("tiandong_shanghai", "Shanghai", "tiandong_shanghai", "tiandong");
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitRadius, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitRadius));
		
		//EntityData data = new EntityData(null);
		//data.name = "Shanghai";
		//data.type = EntityType.STATION;
		//data.forceMarketSize = 4;
		
		//MarketAPI market = addMarketToEntity(shanghaiEntity, data, "independent");

		MarketAPI market = Global.getFactory().createMarket("tiandong_shanghai" + "_market", "Avesta Station", 4);
		market.setFactionId("tiandong");
		market.addCondition(Conditions.POPULATION_5);
		market.addCondition(Conditions.ORBITAL_STATION);
		for (int i=0;i<6; i++)
			market.addCondition(Conditions.ORE_COMPLEX);
		market.addCondition(Conditions.ORE_REFINING_COMPLEX);
		market.addCondition(Conditions.ORE_REFINING_COMPLEX);
		market.addCondition(Conditions.TRADE_CENTER);
		market.addCondition(Conditions.SHIPBREAKING_CENTER);
		
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.GENERIC_cabal);
		market.addSubmarket("tiandong_retrofit");
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		
		addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("generator", 0.2f);
		market.setPrimaryEntity(shanghaiEntity);
		shanghaiEntity.setMarket(market);
		shanghaiEntity.setFaction("tiandong");
		sector.getEconomy().addMarket(market);
		toOrbit.addTag("shanghai");
		shanghaiEntity.addTag("shanghai");
		shanghaiEntity.setCustomDescriptionId("tiandong_shanghai");
	}
	*/
	
	protected void addShanghai(MarketAPI market)
	{
		SectorEntityToken toOrbit = market.getPrimaryEntity();
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		SectorEntityToken shanghaiEntity = toOrbit.getContainingLocation().addCustomEntity("tiandong_shanghai", "Shanghai", "tiandong_shanghai", "tiandong");
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		
		shanghaiEntity.setMarket(market);
		market.getConnectedEntities().add(shanghaiEntity);
		if (!market.hasCondition(Conditions.ORBITAL_STATION) && !market.hasCondition(Conditions.SPACEPORT))
		{
			market.addCondition(Conditions.ORBITAL_STATION);
			marketSetup.modifyCommodityDemand(Commodities.SUPPLIES, ConditionData.ORBITAL_STATION_SUPPLIES * 0.6f);
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
		system.addRingBand(chaosCrack, "misc", "rings1", 256f, 3, Color.white, 256f, 800, 360f);
		system.addRingBand(chaosCrack, "misc", "rings1", 256f, 3, Color.white, 256f, 1200, 360f);
		system.addRingBand(chaosCrack, "misc", "rings1", 256f, 2, Color.white, 1024f, 3000, 360f);
		system.addRingBand(chaosCrack, "misc", "rings1", 256f, 3, Color.white, 512f, 2000, 360f);
		system.addRingBand(chaosCrack, "misc", "rings1", 256f, 2, Color.white, 512f, 4000, 360f);
		system.addRingBand(chaosCrack, "misc", "rings1", 256f, 2, Color.white, 512f, 6000, 360f);
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
	
	protected void handleHQSpecials(SectorAPI sector, String factionId, EntityData data)
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
			data.market.addSubmarket("AL_plugofbarrack");
		}
	}
	
	public void populateSector(SectorAPI sector)
	{
		// initial setup
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>();
		List<String> factions = new ArrayList<>(factionIds);
		factions.remove(ExerelinConstants.PLAYER_NPC_ID);  // player NPC faction only gets homeworld (if applicable)
		factionPicker.addAll(factions);
		
		Map<String, Integer> factionPlanetCount = new HashMap<>();
		Map<String, Integer> factionStationCount = new HashMap<>();
		List<EntityData> habitablePlanetsCopy = new ArrayList<>(habitablePlanets);
		List<EntityData> stationsCopy = new ArrayList<>(stations);
		List<String> pirateFactions = new ArrayList<>();
		
		for (String factionId : factions) {
			factionPlanetCount.put(factionId, 0);
			factionStationCount.put(factionId, 0);
			if (ExerelinUtilsFaction.isPirateFaction(factionId))
				pirateFactions.add(factionId);
		}

		List<StarSystemAPI> systemsWithPirates = new ArrayList<>();
		
		// before we do anything else give the "homeworld" to our faction
		String alignedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		if (!ExerelinSetupData.getInstance().freeStart)
		{
			homeworld.isHQ = true;
			MarketAPI homeMarket = marketSetup.addMarketToEntity(homeworld, alignedFactionId);
			SectorEntityToken relay = sector.getEntityById(systemToRelay.get(homeworld.starSystem.getId()));
			relay.setFaction(alignedFactionId);
			pickEntityInteractionImage(homeworld.entity, homeworld.entity.getMarket(), homeworld.planetType, homeworld.type);
			habitablePlanetsCopy.remove(homeworld);
			factionPicker.remove(alignedFactionId);
			
			StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
			plugin.setPlayerPaidToUnlock(true);
			
			handleHQSpecials(sector, alignedFactionId, homeworld);
			
			if (pirateFactions.contains(alignedFactionId))
				systemsWithPirates.add(homeworld.starSystem);
			factionPlanetCount.put(alignedFactionId, 1);
		}
		
		Collections.shuffle(habitablePlanetsCopy);
		Collections.shuffle(stationsCopy);
		List<EntityData> unassignedEntities = new ArrayList<>(habitablePlanetsCopy);	// needs to be a List instead of a Set for shuffling
		for (EntityData station : stationsCopy) {
			if (!station.primary.habitable)
				unassignedEntities.add(station);
		}
		
		Set<EntityData> toRemove = new HashSet<>();
		
		// assign HQ worlds
		for (String factionId : factions)
		{
			if (factionId.equals(alignedFactionId)) continue;
			if (habitablePlanetsCopy.size() <= 0) break;
			EntityData habitable = habitablePlanetsCopy.get(0);
			habitablePlanetsCopy.remove(0);
			
			ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
			if (!(config != null && config.noHomeworld == true))
				habitable.isHQ = true;
			
			marketSetup.addMarketToEntity(habitable, factionId);
			handleHQSpecials(sector, factionId, habitable);
			
			if (habitable.isCapital)
			{
				SectorEntityToken relay = sector.getEntityById(systemToRelay.get(habitable.starSystem.getId()));
				relay.setFaction(factionId);
			}
			
			pickEntityInteractionImage(habitable.entity, habitable.entity.getMarket(), habitable.planetType, habitable.type);
			
			if (pirateFactions.contains(factionId))
				systemsWithPirates.add(habitable.starSystem);
			factionPlanetCount.put(factionId, factionPlanetCount.get(factionId) + 1);
			
			unassignedEntities.remove(habitable);
		}
		
		// ensure pirate presence in every star system
		
		if (!pirateFactions.isEmpty())
		{
			WeightedRandomPicker<String> piratePicker = new WeightedRandomPicker<>();

			Collections.shuffle(unassignedEntities);
			for (EntityData entity : unassignedEntities)
			{
				if (systemsWithPirates.size() == ExerelinSetupData.getInstance().numSystems)	// all systems already have pirates
					break;

				if (systemsWithPirates.contains(entity.starSystem))
					continue;
				
				if (Math.random() > ExerelinConfig.forcePiratesInSystemChance) {
					systemsWithPirates.add(entity.starSystem);
					continue;
				}

				if (piratePicker.isEmpty())
					piratePicker.addAll(pirateFactions);

				String factionId = piratePicker.pickAndRemove();
				
				if (entity.type == EntityType.PLANET || entity.type == EntityType.MOON)
				{
					marketSetup.addMarketToEntity(entity, factionId);
					habitablePlanetsCopy.remove(entity);
					factionPlanetCount.put(factionId, factionPlanetCount.get(factionId) + 1);
				}
				else
				{
					makeStation(entity, factionId);
					stationsCopy.remove(entity);
					factionStationCount.put(factionId, factionStationCount.get(factionId) + 1);
				}
				toRemove.add(entity);
				systemsWithPirates.add(entity.starSystem);
			}
			unassignedEntities.removeAll(toRemove);
		}
		
		// assign remaining planets
		Map<String, Float> factionShare = new HashMap<>();
		float totalShare = 0;
		for (String factionId : factions) {
			float share = 1;
			ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
			if (config != null)
				share = config.spawnMarketShare;
			totalShare += share;
			factionShare.put(factionId, share);
		}
		
		int remainingPlanets = habitablePlanetsCopy.size();
		for (String factionId : factions) {
			int numPlanets = (int)(remainingPlanets * (factionShare.get(factionId)/totalShare) + 0.5);
			for (int i=factionPlanetCount.get(factionId);i<numPlanets;i++)
			{
				if (habitablePlanetsCopy.isEmpty()) break;
				
				EntityData habitable = habitablePlanetsCopy.get(0);
				habitablePlanetsCopy.remove(0);
				unassignedEntities.remove(habitable);
				marketSetup.addMarketToEntity(habitable, factionId);
				factionPlanetCount.put(factionId, factionPlanetCount.get(factionId) + 1);
				
				if (habitable.isCapital)
				{
					SectorEntityToken relay = sector.getEntityById(systemToRelay.get(habitable.starSystem.getId()));
					relay.setFaction(factionId);
				}
				pickEntityInteractionImage(habitable.entity, habitable.entity.getMarket(), habitable.planetType, habitable.type);
			}
			if (habitablePlanetsCopy.isEmpty()) break;
		}
		
		// dole out any unassigned planets
		for (EntityData planet : habitablePlanetsCopy)
		{
			if (planet.market != null)
			{
				log.error("Unassigned entity " + planet.name + " already has market!");
				continue;
			}
			
			if (factionPicker.isEmpty())
				factionPicker.addAll(factions);
			String factionId = factionPicker.pickAndRemove();
			
			marketSetup.addMarketToEntity(planet, factionId);
			unassignedEntities.remove(planet);
		}
		
		// now for stations
		for (EntityData station : stationsCopy)
		{
			if (station.primary.habitable)
			{
				makeStation(station, station.primary.entity.getFaction().getId());
			}
			else
			{
				standaloneStations.add(station);
			}
		}
		List<EntityData> standaloneStationsCopy = new ArrayList<>(standaloneStations);
		
		int remainingStations = standaloneStationsCopy.size();
		for (String factionId : factions) {
			int numStations = (int)(remainingStations * (factionShare.get(factionId)/totalShare) + 0.5);
			for (int i=factionStationCount.get(factionId);i<numStations;i++)
			{
				EntityData station = standaloneStationsCopy.get(0);
				standaloneStationsCopy.remove(0);
				unassignedEntities.remove(station);
				makeStation(station, factionId);
				factionStationCount.put(factionId, factionStationCount.get(factionId) + 1);
				
				if (standaloneStationsCopy.isEmpty()) break;
			}
			if (standaloneStationsCopy.isEmpty()) break;
		}
		
		// dole out any unassigned stations
		for (EntityData station : standaloneStationsCopy)
		{
			if (station.market != null)
			{
				log.error("Unassigned entity " + station.name + " already has market!");
				continue;
			}
			
			if (factionPicker.isEmpty())
				factionPicker.addAll(factions);
			String factionId = factionPicker.pickAndRemove();
			makeStation(station, factionId);
		}
		
		// end distribution of markets and stations
		
		// balance supply/demand by adding/removing relevant market conditions
		List<EntityData> haveMarkets = new ArrayList<>(habitablePlanets);
		haveMarkets.addAll(standaloneStations);
		Collections.sort(haveMarkets, new Comparator<EntityData>() {	// biggest markets first
			@Override
			public int compare(EntityData data1, EntityData data2)
			{
				//log.warn ("lol, " + data1.name + ", " + data2.name);
				int size1 = data1.market.getSize();
				int size2 = data2.market.getSize();
				if (size1 == size2) return 0;
				else if (size1 > size2) return 1;
				else return -1;
			}
		});
		log.info("INITIAL SUPPLY/DEMAND");
		marketSetup.reportSupplyDemand();
		
		marketSetup.balanceFood(haveMarkets);
		marketSetup.balanceFood(haveMarkets);	// done twice to be able to handle large deviations
		marketSetup.balanceDomesticGoods(haveMarkets);
		marketSetup.balanceFuel(haveMarkets);
		marketSetup.balanceRareMetal(haveMarkets);
		
		marketSetup.balanceMachinery(haveMarkets, true);
		marketSetup.balanceSupplies(haveMarkets);
		marketSetup.balanceOrganics(haveMarkets);
		marketSetup.balanceVolatiles(haveMarkets);
		marketSetup.balanceMetal(haveMarkets);
		marketSetup.balanceOre(haveMarkets);
		
		// second pass
		marketSetup.balanceMachinery(haveMarkets, false);
		marketSetup.balanceSupplies(haveMarkets);
		marketSetup.balanceOrganics(haveMarkets);
		marketSetup.balanceVolatiles(haveMarkets);
		marketSetup.balanceMetal(haveMarkets);
		marketSetup.balanceOre(haveMarkets);
		
		log.info("FINAL SUPPLY/DEMAND");
		marketSetup.reportSupplyDemand();
		
		for (EntityData entity : haveMarkets)
			marketSetup.addStartingMarketCommodities(entity.market);
	}
	
	protected void finish()
	{
		SectorManager.reinitLiveFactions();
		DiplomacyManager.initFactionRelationships(false);
		SectorManager.setHomeworld(homeworld.entity);
		
		SectorManager.setSystemToRelayMap(systemToRelay);
		SectorManager.setPlanetToRelayMap(planetToRelay);
	}
	
	public static class EntityData {
		String name;
		SectorEntityToken entity;
		String planetType = "";
		boolean habitable = false;
		boolean isCapital = false;
		boolean isHQ = false;
		EntityType type = EntityType.PLANET;
		StarSystemAPI starSystem;
		EntityData primary;
		MarketAPI market;
		ExerelinMarketSetup.MarketArchetype archetype = ExerelinMarketSetup.MarketArchetype.MIXED;
		int forceMarketSize = -1;
		int planetNum = -1;
		int planetNumByStar = -1;
		float orbitRadius = 0;
		float orbitPeriod = 0;
		float startAngle = 0;
		float clearRadius = 0;	// don't put other orbits this close to ours
		int marketPoints = 0;
		int marketPointsSpent = 0;
		int bonusMarketPoints = 0;
		
		public EntityData(String name, StarSystemAPI starSystem) 
		{
			this.name = name;
			this.starSystem = starSystem;
		}	  
		public EntityData(String name, StarSystemAPI starSystem, int planetNum) 
		{
			this.name = name;
			this.starSystem = starSystem;
			this.planetNum = planetNum;
			this.planetNumByStar = planetNum;
		}	  
	}
	
	public enum EntityType {
		STAR, PLANET, MOON, STATION
	}
}
