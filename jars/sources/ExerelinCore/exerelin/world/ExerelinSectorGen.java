package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
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
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.events.CoreEventProbabilityManager;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.ExerelinCorvusLocations;
import data.scripts.world.ExerelinCorvusLocations.SpawnPointEntry;
import data.scripts.world.corvus.Corvus;
import data.scripts.world.systems.Arcadia;
import data.scripts.world.systems.Askonia;
import data.scripts.world.systems.Eos;
import data.scripts.world.systems.Magec;
import data.scripts.world.systems.SSP_Arcadia;
import data.scripts.world.systems.SSP_Askonia;
import data.scripts.world.systems.SSP_Corvus;
import data.scripts.world.systems.SSP_Eos;
import data.scripts.world.systems.SSP_Magec;
import data.scripts.world.systems.SSP_Valhalla;
import data.scripts.world.systems.Valhalla;
import exerelin.campaign.AllianceManager;
import exerelin.plugins.*;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsCargo;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollectionUtils.CollectionFilter;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.omnifac.OmniFac;
import org.lwjgl.util.vector.Vector2f;

@SuppressWarnings("unchecked")

public class ExerelinSectorGen implements SectorGeneratorPlugin
{
	// NOTE: system names and planet names are overriden by planetNames.json
	private static final String PLANET_NAMES_FILE = "data/config/planetNames.json";
	private static String[] possibleSystemNames = {"Exerelin", "Askar", "Garil", "Yaerol", "Plagris", "Marot", "Caxort", "Laret", "Narbil", "Karit",
		"Raestal", "Bemortis", "Xanador", "Tralor", "Exoral", "Oldat", "Pirata", "Zamaror", "Servator", "Bavartis", "Valore", "Charbor", "Dresnen",
		"Firort", "Haidu", "Jira", "Wesmon", "Uxor"};
	private static String[] possiblePlanetNames = new String[] {"Baresh", "Zaril", "Vardu", "Drewler", "Trilar", "Polres", "Laret", "Erilatir",
		"Nambor", "Zat", "Raqueler", "Garret", "Carashil", "Qwerty", "Azerty", "Tyrian", "Savarra", "Torm", "Gyges", "Camanis", "Ixmucane", "Yar", "Tyrel",
		"Tywin", "Arya", "Sword", "Centuri", "Heaven", "Hell", "Sanctuary", "Hyperion", "Zaphod", "Vagar", "Green", "Blond", "Gabrielle", "Masset",
		"Effecer", "Gunsa", "Patiota", "Rayma", "Origea", "Litsoa", "Bimo", "Plasert", "Pizzart", "Shaper", "Coruscent", "Hoth", "Gibraltar", "Aurora",
		"Darwin", "Mendel", "Crick", "Franklin", "Watson", "Pauling",
		"Rutherford", "Maxwell", "Bohr", "Pauli", "Curie", "Meitner", "Heisenberg", "Feynman"};
	private static String[] possibleStationNames = new String[] {"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge", "Customs", "Nest",
		"Port", "Quey", "Terminal", "Exchange", "View", "Wall", "Habitat", "Shipyard", "Backwater"};
	private static final String[] starBackgroundsArray = new String[]
	{
		"backgrounds/background1.jpg", "backgrounds/background2.jpg", "backgrounds/background3.jpg", "backgrounds/background4.jpg", "backgrounds/background5.jpg",
		"exerelin/backgrounds/blue_background1.jpg", "exerelin/backgrounds/blue_background2.jpg",
		"exerelin/backgrounds/bluewhite_background1.jpg", "exerelin/backgrounds/orange_background1.jpg",
		"exerelin/backgrounds/dark_background1.jpg", "exerelin/backgrounds/dark_background2.jpg",
		"exerelin/backgrounds/green_background1.jpg", "exerelin/backgrounds/green_background2.jpg",
		"exerelin/backgrounds/purple_background1.jpg", "exerelin/backgrounds/purple_background2.jpg",
		"exerelin/backgrounds/white_background1.jpg", "exerelin/backgrounds/white_background2.jpg",
		"backgrounds/2-2.jpg", "backgrounds/2-4.jpg", "backgrounds/3-1.jpg", "backgrounds/4-1.jpg", "backgrounds/4-2.jpg", "backgrounds/5-1.jpg", "backgrounds/5-2.jpg",
		"backgrounds/6-1.jpg", "backgrounds/7-1.jpg", "backgrounds/7-3.jpg", "backgrounds/8-1.jpg", "backgrounds/8-2.jpg", "backgrounds/9-1.jpg", "backgrounds/9-3.jpg",
		"backgrounds/9-4.jpg", "backgrounds/9-5.jpg",
	};
	
	private static ArrayList<String> starBackgrounds = new ArrayList<>(Arrays.asList(starBackgroundsArray));

	private List<String> possibleSystemNamesList = new ArrayList(Arrays.asList(possibleSystemNames));
	private List<String> possiblePlanetNamesList = new ArrayList(Arrays.asList(possiblePlanetNames));
	private List<String> possibleStationNamesList = new ArrayList(Arrays.asList(possibleStationNames));
	
	private static final String[] planetTypes = new String[] {"desert", "jungle", "frozen", "terran", "arid", "water", "rocky_metallic", "rocky_ice", "barren", "barren-bombarded"};
	private static final String[] planetTypesUninhabitable = new String[] {"barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable",
		"gas_giant", "ice_giant", "frozen", "rocky_ice", "radiated", "barren-bombarded"};
	private static final String[] planetTypesGasGiant = new String[] {"gas_giant", "ice_giant"};
	private static final String[] moonTypes = new String[] {"frozen", "barren", "barren-bombarded", "rocky_ice", "rocky_metallic", "desert", "water", "jungle"};
	private static final String[] moonTypesUninhabitable = new String[] {"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice", "radiated", "barren-bombarded"};
	
	private static final Map<String, String[]> stationImages = new HashMap<>();
	
	private List<String> factionIds = new ArrayList<>();
	private List<Integer[]> starPositions = new ArrayList<>();	
	private EntityData homeworld = null;

	private List<EntityData> habitablePlanets = new ArrayList<>();
	private List<EntityData> stations = new ArrayList<>();
	private Map<String, String> systemToRelay = new HashMap();
	private Map<String, String> planetToRelay = new HashMap();
	
	private float numOmnifacs = 0;
	
	public static Logger log = Global.getLogger(ExerelinSectorGen.class);
	
	/*
	public static class ImageFileFilter implements FileFilter
	{
		private final String[] okFileExtensions = new String[] {"jpg", "jpeg", "png"};
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
	 
	static 
	{		
		// station images (FIXME: move to faction config)
		stationImages.put("default", new String[] {"station_side00", "station_side02", "station_side04", "station_jangala_type"});
		stationImages.put("shadow_industry", new String[] {"station_shi_prana","station_shi_med"} );
		stationImages.put("SCY", new String[] {"SCY_overwatchStation_type","SCY_refinery_type", "SCY_processing_type", "SCY_conditioning_type"} );
		stationImages.put("hiigaran_descendants", new String[] {"new_hiigara_type","hiigara_security_type"} );
		stationImages.put("citadeldefenders", new String[] {"station_citadel_type"} );
		stationImages.put("neutrinocorp", new String[] {"neutrino_station_powerplant", "neutrino_station_largeprocessing", "neutrino_station_experimental"} );
		stationImages.put("diableavionics", new String[] {"diableavionics_station_eclipse"} );
		stationImages.put("exipirated", new String[] {"exipirated_avesta_station"} );
	}
	
	private void loadBackgrounds()
	{
		starBackgrounds = new ArrayList<>(Arrays.asList(starBackgroundsArray));
		List<String> factions = Arrays.asList(ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector()));
		if (factions.contains("blackrock_driveyards"))
		{
			starBackgrounds.add("BR/backgrounds/obsidianBG (2).jpg");
		}
		if (factions.contains("exigency"))
		{
		}
		if (factions.contains("hiigaran_descendants"))
		{
			starBackgrounds.add("HD/backgrounds/hii_background.jpg");
		}
		if (factions.contains("interstellarimperium"))
		{
			starBackgrounds.add("imperium/backgrounds/ii_corsica.jpg");
			starBackgrounds.add("imperium/backgrounds/ii_thracia.jpg");
		}
		if (factions.contains("mayorate"))
		{
			starBackgrounds.add("ilk/backgrounds/ilk_background2.jpg");
		}
		if (factions.contains("neutrinocorp"))
		{
			starBackgrounds.add("neut/backgrounds/CoronaAustralis.jpg");
		}
		if (factions.contains("pn_colony"))
		{
			starBackgrounds.add("backgrounds/tolpbg.jpg");
		}
		if (factions.contains("SCY"))
		{
			starBackgrounds.add("SCY/backgrounds/SCY_acheron.jpg");
			starBackgrounds.add("SCY/backgrounds/SCY_acheron.jpg");
		}
		if (factions.contains("shadow_industry"))
		{
			starBackgrounds.add("backgrounds/anarbg.jpg");
		}
		if (ExerelinUtils.isSSPInstalled())
		{
			starBackgrounds.add("ssp/backgrounds/ssp_arcade.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_atopthemountain.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_conflictofinterest.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_corporateindirection.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_overreachingexpansion.jpg");
		}
		if (factions.contains("templars"))
		{
			starBackgrounds.add("templars/backgrounds/tem_atallcosts_background.jpg");
			starBackgrounds.add("templars/backgrounds/tem_excommunication_background.jpg");
			starBackgrounds.add("templars/backgrounds/tem_massacre_background.jpg");
			starBackgrounds.add("templars/backgrounds/tem_smite_background.jpg");
		}
		if (factions.contains("valkyrian"))
		{
			starBackgrounds.add("backgrounds/valk_extra_background.jpg");
		}
		
		// prepend "graphics/" to item paths
		for (int i = 0; i < starBackgrounds.size(); i++)
		{
			starBackgrounds.set(i, "graphics/" + starBackgrounds.get(i));
		}
	}

	private String getRandomFaction()
	{
		return factionIds.get(MathUtils.getRandomNumberInRange(0, factionIds.size()-1));
	}
	
	private void resetVars()
	{
		habitablePlanets = new ArrayList<>();
		stations = new ArrayList<>();
		ExerelinSetupData.getInstance().resetAvailableFactions();
		factionIds = new ArrayList<>( Arrays.asList(ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector())) );
		numOmnifacs = 0;
	}
	
	private void addListToPicker(List list, WeightedRandomPicker picker)
	{
		for (Object object : list)
		{
			picker.add(object);
		}
	}
			
	private void pickEntityInteractionImage(SectorEntityToken entity, MarketAPI market, String planetType, EntityType entityType)
	{
		WeightedRandomPicker<String[]> allowedImages = new WeightedRandomPicker();
		allowedImages.add(new String[]{"illustrations", "cargo_loading"} );
		allowedImages.add(new String[]{"illustrations", "hound_hangar"} );
		allowedImages.add(new String[]{"illustrations", "space_bar"} );

		boolean isStation = (entityType == EntityType.STATION);
		boolean isMoon = (entityType == EntityType.MOON); 
		int size = market.getSize();

		if(market.hasCondition("urbanized_polity") || size >= 4)
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
			
		}
		if(size >= 4)
		{
			allowedImages.add(new String[]{"illustrations", "industrial_megafacility"} );
			allowedImages.add(new String[]{"illustrations", "city_from_above"} );
		}
		if (isStation && size >= 3)
			allowedImages.add(new String[]{"illustrations", "jangala_station"} );
		if (entity.getFaction().getId().equals("pirates"))
			allowedImages.add(new String[]{"illustrations", "pirate_station"} );
		if (!isStation && (planetType.equals("rocky_metallic") || planetType.equals("rocky_barren") || planetType.equals("barren-bombarded")) )
			allowedImages.add(new String[]{"illustrations", "vacuum_colony"} );
		//if (isMoon)
		//	allowedImages.add(new String[]{"illustrations", "asteroid_belt_moon"} );
		if(planetType.equals("desert") && isMoon)
			allowedImages.add(new String[]{"illustrations", "desert_moons_ruins"} );

		String[] illustration = allowedImages.pick();
		entity.setInteractionImage(illustration[0], illustration[1]);
	}
	
	private MarketAPI addMarketToEntity(SectorEntityToken entity, EntityData data, String factionId)
	{
		// don't make the markets too big; they'll screw up the economy big time
		int marketSize = 1;
		EntityType entityType = data.type;
		String planetType = data.planetType;
		boolean isStation = (entityType == EntityType.STATION); 
		if (isStation) marketSize = 2 + MathUtils.getRandomNumberInRange(1, 2);	// stations are on average smaller
		else if (entityType == EntityType.MOON) marketSize = MathUtils.getRandomNumberInRange(1, 2) + MathUtils.getRandomNumberInRange(2, 3);
		else marketSize = 2 + MathUtils.getRandomNumberInRange(2, 3);
		
		MarketAPI newMarket = Global.getFactory().createMarket(entity.getId() + "_market", entity.getName(), marketSize);
		newMarket.setPrimaryEntity(entity);
		entity.setMarket(newMarket);
		
		newMarket.setFactionId(factionId);
		newMarket.setBaseSmugglingStabilityValue(0);
		
		if (data.isHQ)
		{
			if (marketSize < 6) marketSize = 6;
			newMarket.addCondition("headquarters");
			if (data == homeworld) newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
		}
		else if (data.isCapital)
		{
			if (marketSize < 5) marketSize = 5;
			newMarket.addCondition("regional_capital");
		}
		if (data.forceMarketSize != -1) marketSize = data.forceMarketSize;
		newMarket.setSize(marketSize);
		newMarket.addCondition("population_" + marketSize);
		
		int minSizeForMilitaryBase = 5;
		if (isStation) minSizeForMilitaryBase = 4;
		
		if (marketSize >= minSizeForMilitaryBase)
		{
			newMarket.addCondition("military_base");
		}
		
		// planet type conditions
		switch (planetType) {
			case "jungle":
				newMarket.addCondition("jungle");
				if(MathUtils.getRandomNumberInRange(0, 3) == 0)
					newMarket.addCondition("orbital_burns");
				break;
			case "water":
				newMarket.addCondition("water");
				break;
			case "arid":
				newMarket.addCondition("arid");
				break;
			case "terran":
				newMarket.addCondition("terran");
				break;
			case "desert":
				newMarket.addCondition("desert");
				break;
			case "frozen":
			case "rocky_ice":
				newMarket.addCondition("ice");
				break;
			case "barren":
			case "rocky_metallic":
			case "barren-bombarded":
				newMarket.addCondition("uninhabitable");
				break;
		}
				
		if(marketSize < 4 && !isStation){
			newMarket.addCondition("frontier");
		}
		
		// add random market conditions
		ExerelinMarketConditionPicker picker = new ExerelinMarketConditionPicker();
		picker.addMarketConditions(newMarket, marketSize, planetType, isStation);

		if (isStation && marketSize >= 3)
		{
			newMarket.addCondition("exerelin_recycling_plant");
		}
				
		// add per-faction market conditions
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		
		newMarket.getTariff().modifyFlat("default_tariff", 0.2f);
		if (config.freeMarket)
		{
			newMarket.addCondition("free_market");
			newMarket.getTariff().modifyMult("isFreeMarket", 0.5f);
		}
		
		if (factionId.equals("luddic_church")) {
			newMarket.addCondition("luddic_majority");
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
		
		// seed the market with some stuff to prevent initial shortage
		// because the vanilla one is broken for some reason
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "green_crew", 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "regular_crew", 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "veteran_crew", 0.1f, 0.2f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "marines", 0.8f, 1.0f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "supplies", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "fuel", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "food", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "domestic_goods", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "luxury_goods", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "heavy_machinery", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "metals", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "rare_metals", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "ore", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "rare_ore", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "organics", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "volatiles", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "hand_weapons", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "drugs", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "organs", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(newMarket, "lobster", 0.7f, 0.8f);
		//if (marketSize >= 4)
		//	ExerelinUtilsCargo.addCommodityStockpile(newMarket, "agent", marketSize);
		
		Global.getSector().getEconomy().addMarket(newMarket);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		data.market = newMarket;
		return newMarket;
	}
		
	public void addOmnifactory()
	{
		if (!ExerelinSetupData.getInstance().omnifactoryPresent) return;

		SectorEntityToken toOrbit = null;
		//log.info("Randomized omnifac location: " + ExerelinSetupData.getInstance().randomOmnifactoryLocation);
		boolean random = ExerelinSetupData.getInstance().randomOmnifactoryLocation;
		if (numOmnifacs > 0) random = true;
		
		if (random)
		{
			List<StarSystemAPI> systems = new ArrayList(Global.getSector().getStarSystems());
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
			if (ExerelinConfig.corvusMode) toOrbit = Global.getSector().getEntityById("corvus_IV");
			else toOrbit = homeworld.entity;
		}
		
		
		LocationAPI system = toOrbit.getContainingLocation();
		log.info("Placing Omnifactory around " + toOrbit.getName() + ", in the " + system.getName());
		String[] images = stationImages.get("default");
		String image = images[MathUtils.getRandomNumberInRange(0, images.length - 1)];
		SectorEntityToken omnifac = system.addCustomEntity("omnifactory", "Omnifactory", image, "neutral");
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
		omnifac.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(radius, orbitDistance, getDensity(toOrbit)));
		omnifac.setInteractionImage("illustrations", "abandoned_station");
		omnifac.setCustomDescriptionId("omnifactory");

		MarketAPI market = Global.getFactory().createMarket("omnifactory_market", "Omnifactory", 0);
		SharedData.getData().getMarketsWithoutPatrolSpawn().add("omnifactory_market");
		SharedData.getData().getMarketsWithoutTradeFleetSpawn().add("omnifactory_market");
		market.setPrimaryEntity(omnifac);
		market.setFactionId("neutral");
		market.addCondition(Conditions.ABANDONED_STATION);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		((StoragePlugin) market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);
		omnifac.setMarket(market);
		Global.getSector().getEconomy().addMarket(market);
		
		omnifac.setFaction("neutral");
		
		OmniFac.initOmnifactory(omnifac);
	}
	
	public void addPrismMarket()
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
			prismEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(radius, orbitDistance, getDensity(toOrbit)));
		}
		else
		{
			LocationAPI hyperspace = Global.getSector().getHyperspace();
			prismEntity = hyperspace.addCustomEntity("prismFreeport", "Prism Freeport", "exerelin_freeport_type", "independent");
			prismEntity.setCircularOrbitWithSpin(hyperspace.createToken(0, 0), MathUtils.getRandomNumberInRange(0, 360), 150, 60, 30, 30);
		}
		
		/*
		EntityData data = new EntityData(null);
		data.name = "Prism Freeport";
		data.type = EntityType.STATION;
		data.forceMarketSize = 4;
		
		MarketAPI market = addMarketToEntity(prismEntity, data, "independent");
		*/

		MarketAPI market = Global.getFactory().createMarket("prismFreeport" + "_market", "Prism Freeport", 4);
		market.setFactionId("independent");
		market.addCondition("population_4");
		market.addCondition("spaceport");
		market.addCondition("exerelin_recycling_plant");
		market.addCondition("exerelin_recycling_plant");
		market.addCondition("exerelin_hydroponics");
		market.addCondition("light_industrial_complex");
		market.addCondition("trade_center");
		market.addCondition("stealth_minefields");
		market.addCondition("cryosanctum");
		market.addCondition("military_base");
		market.addCondition("free_market");
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		
		ExerelinUtilsCargo.addCommodityStockpile(market, "green_crew", 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "regular_crew", 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "veteran_crew", 0.1f, 0.2f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "marines", 0.8f, 1.0f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "supplies", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "fuel", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "food", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "domestic_goods", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "luxury_goods", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "heavy_machinery", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "metals", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "rare_metals", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "ore", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "rare_ore", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "organics", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "volatiles", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "hand_weapons", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "drugs", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "organs", 0.7f, 0.8f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "lobster", 0.7f, 0.8f);
		
		market.getTariff().modifyFlat("default_tariff", 0.2f);
		market.getTariff().modifyMult("isFreeMarket", 0.5f);
		market.addSubmarket("exerelin_prismMarket");
		market.setPrimaryEntity(prismEntity);
		prismEntity.setMarket(market);
		prismEntity.setFaction("independent");
		Global.getSector().getEconomy().addMarket(market);
		
		//pickEntityInteractionImage(prismEntity, market, "", EntityType.STATION);
		//prismEntity.setInteractionImage("illustrations", "space_bar");
		prismEntity.setCustomDescriptionId("exerelin_prismFreeport");
	}
	
	void generateSSPSector(SectorAPI sector)
	{
		new SSP_Askonia().generate(sector);
		new SSP_Eos().generate(sector);
		new SSP_Valhalla().generate(sector);
		new SSP_Arcadia().generate(sector);
		new SSP_Magec().generate(sector);
		new SSP_Corvus().generate(sector);

		LocationAPI hyper = Global.getSector().getHyperspace();
		SectorEntityToken zinLabel = hyper.addCustomEntity("zin_label_id", null, "zin_label", null);
		SectorEntityToken abyssLabel = hyper.addCustomEntity("opabyss_label_id", null, "opabyss_label", null);
		SectorEntityToken telmunLabel = hyper.addCustomEntity("telmun_label_id", null, "telmun_label", null);
		SectorEntityToken cathedralLabel = hyper.addCustomEntity("cathedral_label_id", null, "cathedral_label", null);
		SectorEntityToken coreLabel = hyper.addCustomEntity("core_label_id", null, "core_label", null);

		zinLabel.setFixedLocation(-14500, -8000);
		abyssLabel.setFixedLocation(-12000, -19000);
		telmunLabel.setFixedLocation(-16000, 8000);
		cathedralLabel.setFixedLocation(-20000, 2000);
		coreLabel.setFixedLocation(17000, -6000);
	}
	
	public void generateVanillaSector(SectorAPI sector)
	{
		StarSystemAPI system = sector.createStarSystem("Corvus");
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		PlanetAPI star = system.initStar("corvus", "star_yellow", 500f);

		PlanetAPI corvusI = system.addPlanet("asharu", star, "Asharu", "desert", 55, 150, 3000, 100);
		corvusI.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "asharu"));
		corvusI.getSpec().setGlowColor(new Color(255,255,255,255));
		corvusI.getSpec().setUseReverseLightForGlow(true);
		corvusI.applySpecChanges();
		corvusI.setCustomDescriptionId("planet_asharu");
		
		PlanetAPI corvusII = system.addPlanet("jangala", star, "Jangala", "jungle", 235, 200, 4500, 200);		
		corvusII.setCustomDescriptionId("planet_jangala");
		corvusII.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
		corvusII.getSpec().setGlowColor(new Color(255,255,255,255));
		corvusII.getSpec().setUseReverseLightForGlow(true);
		corvusII.applySpecChanges();

		system.addAsteroidBelt(star, 100, 5500, 1000, 150, 300);
		
		SectorEntityToken corvusIII = system.addPlanet("barad", star, "Barad", "gas_giant", 200, 300, 7500, 400);
		SectorEntityToken corvusIIIA = system.addPlanet("corvus_IIIa", corvusIII, "Barad A", "cryovolcanic", 235, 120, 800, 20);
		corvusIIIA.setCustomDescriptionId("planet_barad_a");
		system.addAsteroidBelt(corvusIII, 50, 1000, 200, 10, 45);
		SectorEntityToken corvusIIIB = system.addPlanet("corvus_IIIb", corvusIII, "Barad B", "barren", 235, 100, 1300, 60);
			corvusIIIB.setInteractionImage("illustrations", "vacuum_colony");
		
		SectorEntityToken corvusIV = system.addPlanet("corvus_IV", star, "Somnus", "barren-bombarded", 0, 100, 10000, 700);
		SectorEntityToken corvusV = system.addPlanet("corvus_V", star, "Mors", "frozen", 330, 175, 12000, 500);
		
		//corvusV.setFaction("tritachyon");
		
		new Askonia().generate(sector);
		new Eos().generate(sector);
		new Valhalla().generate(sector);
		new Arcadia().generate(sector);
		new Magec().generate(sector);
		new Corvus().generate(sector);
		
		LocationAPI hyper = Global.getSector().getHyperspace();
		SectorEntityToken zinLabel = hyper.addCustomEntity("zin_label_id", null, "zin_label", null);
		SectorEntityToken abyssLabel = hyper.addCustomEntity("opabyss_label_id", null, "opabyss_label", null);
		SectorEntityToken telmunLabel = hyper.addCustomEntity("telmun_label_id", null, "telmun_label", null);
		SectorEntityToken cathedralLabel = hyper.addCustomEntity("cathedral_label_id", null, "cathedral_label", null);
		SectorEntityToken coreLabel = hyper.addCustomEntity("core_label_id", null, "core_label", null);
		
		zinLabel.setFixedLocation(-14500, -8000);
		abyssLabel.setFixedLocation(-12000, -19000);
		telmunLabel.setFixedLocation(-16000, 8000);
		cathedralLabel.setFixedLocation(-20000, 2000);
		coreLabel.setFixedLocation(17000, -6000);
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
			Global.getLogger(ExerelinSectorGen.class).log(Level.ERROR, ex);
		}
		
		log.info("Loading backgrounds");
		loadBackgrounds();
		
		log.info("resetting vars");
		resetVars();
		
		boolean corvusMode = ExerelinConfig.corvusMode;
		
		if (!corvusMode)
		{
			// purge existing star systems
			/*
			List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
			
			for (MarketAPI market : markets)
			{
				sector.getEconomy().removeMarket(market);
			}
			List<LocationAPI> locs = new ArrayList<>();
			for (StarSystemAPI system : sector.getStarSystems())
			{
				locs.add(system);
			}
			locs.add(Global.getSector().getHyperspace());
			for (LocationAPI loc : locs)
			{
				if (loc instanceof StarSystemAPI)
					log.info("Removing " + loc.getName());
				List entities = loc.getEntities(SectorEntityToken.class);
				List<SectorEntityToken> entitiesToRemove = new ArrayList<>();
				for (Object entity : entities) {
					entitiesToRemove.add((SectorEntityToken)entity);
				}
				for (SectorEntityToken entity : entitiesToRemove) {
					loc.removeEntity(entity);
				}
				if (loc instanceof StarSystemAPI)
					sector.removeStarSystem((StarSystemAPI)loc);
			}
			*/
			
			// stars will be distributed in a concentric pattern
			float angle = 0;
			float distance = 0;
			int numSystems = ExerelinSetupData.getInstance().numSystems;
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


			// build systems
			log.info("Building systems");
			for(int i = 0; i < ExerelinSetupData.getInstance().numSystems; i ++)
				buildSystem(sector, i);
			log.info("Populating sector");
			populateSector();
		}
		else
		{
			if (ExerelinUtils.isSSPInstalled())
			{
				generateSSPSector(sector);
			}
			else generateVanillaSector(sector);
		}
		//for (int i=0; i<OmniFacSettings.) // TODO: use Omnifactory's numberOfFactories setting when it's supported
		addOmnifactory();
		addPrismMarket();
		
		final String selectedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);
		
		log.info("Adding scripts and plugins");
		sector.addScript(new CoreScript());
		sector.registerPlugin(new ExerelinCoreCampaignPlugin());
		
		// SS+ mod plugin already has this covered
		if (corvusMode && ExerelinUtils.isSSPInstalled())
		{
			//SSP_EventProbabilityManager probabilityManager = new SSP_EventProbabilityManager();
			//sector.getPersistentData().put("ssp_eventProbabilityManager", probabilityManager);
			//sector.addScript(probabilityManager);
		}
		else
		{
			sector.addScript(new CoreEventProbabilityManager());
		}
		sector.addScript(new EconomyFleetManager());
		
		if (!corvusMode) sector.addScript(new ForcePatrolFleetsScript());
		//sector.addScript(new EconomyLogger());
		
		sector.addScript(SectorManager.create());
		sector.addScript(DiplomacyManager.create());
		sector.addScript(InvasionFleetManager.create());
		sector.addScript(ResponseFleetManager.create());
		sector.addScript(MiningFleetManager.create());
		sector.addScript(CovertOpsManager.create());
		sector.addScript(AllianceManager.create());
		StatsTracker.create();
		
		DiplomacyManager.initFactionRelationships();
		
		SectorManager.setSystemToRelayMap(systemToRelay);
		SectorManager.setPlanetToRelayMap(planetToRelay);
		SectorManager.setCorvusMode(ExerelinConfig.corvusMode);
		
		// some cleanup
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : markets) {
			if (market.getFactionId().equals("templars"))
			{
				market.removeSubmarket(Submarkets.GENERIC_MILITARY); // auto added by military base; remove it
			}
		}
		
		log.info("Adding teleport script");
		// teleport player to homeworld at start
		// FIXME: doesn't get into the save at start
		if (corvusMode)
		{
			SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(selectedFactionId);
			if (spawnPoint != null)
			{
				// moves player fleet to a suitable location; e.g. Avesta for Association
				final String HOME_ENTITY = spawnPoint.entityName;
				EveryFrameScript teleportScript = new EveryFrameScript() {
					private boolean done = false;
					private boolean unlockedStorage = false;
					
					public boolean runWhilePaused() {
						return false;
					}
					public boolean isDone() {
						return done;
					}
					public void advance(float amount) {
						SectorEntityToken entity = Global.getSector().getEntityById(HOME_ENTITY);
						if (!unlockedStorage && entity != null)
						{
							MarketAPI homeMarket = entity.getMarket();
							if (homeMarket != null)
							{
								StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
								if (plugin != null)
								plugin.setPlayerPaidToUnlock(true);
							}
							unlockedStorage = true;
						}
						
						if (Global.getSector().isInNewGameAdvance()) return;
						if (entity != null)
						{
							Vector2f loc = entity.getLocation();
							Global.getSector().getPlayerFleet().setLocation(loc.x, loc.y);
							done = true;
						}
					}
				};
				sector.addTransientScript(teleportScript);
			}
		}
		else if (!ExerelinSetupData.getInstance().freeStart)
		{
			EveryFrameScript teleportScript = new EveryFrameScript() {
				private boolean done = false;
				public boolean runWhilePaused() {
					return false;
				}
				public boolean isDone() {
					return done;
				}
				public void advance(float amount) {
					if (Global.getSector().isInNewGameAdvance()) return;

					SectorEntityToken entity = homeworld.entity;
					Vector2f loc = entity.getLocation();
					Global.getSector().getPlayerFleet().setLocation(loc.x, loc.y);
					done = true;
				}
			};
			sector.addTransientScript(teleportScript);
		}
		
		//sector.setRespawnLocation(homeworld.starSystem);
		//sector.getRespawnCoordinates().set(homeworld.entity.getLocation().x, homeworld.entity.getLocation().y);
		
		// Remove any data stored in ExerelinSetupData
		ExerelinSetupData.resetInstance();
		
		log.info("Finished sector generation");
	}
		
	public float getOrbitalPeriod(float primaryRadius, float orbitRadius, float density)
	{
		primaryRadius *= 0.01;
		orbitRadius *= 0.01;
		
		float mass = (float)Math.floor(4f / 3f * Math.PI * Math.pow(primaryRadius, 3));
		mass *= density;
		float radiusCubed = (float)Math.pow(orbitRadius, 3);
		float period = (float)(2 * Math.PI * Math.sqrt(radiusCubed/mass) * 2);
		return period;
	}
	
	public float getDensity(SectorEntityToken primary)
	{
		if (primary instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)primary;
			if (planet.getTypeId().equals("star_dark")) return 8;
			else if (planet.isStar()) return 1;
		}
		return 2;
	}
	
	private SectorEntityToken makeStation(EntityData data, String factionId)
	{
		int angle = MathUtils.getRandomNumberInRange(1, 360);
		int orbitRadius = 300;
		PlanetAPI planet = (PlanetAPI)data.primary.entity;
		if (data.primary.type == EntityType.MOON)
			orbitRadius = 200;
		else if (planet.isGasGiant())
			orbitRadius = 500;
		else if (planet.isStar())
			orbitRadius = (int)data.orbitDistance;

		float orbitDays = getOrbitalPeriod(planet.getRadius(), orbitRadius + planet.getRadius(), getDensity(planet));

		String name = planet.getName() + " " + data.name;
		String id = name.replace(' ','_');
		String[] images = stationImages.get("default");
		if (stationImages.containsKey(factionId))
			images = stationImages.get(factionId);
		
		String image = images[MathUtils.getRandomNumberInRange(0, images.length - 1)];
		
		SectorEntityToken newStation = data.starSystem.addCustomEntity(id, name, image, factionId);
		newStation.setCircularOrbitPointingDown(planet, angle, orbitRadius, orbitDays);
		
		MarketAPI existingMarket = planet.getMarket();
		if (existingMarket != null)
		{
			existingMarket.addCondition("orbital_station");
			existingMarket.addCondition("exerelin_recycling_plant");
			newStation.setMarket(existingMarket);
			existingMarket.getConnectedEntities().add(newStation);
			data.market = existingMarket;
		}
		else
		{	
			MarketAPI market = addMarketToEntity(newStation, data, factionId);
		}
		pickEntityInteractionImage(newStation, newStation.getMarket(), planet.getTypeId(), EntityType.STATION);
		newStation.setCustomDescriptionId("orbital_station_default");
		
		return newStation;
	}
	
	public void populateSector()
	{
		SectorAPI sector = Global.getSector();
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>();
		List<String> factions = new ArrayList<>(factionIds);
		factions.remove("player_npc");  // player NPC faction only gets homeworld (if applicable)
		addListToPicker(factions, factionPicker);
		boolean hqsSpawned = false;
		
		// before we do anything else give the "homeworld" to our faction
		if (!ExerelinSetupData.getInstance().freeStart)
		{
			String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
			MarketAPI homeMarket = addMarketToEntity(homeworld.entity, homeworld, alignedFactionId);
			SectorEntityToken relay = sector.getEntityById(systemToRelay.get(homeworld.starSystem.getId()));
			relay.setFaction(alignedFactionId);
			pickEntityInteractionImage(homeworld.entity, homeworld.entity.getMarket(), homeworld.planetType, homeworld.type);
			habitablePlanets.remove(homeworld);
			factionPicker.remove(alignedFactionId);
			
			StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
			plugin.setPlayerPaidToUnlock(true);
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
			if (!hqsSpawned) habitable.isHQ = true;
			addMarketToEntity(habitable.entity, habitable, factionId);
			
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
	}

	public PlanetAPI makeStar(int index, String systemId, StarSystemAPI system, String type, float size)
	{
		Integer[] pos = (Integer[])starPositions.get(index);
		int x = pos[0];
		int y = pos[1];
		return system.initStar(systemId, type, size, x, y);
	}
	
	private float getHabitableChance(int planetNum, boolean isMoon)
	{
		float habitableChance = 0.3f;
		if (planetNum == 0) habitableChance = 0.4f;
		else if (planetNum == 1 || planetNum == 3) habitableChance = 0.7f;
		else if (planetNum == 2) habitableChance = 0.9f;
			
		//if (isMoon) habitableChance *= 0.7f;
		if (isMoon) habitableChance = 0.4f;
		
		return habitableChance;
	}
		
	public void buildSystem(SectorAPI sector, int systemIndex)
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
		PlanetAPI star;
	
		int starType = MathUtils.getRandomNumberInRange(0, 10);

		// TODO refactor to remove endless nested ifs
		if(starType == 0)
		{
			star = makeStar(systemIndex, systemId, system, "star_yellow", 500f);
			//system.setLightColor(new Color(255, 180, 180));
		}
		else if(starType == 1)
		{
			star = makeStar(systemIndex, systemId, system, "star_red", 900f);
			system.setLightColor(new Color(255, 180, 180));
		}
		else if(starType == 2)
		{
			star = makeStar(systemIndex, systemId, system, "star_blue", 400f);
			system.setLightColor(new Color(135,206,250));
		}
		else if(starType == 3)
		{
			star = makeStar(systemIndex, systemId, system, "star_white", 300f);
			//system.setLightColor(new Color(185,185,240));
		}
		else if(starType == 4)
		{
			star = makeStar(systemIndex, systemId, system, "star_orange", 900f);
			system.setLightColor(new Color(255,220,0));
		}
		else if(starType == 5)
		{
			star = makeStar(systemIndex, systemId, system, "star_yellowwhite", 400f);
			system.setLightColor(new Color(255,255,224));
		}
		else if(starType == 6)
		{
			star = makeStar(systemIndex, systemId, system, "star_bluewhite", 400f);
			system.setLightColor(new Color(135,206,250));
		}
		else if(starType == 7)
		{
			star = makeStar(systemIndex, systemId, system, "star_purple", 700f);
			system.setLightColor(new Color(218,112,214));
		}
		else if(starType == 8)
		{
			star = makeStar(systemIndex, systemId, system, "star_dark", 100f);
			system.setLightColor(new Color(105,105,105));
		}
		else if(starType == 9)
		{
			star = makeStar(systemIndex, systemId, system, "star_green", 600f);
			system.setLightColor(new Color(240,255,240));
		}
		else
		{
			star = makeStar(systemIndex, systemId, system, "star_greenwhite", 500f);
			system.setLightColor(new Color(240,255,240));
		}
		system.setBackgroundTextureFilename( starBackgrounds.get(MathUtils.getRandomNumberInRange(0, starBackgrounds.size() - 1)) );

		List<EntityData> entities = new ArrayList<>();
		EntityData starData = new EntityData(system);
		starData.entity = star;
		starData.type = EntityType.STAR;
		
		// now let's start seeding planets
		// note that we don't create the PlanetAPI right away, but set up EntityDatas first
		// so we can check that the system has enough of the types of planets we want
		int numBasePlanets;
		int maxPlanets = ExerelinSetupData.getInstance().maxPlanets;
		if(ExerelinSetupData.getInstance().numSystems != 1)
			numBasePlanets = MathUtils.getRandomNumberInRange(ExerelinConfig.minimumPlanets, maxPlanets);
		else
			numBasePlanets = maxPlanets;
		int distanceStepping = (ExerelinSetupData.getInstance().maxSystemSize-4000)/MathUtils.getRandomNumberInRange(numBasePlanets, maxPlanets+1);
		
		boolean gasPlanetCreated = false;
		int habitableCount = 0;
		List<EntityData> uninhabitables1To4 = new ArrayList<>();
		
		for(int i = 0; i < numBasePlanets; i = i + 1)
		{
			float habitableChance = getHabitableChance(i, false);
			
			boolean habitable = Math.random() <= habitableChance;
			EntityData entityData = new EntityData(system, i);
			entityData.habitable = habitable;
			entityData.primary = starData;
			
			if (habitable)
			{
				habitableCount++;
			}
			else if (i <= 3)
			{
				uninhabitables1To4.add(entityData);
			}
			entities.add(entityData);
		}
		
		// make sure there are at least two habitable planets
		if (habitableCount < 2)
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
			String planetType = "";
			boolean isGasGiant = false;
			
			String name = "";
			String id = "";
			int planetNameIndex = MathUtils.getRandomNumberInRange(0, possiblePlanetNamesList.size() - 1);
			name = possiblePlanetNamesList.get(planetNameIndex);
			possiblePlanetNamesList.remove(planetNameIndex);
			log.info("Creating planet " + name);
			id = name.replace(' ','_');
			
			if (planetData.habitable)
			{
				planetType = planetTypes[MathUtils.getRandomNumberInRange(0, planetTypes.length - 1)];
			}
			else
			{
				float gasGiantChance = 0.45f;
				if (planetData.planetNum == 3) gasGiantChance = 0.3f;
				else if (planetData.planetNum < 3) gasGiantChance = 0;
				
				isGasGiant = Math.random() < gasGiantChance;
				if (isGasGiant) planetType = planetTypesGasGiant[MathUtils.getRandomNumberInRange(0, planetTypesGasGiant.length - 1)];
				else planetType = planetTypesUninhabitable[MathUtils.getRandomNumberInRange(0, planetTypesUninhabitable.length - 1)];
			}
			
			float radius;
			float angle = MathUtils.getRandomNumberInRange(1, 360);
			float distance = 3000 + (distanceStepping * (planetData.planetNum)) + MathUtils.getRandomNumberInRange((distanceStepping/3)*-1, distanceStepping/3);
			float orbitDays = getOrbitalPeriod(star.getRadius(), distance + star.getRadius(), getDensity(star));
			
			if (isGasGiant)
			{
				radius = MathUtils.getRandomNumberInRange(325, 375);
				gasPlanetCreated = true;
			}
			else
				radius = MathUtils.getRandomNumberInRange(150, 250);

			// At least one gas giant per system
			if(!gasPlanetCreated && planetData.planetNum == numBasePlanets - 1)
			{
				planetType = planetTypesGasGiant[MathUtils.getRandomNumberInRange(0, planetTypesGasGiant.length - 1)];
				radius = MathUtils.getRandomNumberInRange(325, 375);
				gasPlanetCreated = true;
				planetData.habitable = false;
				isGasGiant = true;
			}
			
			SectorEntityToken newPlanet = system.addPlanet(id, star, name, planetType, angle, radius, distance, orbitDays);
			planetData.entity = newPlanet;
			planetData.planetType = planetType;
			
			// Now we make moons
			float moonChance = 0.4f;
			if (isGasGiant)
				moonChance = 0.8f;
			if(Math.random() <= moonChance)
			{
				for(int j = 0; j < MathUtils.getRandomNumberInRange(0, ExerelinSetupData.getInstance().maxMoonsPerPlanet - 1); j = j + 1)
				{
					String ext = "";
					if(j == 0)
						ext = "I";
					if(j == 1)
						ext = "II";
					if(j == 2)
						ext = "III";
					
					boolean moonInhabitable = Math.random() < getHabitableChance(planetData.planetNum, true);
					String moonType = "";
					if (moonInhabitable)
						moonType = moonTypes[MathUtils.getRandomNumberInRange(0, moonTypes.length - 1)];
					else
						moonType = moonTypesUninhabitable[MathUtils.getRandomNumberInRange(0, moonTypesUninhabitable.length - 1)];
						
					angle = MathUtils.getRandomNumberInRange(1, 360);
					distance = MathUtils.getRandomNumberInRange(650, 1300);
					float moonRadius = MathUtils.getRandomNumberInRange(50, 100);
					orbitDays = getOrbitalPeriod(newPlanet.getRadius(), distance + newPlanet.getRadius(), 2);
					PlanetAPI newMoon = system.addPlanet(name + " " + ext, newPlanet, name + " " + ext, moonType, angle, moonRadius, distance, orbitDays);
					log.info("Creating moon " + name + " " + ext);
					
					EntityData moonData = new EntityData(system);
					moonData.entity = newMoon;
					moonData.planetType = moonType;
					moonData.primary = planetData;
					moonData.habitable = moonInhabitable;
					moonData.type = EntityType.MOON;
					
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

				if(ringType == 0)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*2, 40f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*2, 60f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*2, 80f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, (int)(radius*2.5), 80f);
				}
				else if (ringType == 1)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*3, 70f);
					//system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*2.5), 90f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*3.5), 110f);
				}
				else if (ringType == 2)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, radius*3, 70f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*3), 90f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*3), 110f);
				}
				else if (ringType == 3)
				{
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, radius*2, 50f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, radius*2, 70f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, radius*2, 80f);
					system.addRingBand(newPlanet, "misc", "rings1", 256f, 1, Color.white, 256f, (int)(radius*2.5), 90f);
				}
			}
		}
		
		// add the moons back to our main entity list
		for (EntityData moon: moons)
		{
			entities.add(moon);
		}
		
		// set sector capital
		WeightedRandomPicker<EntityData> capitalPicker = new WeightedRandomPicker<>();
		for (EntityData planetData : entities)
		{
			if (!planetData.habitable || planetData.type != EntityType.PLANET) continue;
			float weight = 1f;
			if (planetData.planetNum == 2 || planetData.planetNum == 3)
			{
				weight = 4f;
			}
			capitalPicker.add(planetData, weight);
		}
		capital = capitalPicker.pick();
		capital.isCapital = true;
		if (systemIndex == 0)
		{
			homeworld = capital;
			homeworld.isHQ = true;
		}

		// Build asteroid belts
		// If the belt orbits a star, add it to a list so that we can seed belter stations later
		List<PlanetAPI> planets = system.getPlanets();
		List<Float> starBelts = new ArrayList<>();
		int numAsteroidBelts;
		if(ExerelinSetupData.getInstance().numSystems != 1)
			numAsteroidBelts = MathUtils.getRandomNumberInRange(ExerelinConfig.minimumAsteroidBelts, ExerelinSetupData.getInstance().maxAsteroidBelts);
		else
			numAsteroidBelts = ExerelinSetupData.getInstance().maxAsteroidBelts;

		for(int j = 0; j < numAsteroidBelts; j = j + 1)
		{
			PlanetAPI planet = planets.get(MathUtils.getRandomNumberInRange(0, planets.size() - 1));

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
				orbitRadius = MathUtils.getRandomNumberInRange(1000, 8000);
				numAsteroids = 100;
			}
			else
			{
				orbitRadius = MathUtils.getRandomNumberInRange(400, 550);
				numAsteroids = 15;
			}

			float width = MathUtils.getRandomNumberInRange(10, 50);
			float baseOrbitDays = getOrbitalPeriod(planet.getRadius(), orbitRadius, planet.isStar() ? 1 : 2);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			system.addAsteroidBelt(planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
			if (planet.isStar()) starBelts.add(orbitRadius);
		}

		// Always put an asteroid belt around the sun
		do {
			float distance = MathUtils.getRandomNumberInRange(1500, 8000);
			float baseOrbitDays = getOrbitalPeriod(star.getRadius(), distance, 1);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			
			system.addAsteroidBelt(star, 25, distance, MathUtils.getRandomNumberInRange(10, 50), minOrbitDays, maxOrbitDays);
			starBelts.add(distance);

			// Another one if medium system size
			if(ExerelinSetupData.getInstance().maxSystemSize > 16000)
			{
				distance = MathUtils.getRandomNumberInRange(12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star.getRadius(), distance, 1);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				system.addAsteroidBelt(star, 50, distance, MathUtils.getRandomNumberInRange(50, 100), minOrbitDays, maxOrbitDays);
				starBelts.add(distance);
			}
			// And another one if a large system
			if(ExerelinSetupData.getInstance().maxSystemSize > 32000)
			{
				distance = MathUtils.getRandomNumberInRange(12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star.getRadius(), distance, 1);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				system.addAsteroidBelt(star, 75, distance, MathUtils.getRandomNumberInRange(100, 150),  minOrbitDays, maxOrbitDays);
				starBelts.add(distance);
			}
		} while (false);
		
		for (EntityData entity : entities)
		{
			if (entity.habitable) habitablePlanets.add(entity);
		}
		
		// Build stations
		// Note: to enable special faction stations, we don't actually generate the stations until much later,
		// when we're dealing out planets to factions
		int numStation;
		if(ExerelinSetupData.getInstance().numSystems != 1)
			numStation = MathUtils.getRandomNumberInRange(ExerelinConfig.minimumStations, Math.min(ExerelinSetupData.getInstance().maxStations, numBasePlanets*2));
		else
			numStation = ExerelinSetupData.getInstance().maxStations;
		
		WeightedRandomPicker<EntityData> picker = new WeightedRandomPicker<>();
		//addListToPicker(entities, picker);
		for (EntityData entityData : entities)
		{
			float weight = 1f;
			if (entityData.type == EntityType.STAR) weight = 3f;
			else if (entityData.habitable == false) weight = 2f;
			picker.add(entityData, weight);
		}
		
		int k = 0;
		List alreadyUsedStationNames = new ArrayList();
		while(k < numStation)
		{
			if (picker.isEmpty()) picker.add(starData);
			EntityData primaryData = picker.pickAndRemove();
			boolean isGasGiant = primaryData.type == EntityType.PLANET && ((PlanetAPI)primaryData.entity).isGasGiant();
			boolean isStar = primaryData.type == EntityType.STAR;
			SectorEntityToken planet = primaryData.entity;

			EntityData stationData = new EntityData(system);
			stationData.primary = primaryData;
			stationData.type = EntityType.STATION;
			if (isStar) stationData.orbitDistance = starBelts.get(MathUtils.getRandomNumberInRange(0, starBelts.size() - 1));
			
			boolean nameOK = false;
			String name = "";
			while(!nameOK)
			{
				name = possibleStationNamesList.get(MathUtils.getRandomNumberInRange(0, possibleStationNamesList.size() - 1));
				if (!alreadyUsedStationNames.contains(name))
					nameOK = true;
			}
			alreadyUsedStationNames.add(name);
			stationData.name = name;			
			stations.add(stationData);
			log.info("Prepping station " + name);

			k = k + 1;
		}

		// Build hyperspace exits
		if (ExerelinSetupData.getInstance().numSystems > 1)
		{
			SectorEntityToken capitalToken = capital.entity;
			JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint(capitalToken.getId() + "_jump", capitalToken.getName() + " Gate");
			float radius = capitalToken.getRadius();
			float orbitDistance = radius + 250f;
			float orbitDays = getOrbitalPeriod(radius, orbitDistance, 2);
			jumpPoint.setCircularOrbit(capitalToken, (float)Math.random() * 360, orbitDistance, orbitDays);
			jumpPoint.setRelatedPlanet(capitalToken);

			jumpPoint.setStandardWormholeToHyperspaceVisual();
			system.addEntity(jumpPoint);
			system.autogenerateHyperspaceJumpPoints(true, true);
		}

		// Build comm relay
		SectorEntityToken relay = system.addCustomEntity(system.getId() + "_relay", // unique id
				system.getBaseName() + " Relay", // name - if null, defaultName from custom_entities.json will be used
				"comm_relay", // type of object, defined in custom_entities.json
				"neutral"); // faction
		relay.setCircularOrbit(star, (float)Math.random() * 360, 1500, getOrbitalPeriod(star.getRadius(), 1500, 1));
		systemToRelay.put(system.getId(), system.getId() + "_relay");
		planetToRelay.put(capital.entity.getId(), system.getId() + "_relay");
	}
	
	public static class OmnifacFilter implements CollectionUtils.CollectionFilter<SectorEntityToken>
	{
		final Set<SectorEntityToken> blocked;
		private OmnifacFilter(StarSystemAPI system)
		{
			blocked = new HashSet<>();
			for (SectorEntityToken planet : system.getPlanets() )
			{
			String factionId = planet.getFaction().getId();
			String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
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
	
	enum EntityType {
		STAR, PLANET, MOON, STATION
	}
	
	static class EntityData {
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
		int forceMarketSize = -1;
		int planetNum = -1;
		float orbitDistance = 0;	// only used for belter stations
		
		public EntityData(StarSystemAPI starSystem) 
		{
			this.starSystem = starSystem;
		}	  
		public EntityData(StarSystemAPI starSystem, int planetNum) 
		{
			this.starSystem = starSystem;
			this.planetNum = planetNum;
		}	  
	}
	
	// System generation algorithm
	/*
		For each system:
			First create a star using one of the ten possible options picked at random
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

