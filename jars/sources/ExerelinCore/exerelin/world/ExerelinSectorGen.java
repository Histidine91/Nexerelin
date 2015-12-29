package exerelin.world;

import java.awt.Color;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.events.CoreEventProbabilityManager;
import com.fs.starfarer.api.impl.campaign.fleets.BountyPirateFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.LuddicPathFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.MercFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.PirateFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.missions.FactionCommissionMissionCreator;
import com.fs.starfarer.api.impl.campaign.missions.MarketProcurementMissionCreator;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.ExerelinCorvusLocations;
import exerelin.campaign.AllianceManager;
import exerelin.plugins.*;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinCoreScript;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.world.ExerelinMarketSetup.MarketArchetype;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollectionUtils.CollectionFilter;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.orbits.EllipticalOrbit;
import org.lazywizard.omnifac.OmniFac;
import org.lazywizard.omnifac.OmniFacSettings;
import org.lwjgl.util.vector.Vector2f;

@SuppressWarnings("unchecked")

public class ExerelinSectorGen implements SectorGeneratorPlugin
{
	// NOTE: system names and planet names are overriden by planetNames.json
	protected static final String PLANET_NAMES_FILE = "data/config/exerelin/planetNames.json";
	protected static String[] possibleSystemNames = {"Exerelin", "Askar", "Garil", "Yaerol", "Plagris", "Marot", "Caxort", "Laret", "Narbil", "Karit",
		"Raestal", "Bemortis", "Xanador", "Tralor", "Exoral", "Oldat", "Pirata", "Zamaror", "Servator", "Bavartis", "Valore", "Charbor", "Dresnen",
		"Firort", "Haidu", "Jira", "Wesmon", "Uxor"};
	protected static String[] possiblePlanetNames = new String[] {"Baresh", "Zaril", "Vardu", "Drewler", "Trilar", "Polres", "Laret", "Erilatir",
		"Nambor", "Zat", "Raqueler", "Garret", "Carashil", "Qwerty", "Azerty", "Tyrian", "Savarra", "Torm", "Gyges", "Camanis", "Ixmucane", "Yar", "Tyrel",
		"Tywin", "Arya", "Sword", "Centuri", "Heaven", "Hell", "Sanctuary", "Hyperion", "Zaphod", "Vagar", "Green", "Blond", "Gabrielle", "Masset",
		"Effecer", "Gunsa", "Patiota", "Rayma", "Origea", "Litsoa", "Bimo", "Plasert", "Pizzart", "Shaper", "Coruscent", "Hoth", "Gibraltar", "Aurora",
		"Darwin", "Mendel", "Crick", "Franklin", "Watson", "Pauling",
		"Rutherford", "Maxwell", "Bohr", "Pauli", "Curie", "Meitner", "Heisenberg", "Feynman"};
	protected static String[] possibleStationNames = new String[] {"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge", "Customs", "Nest",
		"Port", "Quey", "Terminal", "Exchange", "View", "Wall", "Habitat", "Shipyard", "Backwater"};
	protected static final String[] starBackgroundsArray = new String[]
	{
		"backgrounds/background1.jpg", "backgrounds/background2.jpg", "backgrounds/background3.jpg", "backgrounds/background4.jpg", "backgrounds/background5.jpg",
		"exerelin/backgrounds/blue_background1.jpg", "exerelin/backgrounds/blue_background2.jpg",
		"exerelin/backgrounds/bluewhite_background1.jpg", "exerelin/backgrounds/orange_background1.jpg",
		"exerelin/backgrounds/dark_background1.jpg", "exerelin/backgrounds/dark_background2.jpg",
		"exerelin/backgrounds/green_background1.jpg", //"exerelin/backgrounds/green_background2.jpg",
		"exerelin/backgrounds/purple_background1.jpg", //"exerelin/backgrounds/purple_background2.jpg",
		"exerelin/backgrounds/white_background1.jpg", "exerelin/backgrounds/white_background2.jpg",
		"backgrounds/2-2.jpg", "backgrounds/2-4.jpg", "backgrounds/3-1.jpg", "backgrounds/4-1.jpg", "backgrounds/4-2.jpg", "backgrounds/5-1.jpg", "backgrounds/5-2.jpg",
		"backgrounds/6-1.jpg", "backgrounds/7-1.jpg", "backgrounds/7-3.jpg", "backgrounds/8-1.jpg", "backgrounds/8-2.jpg", "backgrounds/9-1.jpg", "backgrounds/9-3.jpg",
		"backgrounds/9-4.jpg", "backgrounds/9-5.jpg",
	};
	protected static final List<String> nebulaMaps = new ArrayList<>();
	protected static final String[] nebulaColors = new String[] {"blue", "amber"};
	
	protected static ArrayList<String> starBackgrounds = new ArrayList<>(Arrays.asList(starBackgroundsArray));

	protected List<String> possibleSystemNamesList = new ArrayList(Arrays.asList(possibleSystemNames));
	protected List<String> possiblePlanetNamesList = new ArrayList(Arrays.asList(possiblePlanetNames));
	protected List<String> possibleStationNamesList = new ArrayList(Arrays.asList(possibleStationNames));
	
	//protected static final String[] planetTypes = new String[] {"desert", "jungle", "frozen", "terran", "arid", "water", "rocky_metallic", "rocky_ice", "barren", "barren-bombarded"};
	protected static final String[] planetTypesUninhabitable = new String[] 
		{"desert", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "frozen", "rocky_ice", "irradiated", "barren-bombarded", "barren-desert", "terran-eccentric"};
	protected static final String[] planetTypesGasGiant = new String[] {"gas_giant", "ice_giant"};
	//protected static final String[] moonTypes = new String[] {"frozen", "barren", "barren-bombarded", "rocky_ice", "rocky_metallic", "desert", "water", "jungle"};
	protected static final String[] moonTypesUninhabitable = new String[] 
		{"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice", "irradiated", "barren-bombarded", "desert", "water", "jungle", "barren-desert"};
	
	protected static final List<String> stationImages = new ArrayList<>(Arrays.asList(
			new String[] {"station_side00", "station_side02", "station_side04", "station_jangala_type"}));
	
	protected static final float REVERSE_ORBIT_CHANCE = 0.2f;
	protected static final float BINARY_STAR_DISTANCE = 13000;
	protected static final float BINARY_SYSTEM_PLANET_MULT = 1.25f;
	protected static final float NEBULA_CHANCE = 0.35f;
	protected static final float MAGNETIC_FIELD_CHANCE = 0.5f;
	protected static final float STELLAR_RING_CHANCE = 0.3f;
	protected static final float STAR_RANDOM_OFFSET = 100;
	
	protected ExerelinMarketSetup marketSetup;
	
	protected List<String> factionIds = new ArrayList<>();
	protected List<Integer[]> starPositions = new ArrayList<>();	
	protected EntityData homeworld = null;

	protected List<EntityData> habitablePlanets = new ArrayList<>();
	//protected List<EntityData> habitableMoons = new ArrayList<>();	// TODO
	protected List<EntityData> stations = new ArrayList<>();
	protected List<EntityData> standaloneStations = new ArrayList<>();
	protected Map<String, String> systemToRelay = new HashMap();
	protected Map<String, String> planetToRelay = new HashMap();
	
	protected float numOmnifacs = 0;
	
	public static Logger log = Global.getLogger(ExerelinSectorGen.class);
	
	/*
	public static class ImageFileFilter implements FileFilter
	{
		protected final String[] okFileExtensions = new String[] {"jpg", "jpeg", "png"};
		public boolean accept(File file)
		{
		for (String extension : okFileExtensions)
		{
			if (file.getName().toLowerCase().endsWith(extension))
			{
			return true;
			}
		}
		return false;
		}
	}
	*/
	
	protected void loadBackgrounds()
	{
		starBackgrounds = new ArrayList<>(Arrays.asList(starBackgroundsArray));
		if (ExerelinUtilsFaction.doesFactionExist("blackrock_driveyards"))
		{
			starBackgrounds.add("BR/backgrounds/obsidianBG (2).jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("exigency"))
		{
		}
		if (ExerelinUtilsFaction.doesFactionExist("hiigaran_descendants"))
		{
			starBackgrounds.add("HD/backgrounds/hii_background.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("interstellarimperium"))
		{
			starBackgrounds.add("imperium/backgrounds/ii_corsica.jpg");
			starBackgrounds.add("imperium/backgrounds/ii_thracia.png");
		}
		if (ExerelinUtilsFaction.doesFactionExist("mayorate"))
		{
			starBackgrounds.add("ilk/backgrounds/ilk_background2.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("neutrinocorp"))
		{
			//starBackgrounds.add("neut/backgrounds/CoronaAustralis.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("pn_colony"))
		{
			starBackgrounds.add("backgrounds/tolpbg.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("SCY"))
		{
			starBackgrounds.add("SCY/backgrounds/SCY_acheron.jpg");
			starBackgrounds.add("SCY/backgrounds/SCY_tartarus.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("shadow_industry"))
		{
			starBackgrounds.add("backgrounds/anarbg.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("spire"))
		{
			starBackgrounds.add("AIWar/backgrounds/gemstone_alt.jpg");
		}
		if (ExerelinUtils.isSSPInstalled())
		{
			starBackgrounds.add("ssp/backgrounds/ssp_arcade.png");
			starBackgrounds.add("ssp/backgrounds/ssp_atopthemountain.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_conflictofinterest.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_corporateindirection.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_overreachingexpansion.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("templars"))
		{
			starBackgrounds.add("templars/backgrounds/tem_atallcosts_background.jpg");
			starBackgrounds.add("templars/backgrounds/tem_excommunication_background.jpg");
			starBackgrounds.add("templars/backgrounds/tem_massacre_background.jpg");
			starBackgrounds.add("templars/backgrounds/tem_smite_background.jpg");
		}
		if (ExerelinUtilsFaction.doesFactionExist("valkyrian"))
		{
			starBackgrounds.add("backgrounds/valk_extra_background.jpg");
		}
		
		// prepend "graphics/" to item paths
		for (int i = 0; i < starBackgrounds.size(); i++)
		{
			starBackgrounds.set(i, "graphics/" + starBackgrounds.get(i));
		}
	}
	
	protected void loadNebulaMaps()
	{
		nebulaMaps.clear();
		nebulaMaps.add("eos_nebula.png");
		nebulaMaps.add("valhalla_nebula.png");
		nebulaMaps.add("hybrasil_nebula.png");
		nebulaMaps.add("Nexerelin/gemstone_nebula.png");
		
		if (ExerelinUtilsFaction.doesFactionExist("blackrock_driveyards"))
		{
			nebulaMaps.add("gneiss_nebula.png");
		}
		if (ExerelinUtilsFaction.doesFactionExist("interstellarimperium"))
		{
			nebulaMaps.add("ii_thracia_nebula.png");
		}
		if (ExerelinUtilsFaction.doesFactionExist("templars"))
		{
			nebulaMaps.add("tem_antioch_nebula_1.png");
			nebulaMaps.add("tem_antioch_nebula_2.png");
			nebulaMaps.add("tem_antioch_nebula_3.png");
		}
		for (int i = 0; i < nebulaMaps.size(); i++)
		{
			nebulaMaps.set(i, "data/campaign/terrain/" + nebulaMaps.get(i));
		}
	}

	protected String getRandomFaction()
	{
		return (String) ExerelinUtils.getRandomListElement(factionIds);
	}
	
	protected List<String> getStartingFactions()
	{
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		List<String> availableFactions = setupData.getAvailableFactions();
		int wantedFactionNum = setupData.numStartFactions;
		if (wantedFactionNum <= 0) {
			availableFactions.add(Factions.INDEPENDENT);
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
		
		factions.add(Factions.PIRATES);
		availableFactions.remove(Factions.PIRATES);
		factions.add(Factions.INDEPENDENT);
		availableFactions.remove(Factions.INDEPENDENT);
		
		availableFactions.remove("player_npc");
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		addListToPicker(availableFactions, picker);
		
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
	
	protected void resetVars()
	{
		habitablePlanets.clear();
		//habitableMoons.clear();
		stations.clear();
		standaloneStations.clear();
		homeworld = null;
		systemToRelay.clear();
		planetToRelay.clear();
		ExerelinSetupData.getInstance().resetAvailableFactions();
		factionIds = getStartingFactions();
		numOmnifacs = 0;
		marketSetup = new ExerelinMarketSetup(this);
	}
	
	protected void addListToPicker(List list, WeightedRandomPicker picker)
	{
		for (Object object : list)
		{
			picker.add(object);
		}
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
			
			if (factionIds.contains("citadeldefenders"))
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
		
	protected void addOmnifactory(SectorAPI sector, int index)
	{
		if (!ExerelinSetupData.getInstance().omnifactoryPresent) return;

		SectorEntityToken toOrbit = null;
		//log.info("Randomized omnifac location: " + ExerelinSetupData.getInstance().randomOmnifactoryLocation);
		boolean random = ExerelinSetupData.getInstance().randomOmnifactoryLocation;
		if (numOmnifacs > 0) random = true;
		
		if (random)
		{
			List<StarSystemAPI> systems = new ArrayList(sector.getStarSystems());
			Collections.shuffle(systems);
			for (StarSystemAPI system : systems)
			{
				CollectionFilter planetFilter = new OmnifacFilter(system); 
				List planets = CollectionUtils.filter(system.getPlanets(), planetFilter);
				if (!planets.isEmpty())
				{
					Collections.shuffle(planets);
					toOrbit = (SectorEntityToken)planets.get(0);
				}
			}
		}
		
		if (toOrbit == null)
		{
			// Corvus mode: try to place Omnifactory in starting system
			if (ExerelinSetupData.getInstance().corvusMode) {
				do {
					ExerelinCorvusLocations.SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(PlayerFactionStore.getPlayerFactionIdNGC());
					if (spawnPoint == null) break;
					// orbit homeworld proper; too much risk of double stations or other such silliness?
					String entityId = spawnPoint.entityId;
					if (entityId != null) {
						SectorEntityToken entity = Global.getSector().getEntityById(entityId);
						if (entity != null && entity instanceof PlanetAPI)
						{
							toOrbit = entity;
							break;
						}
					}
					
					// place at random location in same system
					StarSystemAPI system = Global.getSector().getStarSystem(spawnPoint.systemName);
					if (system == null) break;
					
					CollectionFilter planetFilter = new OmnifacFilter(system); 
					List planets = CollectionUtils.filter(system.getPlanets(), planetFilter);
					if (!planets.isEmpty())
					{
						Collections.shuffle(planets);
						toOrbit = (SectorEntityToken)planets.get(0);
					}
				} while (false);
			}
		}
		
		if (toOrbit == null)
		{
			if (ExerelinSetupData.getInstance().corvusMode) toOrbit = sector.getEntityById("corvus_IV");
			else toOrbit = homeworld.entity;
		}
		
		LocationAPI system = toOrbit.getContainingLocation();
		log.info("Placing Omnifactory around " + toOrbit.getName() + ", in the " + system.getName());
		String image = (String) ExerelinUtils.getRandomListElement(stationImages);
		String entityName = "omnifactory" + index;
		SectorEntityToken omnifac = system.addCustomEntity(entityName, "Omnifactory", image, "neutral");
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		if (toOrbit instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)toOrbit;
			if (planet.isStar()) 
			{
				orbitDistance = radius + MathUtils.getRandomNumberInRange(3000, 12000);
			}
		}
		omnifac.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(toOrbit, orbitDistance));
		omnifac.setInteractionImage("illustrations", "abandoned_station");
		omnifac.setCustomDescriptionId("omnifactory");

		MarketAPI market = Global.getFactory().createMarket(entityName /*+_market"*/, "Omnifactory", 0);
		SharedData.getData().getMarketsWithoutPatrolSpawn().add(entityName);
		SharedData.getData().getMarketsWithoutTradeFleetSpawn().add(entityName);
		market.setPrimaryEntity(omnifac);
		market.setFactionId(Factions.NEUTRAL);
		market.addCondition(Conditions.ABANDONED_STATION);
		omnifac.setMarket(market);
		sector.getEconomy().addMarket(market);
		
		omnifac.setFaction(Factions.NEUTRAL);
		omnifac.addTag("omnifactory");
		
		OmniFac.initOmnifactory(omnifac);
	}
	
	protected void addPrismMarket(SectorAPI sector)
	{
		if (!ExerelinSetupData.getInstance().prismMarketPresent) return;
		
		SectorEntityToken prismEntity;
		
		if (ExerelinSetupData.getInstance().numSystems == 1)
		{
			SectorEntityToken toOrbit = homeworld.primary.entity;
			float radius = toOrbit.getRadius();
			float orbitDistance = radius + 150;
			if (toOrbit instanceof PlanetAPI)
			{
				PlanetAPI planet = (PlanetAPI)toOrbit;
				if (planet.isStar()) 
				{
					orbitDistance = radius + MathUtils.getRandomNumberInRange(2000, 2500);
				}
			}
			prismEntity = toOrbit.getContainingLocation().addCustomEntity("prismFreeport", "Prism Freeport", "exerelin_freeport_type", "independent");
			prismEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(toOrbit, orbitDistance));
		}
		else
		{
			LocationAPI hyperspace = sector.getHyperspace();
			prismEntity = hyperspace.addCustomEntity("prismFreeport", "Prism Freeport", "exerelin_freeport_type", "independent");
			float xpos = 2000;
			if (!ExerelinSetupData.getInstance().corvusMode) xpos = -2000;
			prismEntity.setCircularOrbitWithSpin(hyperspace.createToken(xpos, 0), getRandomAngle(), 150, 60, 30, 30);
		}
		
		/*
		EntityData data = new EntityData(null);
		data.name = "Prism Freeport";
		data.type = EntityType.STATION;
		data.forceMarketSize = 4;
		
		MarketAPI market = addMarketToEntity(prismEntity, data, "independent");
		*/

		MarketAPI market = Global.getFactory().createMarket("prismFreeport" /*+ "_market"*/, "Prism Freeport", 5);
		market.setFactionId("independent");
		market.addCondition(Conditions.POPULATION_5);
		market.addCondition(Conditions.SPACEPORT);
		market.addCondition("exerelin_recycling_plant");
		//market.addCondition("exerelin_recycling_plant");
		market.addCondition("exerelin_hydroponics");
		market.addCondition("exerelin_hydroponics");
		market.addCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
		market.addCondition(Conditions.TRADE_CENTER);
		market.addCondition(Conditions.STEALTH_MINEFIELDS);
		market.addCondition(Conditions.CRYOSANCTUM);
		market.addCondition(Conditions.MILITARY_BASE);
		market.addCondition(Conditions.FREE_PORT);
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		
		marketSetup.addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("default_tariff", 0.2f);
		market.getTariff().modifyMult("isFreeMarket", 0.5f);
		market.addSubmarket("exerelin_prismMarket");
		market.setPrimaryEntity(prismEntity);
		prismEntity.setMarket(market);
		prismEntity.setFaction("independent");
		sector.getEconomy().addMarket(market);
		
		//pickEntityInteractionImage(prismEntity, market, "", EntityType.STATION);
		//prismEntity.setInteractionImage("illustrations", "space_bar");
		prismEntity.setCustomDescriptionId("exerelin_prismFreeport");
	}
	
	protected void addAvestaStation(SectorAPI sector, StarSystemAPI system)
	{
		SectorEntityToken avestaEntity;
		
		if (ExerelinSetupData.getInstance().numSystems == 1)
		{
			SectorEntityToken toOrbit = system.getStar();
			float radius = toOrbit.getRadius();
			float orbitDistance = radius + MathUtils.getRandomNumberInRange(2000, 2500);
			avestaEntity = toOrbit.getContainingLocation().addCustomEntity("exipirated_avesta", "Avesta Station", "exipirated_avesta_station", "exipirated");
			avestaEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(toOrbit, orbitDistance));
		}
		else
		{
			LocationAPI hyperspace = sector.getHyperspace();
			SectorEntityToken toOrbit = system.getHyperspaceAnchor();
			avestaEntity = hyperspace.addCustomEntity("exipirated_avesta", "Avesta Station", "exipirated_avesta_station", "exipirated");
			//avestaEntity.setCircularOrbitWithSpin(toOrbit, getRandomAngle(), 5000, 60, 30, 30);
			float orbitDist = 4500;
			float period = getOrbitalPeriod(500, orbitDist, 2);
			setOrbit(avestaEntity, toOrbit, 4500, true, getRandomAngle(), period);
		}
		
		/*
		EntityData data = new EntityData(null);
		data.name = "Prism Freeport";
		data.type = EntityType.STATION;
		data.forceMarketSize = 4;
		
		MarketAPI market = addMarketToEntity(avestaEntity, data, "independent");
		*/

		MarketAPI market = Global.getFactory().createMarket("exipirated_avesta" /*+ "_market"*/, "Avesta Station", 5);
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
		
		market.getTariff().modifyFlat("default_tariff", 0.2f);
		market.getTariff().modifyMult("isFreeMarket", 0.5f);
		market.setPrimaryEntity(avestaEntity);
		avestaEntity.setMarket(market);
		avestaEntity.setFaction("exipirated");
		sector.getEconomy().addMarket(market);
	}
	
	/*
	protected void addShanghai(SectorEntityToken toOrbit)
	{	
		float radius = toOrbit.getRadius();
		float orbitRadius = radius + 150;
		SectorEntityToken shanghaiEntity = toOrbit.getContainingLocation().addCustomEntity("tiandong_shanghai", "Shanghai", "tiandong_shanghai", "tiandong");
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitRadius, getOrbitalPeriod(toOrbit, orbitRadius));
		
		//EntityData data = new EntityData(null);
		//data.name = "Shanghai";
		//data.type = EntityType.STATION;
		//data.forceMarketSize = 4;
		
		//MarketAPI market = addMarketToEntity(shanghaiEntity, data, "independent");

		MarketAPI market = Global.getFactory().createMarket("tiandong_shanghai" + "_market", "Avesta Station", 4);
		market.setFactionId("tiandong");
		market.addCondition(Conditions.POPULATION_5);
		market.addCondition(Conditions.ORBITAL_STATION);
		market.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
		for (int i=0;i<6; i++)
			market.addCondition(Conditions.ORE_COMPLEX);
		market.addCondition(Conditions.ORE_REFINING_COMPLEX);
		market.addCondition(Conditions.ORE_REFINING_COMPLEX);
		market.addCondition(Conditions.TRADE_CENTER);
		market.addCondition(Conditions.SHIPBREAKING_CENTER);
		
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.GENERIC_MILITARY);
		market.addSubmarket("tiandong_retrofit");
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		
		addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("default_tariff", 0.2f);
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
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(toOrbit, orbitDistance));
		
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
		shanghaiEntity.setCustomDescriptionId("tiandong_shanghai");
	}
	
	@Override
	public void generate(SectorAPI sector)
	{
		log.info("Starting sector generation...");
		// load planet/star names from config
		try {
			JSONObject planetConfig = Global.getSettings().loadJSON(PLANET_NAMES_FILE);
			
			JSONArray systemNames = planetConfig.getJSONArray("stars");
			possibleSystemNames = new String[systemNames.length()];
			for (int i = 0; i < systemNames.length(); i++)
				possibleSystemNames[i] = systemNames.getString(i);
			
			JSONArray planetNames = planetConfig.getJSONArray("planets");
			possiblePlanetNames = new String[planetNames.length()];
			for (int i = 0; i < planetNames.length(); i++)
				possiblePlanetNames[i] = planetNames.getString(i);
			
			JSONArray stationNames = planetConfig.getJSONArray("stations");
			possibleStationNames = new String[stationNames.length()];
			for (int i = 0; i < stationNames.length(); i++)
				possibleStationNames[i] = stationNames.getString(i);
				
			possibleSystemNamesList = new ArrayList(Arrays.asList(possibleSystemNames));
			possiblePlanetNamesList = new ArrayList(Arrays.asList(possiblePlanetNames));
			possibleStationNamesList = new ArrayList(Arrays.asList(possibleStationNames));
		} catch (JSONException | IOException ex) {
			log.error(ex);
		}
		
		log.info("Loading backgrounds");
		loadBackgrounds();
		
		log.info("Resetting vars");
		resetVars();
		
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean corvusMode = setupData.corvusMode;
		
		if (!corvusMode)
		{
			int numSystems = setupData.numSystems;
			int numSystemsEmpty = setupData.numSystemsEmpty;
			
			// stars will be distributed in a concentric pattern
			/*
			float angle = 0;
			float distance = 0;
			for(int i = 0; i < numSystems; i ++)
			{
				angle += MathUtils.getRandomNumberInRange((float)Math.PI/4, (float)Math.PI);
				float increment = MathUtils.getRandomNumberInRange(250, 1000) + MathUtils.getRandomNumberInRange(250, 1000);
				distance += increment * ((8f/(float)numSystems) * 0.75f + 0.25f);   // put stars closer together if there are a lot of them
				int x = (int)(Math.sin(angle) * distance);
				int y = (int)(Math.cos(angle) * distance);
				starPositions.add(new Integer[] {x, y});
			}
			Collections.shuffle(starPositions);
			*/
			
			WeightedRandomPicker<Vector2f> picker = new WeightedRandomPicker<>();
			addListToPicker(StarLocations.SPOT, picker);
			if (StarLocations.SPOT.size() < numSystems + numSystemsEmpty)
				addListToPicker(StarLocations.SPOT_EXTENDED, picker);
			
			for(int i = 0; i < numSystems + numSystemsEmpty; i ++)
			{
				Vector2f pos = picker.pickAndRemove();
				int x = (int)pos.x;
				int y = (int)pos.y;
				// offset a bit
				x += MathUtils.getRandomNumberInRange(-STAR_RANDOM_OFFSET, STAR_RANDOM_OFFSET) + MathUtils.getRandomNumberInRange(-STAR_RANDOM_OFFSET, STAR_RANDOM_OFFSET);
				y += MathUtils.getRandomNumberInRange(-STAR_RANDOM_OFFSET, STAR_RANDOM_OFFSET) + MathUtils.getRandomNumberInRange(-STAR_RANDOM_OFFSET, STAR_RANDOM_OFFSET);
				
				// map is rotated 180°in non-Corvus mode so adjust accordingly
				starPositions.add(new Integer[] {-x, -y});
			}
			
			// build systems
			log.info("Building systems");
			for(int i = 0; i < numSystems; i ++)
				buildSystem(sector, i, true);
			for(int i = numSystems; i < numSystems + numSystemsEmpty; i++)
				buildSystem(sector, i, false);
			
			log.info("Populating sector");
			populateSector(sector);
		}
		else
		{
			VanillaSystemsGenerator.generate();
		}
		
		// use vanilla hyperspace map
		String hyperMap = "data/campaign/terrain/hyperspace_map.png";
		if (!corvusMode)
		{
			hyperMap = "data/campaign/terrain/Nexerelin/hyperspace_map_rot.png";
		}
		SectorEntityToken deep_hyperspace = Misc.addNebulaFromPNG(hyperMap,
			  0, 0, // center of nebula
			  sector.getHyperspace(), // location to add to
			  "terrain", "deep_hyperspace", // "nebula_blue", // texture to use, uses xxx_map for map
			  4, 4, Terrain.HYPERSPACE); // number of cells in texture
		
		for (int i=0; i<OmniFacSettings.getNumberOfFactories(); i++) // TODO: use Omnifactory's numberOfFactories setting when it's supported
			addOmnifactory(sector, i);
		addPrismMarket(sector);
		
		final String selectedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);
		
		log.info("Adding scripts and plugins");
		sector.addScript(new ExerelinCoreScript());
		sector.registerPlugin(new ExerelinCoreCampaignPlugin());
		
		if (!ExerelinUtils.isSSPInstalled())
		{
			sector.addScript(new CoreEventProbabilityManager());
		}
		sector.addScript(new EconomyFleetManager());
		sector.addScript(new MercFleetManager());
		sector.addScript(new LuddicPathFleetManager());
		sector.addScript(new PirateFleetManager());
		sector.addScript(new BountyPirateFleetManager());
		sector.addScript(new MarketProcurementMissionCreator());
		sector.addScript(new FactionCommissionMissionCreator());	// not really needed; game auto-adds it on load
		
		//sector.addScript(new EconomyLogger());
		
		sector.addScript(SectorManager.create());
		sector.addScript(DiplomacyManager.create());
		sector.addScript(InvasionFleetManager.create());
		sector.addScript(ResponseFleetManager.create());
		sector.addScript(MiningFleetManager.create());
		sector.addScript(CovertOpsManager.create());
		sector.addScript(AllianceManager.create());
		StatsTracker.create();
		
		DiplomacyManager.setRandomFactionRelationships(setupData.randomStartRelationships);
		if (!corvusMode) 
		{
			SectorManager.reinitLiveFactions();
			DiplomacyManager.initFactionRelationships(false);
			SectorManager.setHomeworld(homeworld.entity);
		}
		
		SectorManager.setSystemToRelayMap(systemToRelay);
		SectorManager.setPlanetToRelayMap(planetToRelay);
		SectorManager.setCorvusMode(corvusMode);
		SectorManager.setHardMode(setupData.hardMode);
		SectorManager.setFreeStart(setupData.freeStart);
		
		// Remove any data stored in ExerelinSetupData
		//resetVars();
		//ExerelinSetupData.resetInstance();
		
		log.info("Finished sector generation");
	}
	
	// =========================================================================
	// Utility functions
	
	public float getOrbitalPeriod(float primaryRadius, float orbitRadius, float density)
	{
		primaryRadius *= 0.01;
		orbitRadius *= 1/62.5;	// realistic would be 1/50 but the planets orbit rather too slowly then
		
		float mass = (float)Math.floor(4f / 3f * Math.PI * Math.pow(primaryRadius, 3));
		mass *= density;
		float radiusCubed = (float)Math.pow(orbitRadius, 3);
		float period = (float)(2 * Math.PI * Math.sqrt(radiusCubed/mass) * 2);
		
		if (Math.random() < REVERSE_ORBIT_CHANCE) period *=-1;
		
		return period;
	}
	
	public float getOrbitalPeriod(SectorEntityToken primary, float orbitRadius)
	{
		return getOrbitalPeriod(primary.getRadius(), orbitRadius, getDensity(primary));
	}
	
	public float getDensity(SectorEntityToken primary)
	{
		if (primary instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)primary;
			if (planet.getTypeId().equals("star_dark")) return 8;
			else if (planet.isStar()) return 0.5f;
			else if (planet.isGasGiant()) return 0.5f;
		}
		return 2;
	}
	
	public float getRandomAngle()
	{
		return MathUtils.getRandomNumberInRange(0f, 360f);
	}
	
	/**
	 * @param entity Token whose orbit is to be set.
	 * @param primary Token to orbit around.
	 * @param orbitRadius
	 * @param isEllipse
	 * @param ellipseAngle
	 * @param orbitPeriod
	 * @return The starting angle on the orbit
	 */
	protected float setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, 
			boolean isEllipse, float ellipseAngle, float orbitPeriod)
	{
		return setOrbit(entity, primary, orbitRadius, getRandomAngle(), 
				isEllipse, ellipseAngle, MathUtils.getRandomNumberInRange(1f, 1.2f), orbitPeriod);
	}
	
	/**
	 * @param entity Token whose orbit is to be set.
	 * @param primary Token to orbit around.
	 * @param orbitRadius
	 * @param angle The angle (in degrees) that the orbit will begin at.
     *                     0 degrees = right - this is not relative to
     *                     {@code ellipseAngle}.
	 * @param isEllipse
	 * @param ellipseAngle
	 * @param ellipseMult Multiplies radius to get semi-major axis,
	 *						divides radius to get semi-minor axis.
	 * @param orbitPeriod
	 * @return The starting angle on the orbit
	 */
	protected float setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, float angle, 
			boolean isEllipse, float ellipseAngle, float ellipseMult, float orbitPeriod)
	{
		if (isEllipse)
		{
			float semiMajor = (int)(orbitRadius * ellipseMult);
			float semiMinor = (int)(orbitRadius / ellipseMult);
			EllipticalOrbit ellipseOrbit = new EllipticalOrbit(primary, angle, semiMinor, semiMajor, ellipseAngle, orbitPeriod);
			entity.setOrbit(ellipseOrbit);
			return angle;
		}
		else
		{
			entity.setCircularOrbit(primary, angle, orbitRadius, orbitPeriod);
			return angle;
		}
	}
	
	/**
	 * Makes one entity orbit at another entity's Lagrangian points.
	 * @param orbiter The entity whose orbit is being set
	 * @param m1 Larger mass (e.g. the star)
	 * @param m2 Smaller mass (e.g. the planet)
	 * @param point 1 - 5 = L1 to L5, other values randomize between L4 and L5
	 * @param m2Angle The starting angle of {@code m2} in its orbit
	 * @param m2OrbitRadius The orbit radius of {@code m2} in its orbit around {@code m1} 
	 * @param myOrbitRadius The orbit radius of {@code orbiter} in its orbit around {@code m2} (only applies to L1 and L2)
	 * @param orbitPeriod The time {@code m2} takes to orbit {@code m1} 
	 * @param isEllipse Is this orbit elliptic?
	 * @param ellipseAngle Angle of the ellipse orbit
	 * @param ellipseMult Used to calculate the ellipse's semi-major and semi-minor axes
	 */
	protected void setLagrangeOrbit(SectorEntityToken orbiter, SectorEntityToken m1, SectorEntityToken m2, int point, 
			float m2Angle, float m2OrbitRadius, float myOrbitRadius, float orbitPeriod, boolean isEllipse, float ellipseAngle, float ellipseMult)
	{
		if (point <= 0 || point > 5)
		{
			if (Math.random() < 0.5) point = 4;
			else point = 5;
		}
		//log.info("Setting Lagrange orbit for " + orbiter.getName() + " at point " + point);
		switch (point) {
			case 1:
			case 2:
				float angle = m2Angle;
				if (point == 1) angle += 180;
				if (!isEllipse) orbiter.setCircularOrbit(m2, angle, myOrbitRadius, orbitPeriod);
				else setOrbit(orbiter, m2, myOrbitRadius, angle, isEllipse, ellipseAngle, ellipseMult, orbitPeriod);
				break;
			case 3:
				if (!isEllipse) orbiter.setCircularOrbit(m1, m2Angle + 180, m2OrbitRadius, orbitPeriod);
				else setOrbit(orbiter, m1, m2OrbitRadius, m2Angle + 180, isEllipse, ellipseAngle, ellipseMult, orbitPeriod);
				break;
			case 4:
			case 5:
				float offset = -60;
				if (point == 5) offset = 60;

				if (!isEllipse) orbiter.setCircularOrbit(m1, m2Angle + offset, m2OrbitRadius, orbitPeriod);
				else setOrbit(orbiter, m1, m2OrbitRadius, m2Angle + offset, isEllipse, ellipseAngle, ellipseMult, orbitPeriod);
				break;
		}
	}
	
	/**
	 *	Given a list of EntityDatas representing planets, and min/max orbit radius, 
	 *  find an orbit that avoids overlapping with that of any planet
	 */
	protected float getRandomOrbitRadiusBetweenPlanets(List<EntityData> planets, float minDist, float maxDist)
	{		
		float orbitRadius = 0;
		List<EntityData> validPlanets = new ArrayList<>();
		for (EntityData data : planets)
		{
			if (data.orbitRadius < minDist) continue;
			if (data.orbitRadius > maxDist) continue;
			validPlanets.add(data);
		}
		if (validPlanets.isEmpty())
			return (MathUtils.getRandomNumberInRange(minDist, maxDist) + MathUtils.getRandomNumberInRange(minDist, maxDist))/2;
		
		int index = 0;
		if (validPlanets.size() > 1) index = MathUtils.getRandomNumberInRange(0, validPlanets.size() - 1);
		float min = minDist;
		float max = maxDist;
		EntityData planet = validPlanets.get(index);
		if (index == 0)
		{
			if (validPlanets.size() > 1)
			{
				EntityData nextPlanet = validPlanets.get(1);
				max = nextPlanet.orbitRadius - nextPlanet.entity.getRadius()*2;
			}
			min = Math.max(minDist, planet.primary.entity.getRadius()*2);
		}
		else if (index == validPlanets.size() - 1)
		{
			min = planet.orbitRadius + planet.entity.getRadius()*2;
		}
		else
		{
			EntityData prevPlanet = validPlanets.get(index - 1);
			min = prevPlanet.orbitRadius + prevPlanet.entity.getRadius()*2;
			max = planet.orbitRadius - planet.entity.getRadius()*2;
		}
		if (min > max)
		{
			float tempMin = min;
			min = max;
			max = tempMin;
		}
		orbitRadius = MathUtils.getRandomNumberInRange(min, max) + MathUtils.getRandomNumberInRange(min, max);
		orbitRadius /= 2;
		
		return orbitRadius;
	}
	
	public void addAsteroidBelt(LocationAPI system, SectorEntityToken planet, int numAsteroids, float orbitRadius, float width, float minOrbitDays, float maxOrbitDays)
	{
		// since we can't easily store belts' orbital periods at present, make sure asteroids all orbit in the same direction
		if (minOrbitDays < 0) minOrbitDays *= -1;
		if (maxOrbitDays < 0) maxOrbitDays *= -1;
		
		system.addAsteroidBelt(planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
		system.addRingBand(planet, "misc", "rings1", 256f, 2, Color.white, 256f, orbitRadius + width/4, (minOrbitDays + maxOrbitDays)/2);
		system.addRingBand(planet, "misc", "rings1", 256f, 2, Color.white, 256f, orbitRadius - width/4, (minOrbitDays + maxOrbitDays)/2);
	}
	
	// end utility functions
	// =========================================================================
	
	protected SectorEntityToken makeStation(EntityData data, String factionId)
	{
		float angle = getRandomAngle();
		int orbitRadius = 200;
		PlanetAPI planet = (PlanetAPI)data.primary.entity;
		if (data.primary.type == EntityType.MOON)
			orbitRadius = 150;
		else if (planet.isGasGiant())
			orbitRadius = 500;
		else if (planet.isStar())
			orbitRadius = (int)data.orbitRadius;
		orbitRadius += planet.getRadius();
		
		data.orbitRadius = orbitRadius;

		float orbitDays = getOrbitalPeriod(planet, orbitRadius);
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
			standaloneStations.add(data);
		}
		pickEntityInteractionImage(newStation, newStation.getMarket(), planet.getTypeId(), EntityType.STATION);
		newStation.setCustomDescriptionId("orbital_station_default");
		
		data.entity = newStation;		
		return newStation;
	}
	
	public void populateSector(SectorAPI sector)
	{
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>();
		List<String> factions = new ArrayList<>(factionIds);
		factions.remove("player_npc");  // player NPC faction only gets homeworld (if applicable)
		addListToPicker(factions, factionPicker);
		boolean hqsSpawned = false;
		
		// before we do anything else give the "homeworld" to our faction
		if (!ExerelinSetupData.getInstance().freeStart)
		{
			String alignedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
			homeworld.isHQ = true;
			MarketAPI homeMarket = marketSetup.addMarketToEntity(homeworld, alignedFactionId);
			SectorEntityToken relay = sector.getEntityById(systemToRelay.get(homeworld.starSystem.getId()));
			relay.setFaction(alignedFactionId);
			pickEntityInteractionImage(homeworld.entity, homeworld.entity.getMarket(), homeworld.planetType, homeworld.type);
			habitablePlanets.remove(homeworld);
			factionPicker.remove(alignedFactionId);
			
			StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
			plugin.setPlayerPaidToUnlock(true);
			
			if (alignedFactionId.equals("exipirated") && ExerelinConfig.enableAvesta)
				addAvestaStation(sector, homeworld.starSystem);
			if (alignedFactionId.equals("tiandong") && ExerelinConfig.enableShanghai)
				addShanghai(homeMarket);
		}
		
		Collections.shuffle(habitablePlanets);
		Collections.shuffle(stations);
		
		// add factions and markets to planets
		for (EntityData habitable : habitablePlanets)
		{
			if (factionPicker.isEmpty()) 
			{
				addListToPicker(factions, factionPicker);
				hqsSpawned = true;
			}
			String factionId = factionPicker.pickAndRemove();
			
			if (!hqsSpawned) 
			{
				ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
				if (!(config != null && config.noHomeworld == true))
					habitable.isHQ = true;
				
				if (factionId.equals("exipirated") && ExerelinConfig.enableAvesta)
					addAvestaStation(sector, habitable.starSystem);
			}
			habitable.market = marketSetup.addMarketToEntity(habitable, factionId);
			if (!hqsSpawned) // separate from the above if block because the market needs to exist first
			{
				if (factionId.equals("tiandong") && ExerelinConfig.enableShanghai)
					addShanghai(habitable.market);
			}
			
			// assign relay
			if (habitable.isCapital)
			{
				SectorEntityToken relay = sector.getEntityById(systemToRelay.get(habitable.starSystem.getId()));
				relay.setFaction(factionId);
			}
			pickEntityInteractionImage(habitable.entity, habitable.entity.getMarket(), habitable.planetType, habitable.type);
		}
		
		// we didn't actually create the stations before, so do so now
		for (EntityData station : stations)
		{
			String factionId = "neutral";
			if (station.primary.entity.getMarket() == null)
			{
				if (factionPicker.isEmpty()) 
				{
					addListToPicker(factions, factionPicker);
				}
				factionId = factionPicker.pickAndRemove();
			}
			else
			{
				factionId = station.primary.entity.getFaction().getId();
			}
			makeStation(station, factionId);
		}
		
		// balance supply/demand by adding/removing relevant market conditions
		List<EntityData> haveMarkets = new ArrayList<>(habitablePlanets);
		haveMarkets.addAll(standaloneStations);
		Collections.sort(haveMarkets, new Comparator<EntityData>() {	// biggest markets first
			@Override
			public int compare(EntityData data1, EntityData data2)
			{
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

	public PlanetAPI createStarToken(int index, String systemId, StarSystemAPI system, String type, float size, boolean isSecondStar)
	{
		float fieldMult = 1;	// corona
		if (type.equals("star_red")) fieldMult = 2;
		if (!isSecondStar) 
		{
			Integer[] pos = (Integer[])starPositions.get(index);
			int x = pos[0];
			int y = pos[1];
			return system.initStar(systemId + "_star", type, size, x, y, 500 * fieldMult);
		}
		else 
		{
			size = Math.min(size * 0.75f, system.getStar().getRadius()*0.8f);
			
			//int systemNameIndex = MathUtils.getRandomNumberInRange(0, possibleSystemNamesList.size() - 1);
			String name = system.getBaseName() + " B";	//possibleSystemNamesList.get(systemNameIndex);
			//possibleSystemNamesList.remove(systemNameIndex);
			
			PlanetAPI star = system.getStar();
			
			float angle = MathUtils.getRandomNumberInRange(1, 360);
			float distance = (BINARY_STAR_DISTANCE + star.getRadius()*5 + size*5) * MathUtils.getRandomNumberInRange(0.95f, 1.1f) ;
			float orbitDays = getOrbitalPeriod(star, distance + star.getRadius());
			
			PlanetAPI planet = system.addPlanet(systemId + "_star_b", star, name, type, angle, size, distance, orbitDays);
			setOrbit(planet, star, distance, true, getRandomAngle(), orbitDays);
			system.addCorona(planet, 300, 2f, 0.1f, 1f);
			
			return planet;
		}
	}
	
	protected PlanetAPI makeStar(int systemIndex, StarSystemAPI system, boolean isSecondStar)
	{
		log.info("Creating star for system " + system.getBaseName());
		PlanetAPI star;
		int starType = MathUtils.getRandomNumberInRange(0, 10);
		String systemId = system.getId();
		
		// TODO refactor to remove endless nested ifs
		if (starType == 0)
		{
			star = createStarToken(systemIndex, systemId, system, "star_yellow", 500f, isSecondStar);
			//system.setLightColor(new Color(255, 180, 180));
		}
		else if(starType == 1)
		{
			star = createStarToken(systemIndex, systemId, system, "star_red", 900f, isSecondStar);
			system.setLightColor(new Color(255, 180, 180));
		}
		else if(starType == 2)
		{
			star = createStarToken(systemIndex, systemId, system, "star_blue", 800f, isSecondStar);
			system.setLightColor(new Color(135,206,250));
		}
		else if(starType == 3)
		{
			star = createStarToken(systemIndex, systemId, system, "star_white", 300f, isSecondStar);
			//system.setLightColor(new Color(185,185,240));
		}
		else if(starType == 4)
		{
			star = createStarToken(systemIndex, systemId, system, "star_orange", 400f, isSecondStar);
			system.setLightColor(new Color(255,220,0));
		}
		else if(starType == 5)
		{
			star = createStarToken(systemIndex, systemId, system, "star_yellowwhite", 400f, isSecondStar);
			system.setLightColor(new Color(255,255,224));
		}
		else if(starType == 6)
		{
			star = createStarToken(systemIndex, systemId, system, "star_bluewhite", 600f, isSecondStar);
			system.setLightColor(new Color(135,206,250));
		}
		else if(starType == 7)
		{
			star = createStarToken(systemIndex, systemId, system, "star_purple", 700f, isSecondStar);
			system.setLightColor(new Color(218,112,214));
		}
		else if(starType == 8)
		{
			star = createStarToken(systemIndex, systemId, system, "star_dark", 100f, isSecondStar);
			system.setLightColor(new Color(105,105,105));
		}
		else if(starType == 9)
		{
			star = createStarToken(systemIndex, systemId, system, "star_green", 600f, isSecondStar);
			system.setLightColor(new Color(240,255,240));
		}
		else
		{
			star = createStarToken(systemIndex, systemId, system, "star_greenwhite", 500f, isSecondStar);
			system.setLightColor(new Color(240,255,240));
		}
		if (!isSecondStar)
			system.setBackgroundTextureFilename( (String) ExerelinUtils.getRandomListElement(starBackgrounds) );
		return star;
	}
	
	protected float getHabitableChance(int planetNum, boolean isMoon)
	{
		float habitableChance = 0.3f;
		if (planetNum == 0) habitableChance = 0.4f;
		else if (planetNum == 1 || planetNum == 3) habitableChance = 0.7f;
		else if (planetNum == 2) habitableChance = 0.9f;
			
		//if (isMoon) habitableChance *= 0.7f;
		if (isMoon) habitableChance = 0.35f;
		
		return habitableChance;
	}
		
	protected void buildSystem(SectorAPI sector, int systemIndex, boolean inhabited)
	{
		// First we make a star system with random name
		int systemNameIndex = MathUtils.getRandomNumberInRange(0, possibleSystemNamesList.size() - 1);
		if (systemIndex == 0) systemNameIndex = 0;	// there is always a starSystem named Exerelin
		StarSystemAPI system = sector.createStarSystem(possibleSystemNamesList.get(systemNameIndex));
		possibleSystemNamesList.remove(systemNameIndex);
		String systemName = system.getName();
		String systemId = system.getId();
		EntityData capital = null;
		
		// Set starSystem/light colour/background
		PlanetAPI star = makeStar(systemIndex, system, false);
		PlanetAPI star2 = null;
		int numPlanetsStar1 = 0;
		int numPlanetsStar2 = 0;
		
		List<EntityData> entities = new ArrayList<>();
		
		EntityData starData = new EntityData(star.getName(), system);
		EntityData starData2 = null;
		starData.entity = star;
		starData.type = EntityType.STAR;
		entities.add(starData);
		
		boolean isBinary = (Math.random() < ExerelinConfig.binarySystemChance) && (!star.getTypeId().equals("star_dark"));
		if (isBinary)
		{
			star2 = makeStar(systemIndex, system, true);
			starData2 = new EntityData(star2.getName(), system);
			starData2.entity = star2;
			starData2.type = EntityType.STAR;
			entities.add(starData2);
		}
		
		float ellipseAngle = getRandomAngle();
		float ellipseMult = 1;	//MathUtils.getRandomNumberInRange(1.05f, 1.2f);
		
		// now let's start seeding planets
		// note that we don't create the PlanetAPI right away, but set up EntityDatas first
		// so we can check that the system has enough of the types of planets we want
		int numBasePlanets;
		int maxPlanets = ExerelinSetupData.getInstance().maxPlanets;
		if(ExerelinSetupData.getInstance().numSystems != 1)
		{
			int minPlanets = ExerelinConfig.minimumPlanets;
			if (minPlanets > maxPlanets) minPlanets = maxPlanets;
			numBasePlanets = MathUtils.getRandomNumberInRange(minPlanets, maxPlanets);
		}
		else
			numBasePlanets = maxPlanets;
		
		int distanceStepping = (ExerelinSetupData.getInstance().baseSystemSize + numBasePlanets * 600)/MathUtils.getRandomNumberInRange(numBasePlanets+1, maxPlanets+1);
		
		if (isBinary) numBasePlanets *= BINARY_SYSTEM_PLANET_MULT;
		
		boolean gasPlanetCreated = false;
		
		int habitableCount = 0;
		List<EntityData> uninhabitables1To4 = new ArrayList<>();

		for(int i = 0; i < numBasePlanets; i = i + 1)
		{
			float habitableChance = getHabitableChance(i, false);

			boolean habitable = false;
			if (inhabited) habitable = Math.random() <= habitableChance;
			EntityData entityData = new EntityData("", system, i+1);
			entityData.habitable = habitable;
			//entityData.primary = starData;

			if (habitable)
			{
				habitableCount++;
			}
			else if (inhabited && i <= 3)
			{
				uninhabitables1To4.add(entityData);
			}
			entities.add(entityData);
		}

		// make sure there are at least two habitable planets
		if (inhabited && habitableCount < 2)
		{
			WeightedRandomPicker<EntityData> picker = new WeightedRandomPicker<>();
			addListToPicker(uninhabitables1To4, picker);
			for (int i=habitableCount; i < 2; i++)
			{
				picker.pickAndRemove().habitable = true;
			}	
		}
		
		List<EntityData> moons = new ArrayList<>();
				
		// okay, now we can actually create the planets
		for(EntityData planetData : entities)
		{
			if (planetData.type == EntityType.STAR) continue;
			
			String planetType = "";
			boolean isGasGiant = false;
			
			String name = "";
			String id = "";
			int planetNameIndex = MathUtils.getRandomNumberInRange(0, possiblePlanetNamesList.size() - 1);
			name = possiblePlanetNamesList.get(planetNameIndex);
			possiblePlanetNamesList.remove(planetNameIndex);
			log.info("Creating planet " + name);
			id = name.replace(' ','_');
			id = id.toLowerCase();
			planetData.name = name;
			
			// binary star handling
			PlanetAPI toOrbit = star;
			boolean orbitsSecondStar = isBinary && Math.random() < 0.3f;
			if (orbitsSecondStar) 
			{
				toOrbit = star2;
				numPlanetsStar2++;
				planetData.planetNumByStar = numPlanetsStar2;
				planetData.primary = starData2;
			}
			else
			{
				numPlanetsStar1++;
				planetData.planetNumByStar = numPlanetsStar1;
				planetData.primary = starData;
			}
			
			// planet type
			if (planetData.habitable)
			{
				planetData.archetype = marketSetup.pickMarketArchetype(false);
				planetType = marketSetup.pickPlanetTypeFromArchetype(planetData.archetype, false);
			}
			else
			{
				float gasGiantChance = 0.45f;
				if (planetData.planetNumByStar == 3) gasGiantChance = 0.3f;
				else if (planetData.planetNumByStar < 3) gasGiantChance = 0;
				
				isGasGiant = Math.random() < gasGiantChance;
				if (isGasGiant) planetType = (String) ExerelinUtils.getRandomArrayElement(planetTypesGasGiant);
				else planetType = (String) ExerelinUtils.getRandomArrayElement(planetTypesUninhabitable);
			}
			
			// orbital mechanics
			float radius;
			float angle = MathUtils.getRandomNumberInRange(1, 360);
			float distance = 3000 + toOrbit.getRadius() + (distanceStepping * (planetData.planetNumByStar - 1) * MathUtils.getRandomNumberInRange(0.75f, 1.25f));
			distance = (int)distance;
			float orbitDays = getOrbitalPeriod(toOrbit, distance);
			planetData.orbitRadius = distance;
			planetData.orbitPeriod = orbitDays;
			
			// size
			if (isGasGiant)
			{
				radius = MathUtils.getRandomNumberInRange(325, 375);
				gasPlanetCreated = true;
			}
			else
				radius = MathUtils.getRandomNumberInRange(150, 250);

			// At least one gas giant per system
			if (!gasPlanetCreated && planetData.planetNum == numBasePlanets)
			{
				planetType = (String) ExerelinUtils.getRandomArrayElement(planetTypesGasGiant);
				radius = MathUtils.getRandomNumberInRange(325, 375);
				gasPlanetCreated = true;
				planetData.habitable = false;
				isGasGiant = true;
			}
			
			// create the planet token
			SectorEntityToken newPlanet = system.addPlanet(id, toOrbit, name, planetType, angle, radius, distance, orbitDays);
			planetData.startAngle = setOrbit(newPlanet, toOrbit, distance, getRandomAngle(), !isBinary, ellipseAngle, ellipseMult, orbitDays);
			planetData.entity = newPlanet;
			planetData.planetType = planetType;
			
			// Now we make moons
			float moonChance = 0.35f;
			if (isGasGiant)
				moonChance = 0.7f;
			if(Math.random() <= moonChance)
			{
				int maxMoons = ExerelinSetupData.getInstance().maxMoonsPerPlanet;
				if (isGasGiant) maxMoons +=1;
				int numMoons = MathUtils.getRandomNumberInRange(0, 1) + MathUtils.getRandomNumberInRange(0, maxMoons - 1);
				distance = newPlanet.getRadius() + MathUtils.getRandomNumberInRange(0, 600);
				float ellipseAngleMoon = getRandomAngle();
				
				for(int j = 0; j < numMoons; j++)
				{
					String ext = "";
					if(j == 0)
						ext = "I";
					if(j == 1)
						ext = "II";
					if(j == 2)
						ext = "III";
					String moonName = name + " " + ext;
					String moonId = name + "_" + ext;
					moonId = moonId.toLowerCase();
					
					EntityData moonData = new EntityData(moonName, system);
					boolean moonInhabitable = false;
					if (inhabited) 
						moonInhabitable = Math.random() < getHabitableChance(planetData.planetNum, true);
					
					if (moonInhabitable)
					{
						moonData.archetype = marketSetup.pickMarketArchetype(false);
						moonData.planetType = marketSetup.pickPlanetTypeFromArchetype(moonData.archetype, true);
					}
					else
						moonData.planetType = (String) ExerelinUtils.getRandomArrayElement(moonTypesUninhabitable);
					
					moonData.primary = planetData;
					moonData.habitable = moonInhabitable;
					moonData.type = EntityType.MOON;		
					
					// moon orbital mechanics
					angle = MathUtils.getRandomNumberInRange(1, 360);
					distance += MathUtils.getRandomNumberInRange(200, 450);
					float moonRadius = MathUtils.getRandomNumberInRange(50, 100);
					orbitDays = getOrbitalPeriod(newPlanet, distance + newPlanet.getRadius());
					
					PlanetAPI newMoon = system.addPlanet(moonId, newPlanet, moonName, moonData.planetType, angle, moonRadius, distance, orbitDays);
					moonData.startAngle = angle;	// setOrbit(newMoon, newPlanet, distance, false, 0, orbitDays);
					log.info("Creating moon " + moonName);
					moonData.entity = newMoon;
					moonData.orbitRadius = distance;
					moonData.orbitPeriod = orbitDays;
					
					// concurrency exception - don't add it direct; add to another list and merge
					//entities.add(moonData);
					moons.add(moonData);
				}
			}
			
			// 20% chance of rings around planet / 50% chance if a gas giant
			float ringChance = (planetType.equalsIgnoreCase("gas_giant") || planetType.equalsIgnoreCase("ice_giant")) ? 0.5f : 0.2f;
			if(Math.random() < ringChance)
			{
				int ringType = MathUtils.getRandomNumberInRange(0,3);
				float radiusMult = MathUtils.getRandomNumberInRange(2, 3.5f);
				int ringRadius = (int)(radius*radiusMult);

				if(ringType == 0)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, ringRadius, 40f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius, 60f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, ringRadius, 80f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 80f, Terrain.RING, null);
				}
				else if (ringType == 1)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, ringRadius, 70f, Terrain.RING, null);
					//system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*2.5), 90f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 110f, Terrain.RING, null);
				}
				else if (ringType == 2)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 70f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 90f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 110f, Terrain.RING, null);
				}
				else if (ringType == 3)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, (int)(radius*radiusMult), 50f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, (int)(radius*radiusMult), 70f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, (int)(radius*radiusMult), 80f, Terrain.RING, null);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 1, Color.white, 256f, (int)(radius*radiusMult*1.25), 90f, Terrain.RING, null);
				}
			}
			// add magnetic field
			if (isGasGiant && Math.random() < MAGNETIC_FIELD_CHANCE)
			{
				addMagneticField(newPlanet);
			}
			
			// add Lagrange asteroids
			if (isGasGiant)
			{
				SectorEntityToken l4asteroids = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldTerrainPlugin.AsteroidFieldParams(
						400f, // min radius
						600f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						name + " L4 Asteroids")); // null for default name
				SectorEntityToken l5asteroids = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldTerrainPlugin.AsteroidFieldParams(
						400f, // min radius
						600f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						name + " L5 Asteroids")); // null for default name
				setLagrangeOrbit(l4asteroids, toOrbit, newPlanet, 4, planetData.startAngle, planetData.orbitRadius, 0, planetData.orbitPeriod, !isBinary, ellipseAngle, ellipseMult);
				setLagrangeOrbit(l5asteroids, toOrbit, newPlanet, 5, planetData.startAngle, planetData.orbitRadius, 0, planetData.orbitPeriod, !isBinary, ellipseAngle, ellipseMult);
			}
		}
		
		// add the moons back to our main entity list
		for (EntityData moon: moons)
		{
			entities.add(moon);
		}
		
		// set sector capital
		WeightedRandomPicker<EntityData> capitalPicker = new WeightedRandomPicker<>();
		for (EntityData entityData : entities)
		{
			if (inhabited && !entityData.habitable) continue;
			float weight = 1f;
			if (entityData.type == EntityType.STAR) continue;
			else if (entityData.type == EntityType.MOON) weight *= 0.001f;
			
			EntityData planetData = entityData;
			if (entityData.type == EntityType.MOON) planetData = entityData.primary;
			if (planetData.planetNum == 2 || planetData.planetNum == 3)
			{
				weight *= 16f;
			}
			if (planetData.primary == starData2) 
			{
				if (systemIndex == 0) weight *= 0;
				else weight *= 0.67f;
			}
			capitalPicker.add(entityData, weight);
		}
		capital = capitalPicker.pick();
		capital.isCapital = true;
		if (systemIndex == 0)
		{
			homeworld = capital;
			//homeworld.isHQ = true;	// only do this when we assign the homeworld to player's faction
			//homeworld.archetype = MarketArchetype.MIXED;
		}

		// Build asteroid belts
		// If the belt orbits a star, add it to a list so that we can seed belter stations later
		WeightedRandomPicker<EntityData> entitiesForAsteroids = new WeightedRandomPicker<>();
		for (EntityData data: entities)
		{
			float weight = 1;
			if (data.type == EntityType.MOON) weight = 0.5f;
			else if (data.type == EntityType.STAR) weight = 2.5f;
			else if (data.planetType.equals("gas_giant") || data.planetType.equals("ice_giant")) weight = 2f;
			entitiesForAsteroids.add(data, weight);
		}
		
		List<Float> starBelts1 = new ArrayList<>();
		List<Float> starBelts2 = new ArrayList<>();
		int numAsteroidBelts;
		if(ExerelinSetupData.getInstance().numSystems != 1)
			numAsteroidBelts = MathUtils.getRandomNumberInRange(ExerelinConfig.minimumAsteroidBelts, ExerelinSetupData.getInstance().maxAsteroidBelts);
		else
			numAsteroidBelts = ExerelinSetupData.getInstance().maxAsteroidBelts;

		for(int j = 0; j < numAsteroidBelts; j = j + 1)
		{
			EntityData entity = entitiesForAsteroids.pickAndRemove();
			if (entity == null) break;
			PlanetAPI planet = (PlanetAPI)(entity.entity);

			float orbitRadius;
			int numAsteroids;

			if (planet.getFullName().contains(" I") || planet.getFullName().contains(" II") || planet.getFullName().contains(" III"))
			{
				orbitRadius = MathUtils.getRandomNumberInRange(250, 350);
				numAsteroids = 5;
			}
			else if(planet.isGasGiant())
			{
				orbitRadius = MathUtils.getRandomNumberInRange(700, 900);
				numAsteroids = 20;
			}
			else if (planet.isStar())
			{
				orbitRadius = getRandomOrbitRadiusBetweenPlanets(entities, 1000 + star.getRadius(), 10000 + star.getRadius());
				numAsteroids = 125;
			}
			else
			{
				orbitRadius = MathUtils.getRandomNumberInRange(400, 550);
				numAsteroids = 15;
			}
			numAsteroids = (int)(numAsteroids * MathUtils.getRandomNumberInRange(0.75f, 1.25f));

			float width = MathUtils.getRandomNumberInRange(160, 200);
			float baseOrbitDays = getOrbitalPeriod(planet, orbitRadius);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			addAsteroidBelt(system, planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
			if (planet == star) starBelts1.add(orbitRadius);
			else if (planet == star2) starBelts2.add(orbitRadius);
			log.info("Added asteroid belt around " + planet.getName());
		}

		// Always put an asteroid belt around the sun
		do {
			float distance = getRandomOrbitRadiusBetweenPlanets(entities, 3000 + star.getRadius(), 10000 + star.getRadius());
			float baseOrbitDays = getOrbitalPeriod(star, distance);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			
			addAsteroidBelt(system, star, 50, distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
			starBelts1.add(distance);
			
			// Another one if medium system size
			if(ExerelinSetupData.getInstance().baseSystemSize > 16000)
			{
				distance = getRandomOrbitRadiusBetweenPlanets(entities, 12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star, distance);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				addAsteroidBelt(system, star, 75, distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
				starBelts1.add(distance);
			}
			// And another one if a large system
			if(ExerelinSetupData.getInstance().baseSystemSize > 32000)
			{
				distance = getRandomOrbitRadiusBetweenPlanets(entities, 18000, 35000);
				baseOrbitDays = getOrbitalPeriod(star, distance);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				addAsteroidBelt(system, star, 100, distance, MathUtils.getRandomNumberInRange(160, 200),  minOrbitDays, maxOrbitDays);
				starBelts1.add(distance);
			}
		} while (false);
		
		for (EntityData entity : entities)
		{
			if (entity.habitable) habitablePlanets.add(entity);
		}
		
		// Build stations
		// Note: to enable special faction stations, we don't actually generate the stations until much later,
		// when we're dealing out planets to factions
		if (inhabited)
		{
			int numStations;
			int maxStations = ExerelinSetupData.getInstance().maxStations;
			if(ExerelinSetupData.getInstance().numSystems != 1)
			{
				int minStations = ExerelinConfig.minimumStations;
				if (minStations > maxStations) minStations = maxStations;
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
				if (picker.isEmpty()) picker.add(starData);
				EntityData primaryData = picker.pickAndRemove();

				EntityData stationData = new EntityData("", system);
				stationData.primary = primaryData;
				stationData.type = EntityType.STATION;
				stationData.archetype = marketSetup.pickMarketArchetype(true);
				if (primaryData.entity == star) stationData.orbitRadius = (Float) ExerelinUtils.getRandomListElement(starBelts1);
				else if (primaryData.entity == star2) 
				{
					// make a belt for binary companion if we don't have one already
					if (starBelts2.isEmpty())
					{
						float distance = getRandomOrbitRadiusBetweenPlanets(entities, 3000 + star.getRadius(), 10000 + star.getRadius());
						float baseOrbitDays = getOrbitalPeriod(star2, distance);
						float minOrbitDays = baseOrbitDays * 0.75f;
						float maxOrbitDays = baseOrbitDays * 1.25f;

						addAsteroidBelt(system, star2, 75, distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
						starBelts2.add(distance);
					}

					stationData.orbitRadius = (Float) ExerelinUtils.getRandomListElement(starBelts2);
				}

				// name our station
				boolean nameOK = false;
				String name = "";
				while(!nameOK)
				{
					name = primaryData.name + " " + (String) ExerelinUtils.getRandomListElement(possibleStationNamesList);
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

		// Build hyperspace exits
		if (true || ExerelinSetupData.getInstance().numSystems > 1)
		{
			// capital jump point
			/*
			SectorEntityToken capitalToken = capital.entity;
			JumpPointAPI capitalJumpPoint = Global.getFactory().createJumpPoint(capitalToken.getId() + "_jump", capitalToken.getName() + " Jumppoint");
			float radius = capitalToken.getRadius();
			float orbitRadius = radius + 250f;
			float orbitDays = getOrbitalPeriod(capitalToken, orbitRadius);
			capitalJumpPoint.setCircularOrbit(capitalToken, getRandomAngle(), orbitRadius, orbitDays);
			capitalJumpPoint.setRelatedPlanet(capitalToken);
			system.addEntity(capitalJumpPoint);
			capitalJumpPoint.setStandardWormholeToHyperspaceVisual();
			*/
			
			// capital L4/L5 jump point
			EntityData jumpLink = capital;
			if (jumpLink.type == EntityType.MOON) jumpLink = jumpLink.primary;	// L4/L5 of the planet instead of the moon
			JumpPointAPI capitalJumpPoint = Global.getFactory().createJumpPoint(jumpLink.entity.getId() + "_jump", jumpLink.name + " Bridge");
			log.info("Creating jump point at " + jumpLink.name + ", has primary? " + (jumpLink.primary != null));
			setLagrangeOrbit(capitalJumpPoint, jumpLink.primary.entity, jumpLink.entity, -1, jumpLink.startAngle, jumpLink.orbitRadius, 0, jumpLink.orbitPeriod, !isBinary, ellipseAngle, ellipseMult);
			system.addEntity(capitalJumpPoint);
			capitalJumpPoint.setStandardWormholeToHyperspaceVisual();
			
			// loose star jump point
			/*
			JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint(star.getId() + "_jump", star.getName() + " Jumppoint");
			orbitRadius = MathUtils.getRandomNumberInRange(2500, 3500) + star.getRadius();
			orbitDays = getOrbitalPeriod(star, orbitRadius);
			setOrbit(jumpPoint, star, orbitRadius, !isBinary, ellipseAngle, orbitDays);
			system.addEntity(jumpPoint);
			jumpPoint.setStandardWormholeToHyperspaceVisual();
			
			// another one for binary companion
			if (isBinary)
			{
				JumpPointAPI jumpPoint2 = Global.getFactory().createJumpPoint(star2.getId() + "_jump", star2.getName() + " Jumppoint");
				orbitRadius = MathUtils.getRandomNumberInRange(2000, 3000) + star.getRadius();
				orbitDays = getOrbitalPeriod(star, orbitRadius);
				setOrbit(jumpPoint2, star2, orbitRadius, !isBinary, ellipseAngle, orbitDays);
				system.addEntity(jumpPoint2);
				jumpPoint2.setStandardWormholeToHyperspaceVisual();
			}
			*/
			
			// for binary systems, add a jump point to the star that the capital does not orbit
			if (isBinary)
			{
				SectorEntityToken secondJumpFocus = star2;
				if (capital.primary == starData2)
					secondJumpFocus = star;
				JumpPointAPI jumpPoint2 = Global.getFactory().createJumpPoint(secondJumpFocus.getId() + "_jump", secondJumpFocus.getName() + " Jumppoint");
				float orbitRadius = getRandomOrbitRadiusBetweenPlanets(entities, 2500 + secondJumpFocus.getRadius(), 5000 + secondJumpFocus.getRadius());
				float orbitPeriod = getOrbitalPeriod(secondJumpFocus, orbitRadius);
				setOrbit(jumpPoint2, secondJumpFocus, orbitRadius, !isBinary, ellipseAngle, orbitPeriod);
				system.addEntity(jumpPoint2);
				jumpPoint2.setStandardWormholeToHyperspaceVisual();
			}
			system.autogenerateHyperspaceJumpPoints(true, true);
		}

		// Build comm relay
		if (inhabited)
		{
			SectorEntityToken relay = system.addCustomEntity(system.getId() + "_relay", // unique id
					system.getBaseName() + " Relay", // name - if null, defaultName from custom_entities.json will be used
					"comm_relay", // type of object, defined in custom_entities.json
					"neutral"); // faction
			float distance = getRandomOrbitRadiusBetweenPlanets(entities, 1200 + star.getRadius(), 3000 + star.getRadius());
			relay.setCircularOrbitPointingDown(star, getRandomAngle(), distance, getOrbitalPeriod(star, distance));
			//setOrbit(relay, star, distance, !isBinary, ellipseAngle, getOrbitalPeriod(star, distance));
			systemToRelay.put(system.getId(), system.getId() + "_relay");
			planetToRelay.put(capital.entity.getId(), system.getId() + "_relay");
		}
		
		// add nebula
		if (Math.random() < NEBULA_CHANCE)
		{
			SectorEntityToken nebula = Misc.addNebulaFromPNG((String)ExerelinUtils.getRandomListElement(nebulaMaps),	// nebula texture
					  0, 0, // center of nebula
					  system, // location to add to
					  "terrain", "nebula_" + (String)ExerelinUtils.getRandomArrayElement(nebulaColors), // texture to use, uses xxx_map for map
					  4, 4); // number of cells in texture
		}
		
		// add stellar ring
		if (Math.random() < STELLAR_RING_CHANCE)
		{
			float distance = getRandomOrbitRadiusBetweenPlanets(entities, 2500 + star.getRadius(), 6000 + star.getRadius());
			// ring (adapted from Magec.java)
			system.addRingBand(star, "misc", "rings1", 256f, 2, Color.white, 256f, distance - 300, 80f);
			system.addRingBand(star, "misc", "rings1", 256f, 3, Color.white, 256f, distance - 100, 100f);
			system.addRingBand(star, "misc", "rings1", 256f, 2, Color.white, 256f, distance + 100, 130f);
			system.addRingBand(star, "misc", "rings1", 256f, 3, Color.white, 256f, distance + 300, 80f);

			// add one ring that covers all of the above
			SectorEntityToken ring = system.addTerrain(Terrain.RING, new BaseRingTerrain.RingParams(600 + 256, distance, null));
			ring.setCircularOrbit(star, 0, 0, 100);
		}
		
		// add dead gate
		if (systemIndex == 0)
		{
			SectorEntityToken gate = system.addCustomEntity(system.getBaseName() + "_gate", // unique id
					 system.getBaseName() + " Gate", // name - if null, defaultName from custom_entities.json will be used
					 "inactive_gate", // type of object, defined in custom_entities.json
					 null); // faction
			float gateDist = MathUtils.getRandomNumberInRange(4000, 7000) + star.getRadius();
			setOrbit(gate, star, gateDist, !isBinary, ellipseAngle, getOrbitalPeriod(star, gateDist));
		}
	}
	
	public void addMagneticField(SectorEntityToken entity)
	{
		LocationAPI loc = entity.getContainingLocation();
		float radius = entity.getRadius();
		float effectRadius = 200;
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)entity;
			if (planet.isStar()) effectRadius = 800f;
		}
		SectorEntityToken field = loc.addTerrain(Terrain.MAGNETIC_FIELD,
			new MagneticFieldTerrainPlugin.MagneticFieldParams(radius + effectRadius, // terrain effect band width 
					(radius + effectRadius)/2f, // terrain effect middle radius
					entity, // entity that it's around
					radius + effectRadius/4, // visual band start
					radius + effectRadius/4 + effectRadius, // visual band end
					new Color(50, 20, 100, 40), // base color
					1f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
					new Color(50, 20, 110, 130),
					new Color(150, 30, 120, 150), 
					new Color(200, 50, 130, 190),
					new Color(250, 70, 150, 240),
					new Color(200, 80, 130, 255),
					new Color(75, 0, 160), 
					new Color(127, 0, 255)
					));
			field.setCircularOrbit(entity, 0, 0, 100);
	}
	
	public static class OmnifacFilter implements CollectionUtils.CollectionFilter<SectorEntityToken>
	{
		final Set<SectorEntityToken> blocked;
		private OmnifacFilter(StarSystemAPI system)
		{
			String alignedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
			blocked = new HashSet<>();
			for (SectorEntityToken planet : system.getPlanets() )
			{
				String factionId = planet.getFaction().getId();

				if (!factionId.equals("neutral") && !factionId.equals(alignedFactionId))
					blocked.add(planet);
				//else
					//log.info("Authorizing planet " + planet.getName() + " (faction " + factionId + ")");
			}
		}

		@Override
		public boolean accept(SectorEntityToken token)
		{
			return !blocked.contains(token);
		}
	}
	
	protected enum EntityType {
		STAR, PLANET, MOON, STATION
	}
	
	protected static class EntityData {
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
		MarketArchetype archetype = MarketArchetype.MIXED;
		int forceMarketSize = -1;
		int planetNum = -1;
		int planetNumByStar = -1;
		float orbitRadius = 0;
		float orbitPeriod = 0;
		float startAngle = 0;
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
	
	// System generation algorithm
	/*
		For each system:
			First create a star using one of the ten possible options picked at random
			If binary system, add another star as a "planet"
	
			Create planets according to the following rules:
				first planet: 40% habitable chance
				second planet: 70% habitable chance
				third planet: 90% habitable chance
				fourth planet: 70% habitable chance, 30% gas giant chance if fail habitability check
				fifth and higher planet: 30% habitable chance, 45% gas giant chance
				last planet will always be gas giant if one hasn't been added yet 
				moon: 40% habitable chance
				if not at least two habitable entities, randomly pick planets 1-4 and force them to be habitable
				designate one habitable planet from these four as system capital
					If this is the first star (Exerelin), mark it as HQ instead (we'll come back to it later)
				Binary systems have 25% more planets; planets will randomly orbit either star
			Don't actually generate PlanetAPIs until all EntityDatas have been created
			If habitable planet/moon, add to list of habitables

			Add random asteroid belts
	
			Next seed stations randomly around planets/moons or in asteroid belts
				If station orbiting uninhabitable, add to list of "independent" stations
				If station orbiting habitable, add to list of "associated" stations

			Now add relay around star, add jump point to system capital, add automatic jump points
			Associate relay with capital and system
					
		Next go through all habitables and assign them to factions
			First off we go to the first star (Exerelin) and give our faction the HQ planet we picked earlier
			Next line up factions (exclude our own faction for this round), pick one at random and remove from list
			Give this faction a habitable planet; add market to it
			If this is the faction's first habitable, make it their headquarters
			If this is a system capital or headquarters, set minimum size accordingly
			Once list is empty, refill with all factions (including ours) again and repeat process
		Repeat until all habitables have been populated
	
		Do that again except for independent stations
	
		Lastly we go through all associated stations
		Associate them with the market of the planet/moon they orbit
	*/
}