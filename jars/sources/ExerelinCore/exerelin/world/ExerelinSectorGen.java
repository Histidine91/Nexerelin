package exerelin.world;

import java.awt.Color;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.omnifac.OmniFacModPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.events.CoreEventProbabilityManager;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.plugins.*;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollectionUtils.CollectionFilter;
import org.lazywizard.lazylib.MathUtils;

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
	private static final String[] possibleStationNames = new String[] {"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge", "Customs", "Nest",
		"Port", "Quey", "Terminal", "Exchange", "View", "Wall", "Habitat", "Shipyard", "Backwater"};
	private static final String[] starBackgrounds = new String[]
	{
		"backgrounds/background1.jpg", "backgrounds/background2.jpg", "backgrounds/background3.jpg", "backgrounds/background4.jpg",
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

	
	private List possibleSystemNamesList = new LinkedList(Arrays.asList(possibleSystemNames));
	private List possiblePlanetNamesList = new LinkedList(Arrays.asList(possiblePlanetNames));
	
	private static final String[] planetTypes = new String[] {"desert", "jungle", "frozen", "terran", "arid", "water", "rocky_metallic", "rocky_ice", "barren"};
	private static final String[] planetTypesUninhabitable = new String[] {"barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable",
		"gas_giant", "ice_giant", "frozen", "rocky_ice"};
	private static final String[] planetTypesGasGiant = new String[] {"gas_giant", "ice_giant"};
	private static final String[] moonTypes = new String[] {"frozen", "barren", "rocky_ice", "rocky_metallic"};
	private static final String[] moonTypesUninhabitable = new String[] {"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice"};
	private static final String[] stationImages = new String[] {"station_side00", "station_side02", "station_side04"};
	
	private String[] factionIds = new String[]{};
	private List starPositions = new ArrayList();	
	private EntityData homeworld = null;

	private List<EntityData> habitablePlanets = new ArrayList<>();
	private List<EntityData> stations = new ArrayList<>();
	private Map<String, String> systemToRelay = new HashMap();
	
	public static Logger log = Global.getLogger(ExerelinSectorGen.class);

	private String getRandomFaction()
	{
		return factionIds[ExerelinUtils.getRandomInRange(0, factionIds.length-1)];
	}
	
	private void resetVars()
	{
		habitablePlanets = new ArrayList<>();
		stations = new ArrayList<>();
		ExerelinSetupData.getInstance().resetAvailableFactions();
		factionIds = ExerelinSetupData.getInstance().getAvailableFactions(Global.getSector());
	}
	
	private void addListToPicker(List list, WeightedRandomPicker picker)
	{
		for (Object object : list)
		{
		picker.add(object);
		}
	}
	
	private void addCommodityStockpile(MarketAPI market, String commodityID, float amountToAdd)
	{
		CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
		
		commodity.addToStockpile(amountToAdd);
		commodity.addToAverageStockpile(amountToAdd);
		
		if (market.getFactionId().equals("templars"))
		{
			CargoAPI cargoTemplars = market.getSubmarket("tem_templarmarket").getCargo();
			cargoTemplars.addCommodity(commodityID, amountToAdd * 0.2f);
			return;
		}
		
		CargoAPI cargoOpen = market.getSubmarket(Submarkets.SUBMARKET_OPEN).getCargo();
		CargoAPI cargoBlack = market.getSubmarket(Submarkets.SUBMARKET_BLACK).getCargo();
		CargoAPI cargoMilitary = null;
		if (market.hasSubmarket(Submarkets.GENERIC_MILITARY))
			cargoMilitary = market.getSubmarket(Submarkets.GENERIC_MILITARY).getCargo();
		
		if (commodityID.equals("agent"))
		{
			if (cargoMilitary != null)
			{
				cargoOpen.addCommodity(commodityID, amountToAdd * 0.02f);
				cargoMilitary.addCommodity(commodityID, amountToAdd * 0.11f);
				cargoBlack.addCommodity(commodityID, amountToAdd * 0.02f);
			}
			else
			{
				cargoOpen.addCommodity(commodityID, amountToAdd * 0.04f);
				cargoBlack.addCommodity(commodityID, amountToAdd * 0.11f);
			}
		}
		else if(!market.isIllegal(commodity))
			cargoOpen.addCommodity(commodityID, amountToAdd * 0.15f);
		else if (commodityID.equals("hand_weapons") && cargoMilitary != null)
		{
			cargoMilitary.addCommodity(commodityID, amountToAdd * 0.1f);
			cargoBlack.addCommodity(commodityID, amountToAdd * 0.05f);
		}
		else
			cargoBlack.addCommodity(commodityID, amountToAdd * 0.1f);
		//log.info("Adding " + amount + " " + commodityID + " to " + market.getName());
	}
	
	private void addCommodityStockpile(MarketAPI market, String commodityID, float minMult, float maxMult)
	{
		float multDiff = maxMult - minMult;
		float mult = minMult + (float)(Math.random()) * multDiff;
		CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
		float demand = commodity.getDemand().getDemandValue();
		float amountToAdd = demand*mult;
		addCommodityStockpile(market, commodityID, amountToAdd);
	}
		
	private void pickEntityInteractionImage(SectorEntityToken entity, MarketAPI market, String planetType, String entityType)
	{
		List allowedImages = new ArrayList();
		allowedImages.add(new String[]{"illustrations", "cargo_loading"} );
		allowedImages.add(new String[]{"illustrations", "hound_hangar"} );
		allowedImages.add(new String[]{"illustrations", "space_bar"} );

		boolean isStation = (entityType.equals("station"));
		boolean isMoon = (entityType.equals("moon")); 
		int size = market.getSize();

		if(market.hasCondition("urbanized_polity") || size >= 4)
		{
			allowedImages.add(new String[]{"illustrations", "urban00"} );
			allowedImages.add(new String[]{"illustrations", "urban01"} );
			allowedImages.add(new String[]{"illustrations", "urban02"} );
			allowedImages.add(new String[]{"illustrations", "urban03"} );
		}
		if(size >= 4)
		{
			allowedImages.add(new String[]{"illustrations", "industrial_megafacility"} );
			allowedImages.add(new String[]{"illustrations", "city_from_above"} );
		}
		if(isStation && size >= 3)
			allowedImages.add(new String[]{"illustrations", "jangala_station"} );
		if(entity.getFaction().getId().equals("pirates"))
			allowedImages.add(new String[]{"illustrations", "pirate_station"} );
		if(!isStation && (planetType.equals("rocky_metallic") || planetType.equals("rocky_barren")))
			allowedImages.add(new String[]{"illustrations", "vacuum_colony"} );
		//if (isMoon)
		//	allowedImages.add(new String[]{"illustrations", "asteroid_belt_moon"} );
		if(planetType.equals("desert") && isMoon)
			allowedImages.add(new String[]{"illustrations", "desert_moons_ruins"} );

		int index = ExerelinUtils.getRandomInRange(0,allowedImages.size()-1);
		String[] illustration = (String[])allowedImages.get(index);
		entity.setInteractionImage(illustration[0], illustration[1]);
	}
	
	private MarketAPI addMarketToEntity(SectorEntityToken entity, EntityData data, String owningFactionId, boolean isCapital)
	{
		// don't make the markets too big; they'll screw up the economy big time
		int marketSize = 1;
		EntityType entityType = data.type;
		String planetType = data.planetType;
		boolean isStation = (entityType == EntityType.STATION); 
		if (isStation) marketSize = 2 + ExerelinUtils.getRandomInRange(1, 2);	// stations are on average smaller
		else if (entityType == EntityType.MOON) marketSize = ExerelinUtils.getRandomInRange(1, 2) + ExerelinUtils.getRandomInRange(2, 3);
		else marketSize = ExerelinUtils.getRandomInRange(2, 3) + ExerelinUtils.getRandomInRange(2, 3);
		
		if (isCapital)
		{
			if (data == homeworld)
			{
				if (marketSize < 6) marketSize = 6;
			}
			else if (marketSize < 5) marketSize = 5;
		}
		// Alex says "You can set marketSize via MarketAPI, but it's not "nicely" mutable like other stats at the moment."
		// so to be safe we only spawn the market after we already know what size it'll be
		
		MarketAPI newMarket = Global.getFactory().createMarket(entity.getId() + "_market", entity.getName(), marketSize);
		newMarket.setPrimaryEntity(entity);
		entity.setMarket(newMarket);
		
		newMarket.setFactionId(owningFactionId);
		newMarket.setBaseSmugglingStabilityValue(0);
		
		newMarket.addCondition("population_" + marketSize);

		if (isCapital)
		{
			newMarket.addCondition(data == homeworld ? "headquarters" : "regional_capital");
			if (data == homeworld) newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
		}
		
		int minSizeForMilitaryBase = 5;
		if (isStation) minSizeForMilitaryBase = 4;
		
		if (marketSize >= minSizeForMilitaryBase)
		{
			newMarket.addCondition("military_base");
		}
		
		// planet type conditions
		if (planetType.equals("jungle")) {
			newMarket.addCondition("jungle");

			if(ExerelinUtils.getRandomInRange(0, 3) == 0)
				newMarket.addCondition("orbital_burns");
		}
		if (planetType.equals("water"))
			newMarket.addCondition("water");
		if (planetType.equals("arid"))
			newMarket.addCondition("arid");
		if (planetType.equals("terran"))
			newMarket.addCondition("terran");
		if (planetType.equals("desert"))
			newMarket.addCondition("desert");
		if (planetType.equals("frozen") || planetType.equals("rocky_ice"))
			newMarket.addCondition("ice");
		if (planetType.equals("barren") || planetType.equals("rocky_metallic"))
			newMarket.addCondition("uninhabitable");
				
		if(marketSize < 4 && !isStation){
			newMarket.addCondition("frontier");
		}
		
		// add random market conditions
		ExerelinMarketConditionPicker picker = new ExerelinMarketConditionPicker();
		picker.AddMarketConditions(newMarket, marketSize, planetType, isStation);

		if (isStation && marketSize >= 3)
		{
			newMarket.addCondition("exerelin_recycling_plant");
		}
				
		// add per-faction market conditions
		String factionId = newMarket.getFaction().getId();
		if(factionId.equals("tritachyon")) {
			newMarket.addCondition("free_market");
		}
		if(factionId.equals("luddic_church")) {
			newMarket.addCondition("luddic_majority");
			//newMarket.addCondition("cottage_industry");
		}
		if(factionId.equals("pirates")) {
			newMarket.addCondition("free_market");
		}
		
		if (owningFactionId.equals("templars"))
		{
			newMarket.addSubmarket("tem_templarmarket");
		}
		else
		{
			newMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
			newMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
		}
		newMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		// seed the market with some stuff to prevent initial shortage
		// because the vanilla one is broken for some reason
		addCommodityStockpile(newMarket, "green_crew", 0.45f, 0.55f);
		addCommodityStockpile(newMarket, "regular_crew", 0.45f, 0.55f);
		addCommodityStockpile(newMarket, "veteran_crew", 0.1f, 0.2f);
		addCommodityStockpile(newMarket, "marines", 0.8f, 1.0f);
		addCommodityStockpile(newMarket, "supplies", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "fuel", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "food", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "domestic_goods", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "luxury_goods", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "heavy_machinery", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "metals", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "rare_metals", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "ore", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "rare_ore", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "organics", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "volatiles", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "hand_weapons", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "drugs", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "organs", 0.7f, 0.8f);
		addCommodityStockpile(newMarket, "lobster", 0.7f, 0.8f);
		//if (marketSize >= 4)
		//	addCommodityStockpile(newMarket, "agent", marketSize);
		
		// set tariffs
		if (newMarket.hasCondition("free_market"))
		{
			newMarket.getTariff().modifyFlat("generator", 0.1f);
		}
		else
		{
			newMarket.getTariff().modifyFlat("generator", 0.2f);	// TODO put in a config or something 
		}
		
		Global.getSector().getEconomy().addMarket(newMarket);
		entity.setFaction(owningFactionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		return newMarket;
	}
		
	public void addOmnifactory()
	{
		if (!ExerelinSetupData.getInstance().omnifactoryPresent) return;

		SectorEntityToken toOrbit = null;
		log.info("Randomized omnifac location: " + ExerelinSetupData.getInstance().randomOmnifactoryLocation);
		
		if (ExerelinSetupData.getInstance().randomOmnifactoryLocation)
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
			toOrbit = homeworld.entity;
		
		
		LocationAPI system = toOrbit.getContainingLocation();
		log.info("Placing Omnifactory around " + toOrbit.getName() + ", in the " + system.getName());
		String image = stationImages[ExerelinUtils.getRandomInRange(0, stationImages.length - 1)];
		SectorEntityToken omnifac = system.addCustomEntity("omnifactory", "Omnifactory", image, "neutral");
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		if (toOrbit instanceof PlanetAPI)
		{
			orbitDistance = radius + ExerelinUtils.getRandomInRange(3000, 12000);
		}
		omnifac.setCircularOrbitPointingDown(toOrbit, ExerelinUtils.getRandomInRange(1, 360), orbitDistance, getOrbitalPeriod(radius, orbitDistance, 2));
		OmniFacModPlugin.initOmnifactory(omnifac);
		omnifac.setInteractionImage("illustrations", "abandoned_station");
		omnifac.setCustomDescriptionId("omnifactory");

		omnifac.setFaction("neutral");
		MarketAPI market = omnifac.getMarket();
		market.setFactionId("neutral");
		List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
		for (SubmarketAPI submarket : submarkets) {
			submarket.setFaction(Global.getSector().getFaction("neutral"));
		}
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
				
			possibleSystemNamesList = new ArrayList();
			for (int i=0; i < possibleSystemNames.length; i++)
				possibleSystemNamesList.add(possibleSystemNames[i]);
			possiblePlanetNamesList = new ArrayList();
			for (int i=0; i < possiblePlanetNames.length; i++)
				possiblePlanetNamesList.add(possiblePlanetNames[i]);
			
		} catch (JSONException | IOException ex) {
			Global.getLogger(ExerelinSectorGen.class).log(Level.ERROR, ex);
		}
		
		resetVars();
		
		// stars will be distributed in a concentric pattern
		float angle = 0;
		float distance = 0;
		for(int i = 0; i < ExerelinSetupData.getInstance().numSystems; i ++)
		{
			angle += MathUtils.getRandomNumberInRange((float)Math.PI/4, (float)Math.PI);
			distance = distance + MathUtils.getRandomNumberInRange(250, 1250) + MathUtils.getRandomNumberInRange(250, 1250);
			int x = (int)(Math.sin(angle) * distance);
			int y = (int)(Math.cos(angle) * distance);
			starPositions.add(new int[] {x, y});
		}
		Collections.shuffle(starPositions);
		
		
		
		// build systems
		for(int i = 0; i < ExerelinSetupData.getInstance().numSystems; i ++)
			buildSystem(sector, i);
		populateSector();
		addOmnifactory();
		
		ExerelinConfig.loadSettings();
		String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);

		sector.registerPlugin(new ExerelinCoreCampaignPlugin());
		
		sector.addScript(new CoreScript());
		sector.addScript(new ForcePatrolFleetsScript());
		//sector.addScript(new EconomyLogger());
		sector.addScript(new CoreEventProbabilityManager());
		sector.addScript(new EconomyFleetManager());
		
		sector.addScript(SectorManager.create());
		sector.addScript(DiplomacyManager.create());
		sector.addScript(new InvasionFleetManager());
		sector.addScript(ResponseFleetManager.create());
		sector.addScript(CovertOpsManager.create());
		
		DiplomacyManager.initFactionRelationships();
		
		SectorManager.setSystemToRelayMap(systemToRelay);
		
		// some cleanup
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : markets) {
			if (market.getFactionId().equals("templars"))
			{
			market.removeSubmarket(Submarkets.GENERIC_MILITARY); // auto added by military base; remove it
			}
		}
		
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
	
	private SectorEntityToken makeStation(EntityData data, String factionId)
	{
		int angle = ExerelinUtils.getRandomInRange(1, 360);
		int orbitRadius = 300;
		PlanetAPI planet = (PlanetAPI)data.primary.entity;
		if (data.primary.type == EntityType.MOON)
			orbitRadius = 200;
		else if (planet.isGasGiant())
			orbitRadius = 500;
		else if (planet.isStar())
			orbitRadius = (int)data.orbitDistance;
		float orbitDays = getOrbitalPeriod(planet.getRadius(), orbitRadius + planet.getRadius(), 2);

		String name = planet.getName() + " " + data.name;
		String id = name.replace(' ','_');
		String image = stationImages[ExerelinUtils.getRandomInRange(0, stationImages.length - 1)];
		if (factionId.equals("shadow_industry"))
			image = "station_shi_prana";	// custom station image for Shadowyards
		
		SectorEntityToken newStation = data.starSystem.addCustomEntity(id, name, image, factionId);
		newStation.setCircularOrbitPointingDown(planet, angle, orbitRadius, orbitDays);
		
		MarketAPI existingMarket = planet.getMarket();
		if (existingMarket != null)
		{
			existingMarket.addCondition("orbital_station");
			existingMarket.addCondition("exerelin_recycling_plant");
			newStation.setMarket(existingMarket);
			existingMarket.getConnectedEntities().add(newStation);
		}
		else
		{	
			addMarketToEntity(newStation, data, factionId, false);
		}
		pickEntityInteractionImage(newStation, newStation.getMarket(), "", "station");
		
		return newStation;
	}
	
	public void populateSector()
	{
		SectorAPI sector = Global.getSector();
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>();
		List<String> factions = new ArrayList<>(Arrays.asList(factionIds));
		factions.remove("player_npc");  // player NPC faction only gets homeworld (if applicable)
		
		// before we do anything else give the "homeworld" to our faction
		String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
		addMarketToEntity(homeworld.entity, homeworld, alignedFactionId, true);
		SectorEntityToken relay = sector.getEntityById(systemToRelay.get(homeworld.starSystem.getId()));
		relay.setFaction(alignedFactionId);
		
		Collections.shuffle(habitablePlanets);
		Collections.shuffle(stations);
		
		// add factions and markets to planets
		for (EntityData habitable : habitablePlanets)
		{
			if (habitable == homeworld) continue;
			if (factionPicker.isEmpty()) addListToPicker(factions, factionPicker);
			String factionId = factionPicker.pickAndRemove();
			addMarketToEntity(habitable.entity, habitable, factionId, habitable.isCapital);
			
			// assign relay
			if (habitable.isCapital)
			{   
				relay = sector.getEntityById(systemToRelay.get(habitable.starSystem.getId()));
				relay.setFaction(factionId);
			}
		}
		
		// we didn't actually create the stations before, so do so now
		for (EntityData station : stations)
		{
			String factionId = "neutral";
			if (station.primary.entity.getMarket() == null)
			{
			if (factionPicker.isEmpty()) addListToPicker(factions, factionPicker);
			factionId = factionPicker.pickAndRemove();
			}
			else
			{
			factionId = station.primary.entity.getFaction().getId();
			}
			makeStation(station, factionId);
		}
	}

	public SectorEntityToken makeStar(int index, String systemId, StarSystemAPI system, String type, float size)
	{
		int[] pos = (int[])starPositions.get(index);
		int x = pos[0];
		int y = pos[1];
		return system.initStar(systemId, type, 500f, x, y);
	}
	
	private float getHabitableChance(int planetNum, boolean isMoon)
	{
		float habitableChance = 0.3f;
		if (planetNum == 0) habitableChance = 0.4f;
		else if (planetNum == 1 || planetNum == 3) habitableChance = 0.7f;
		else if (planetNum == 2) habitableChance = 0.9f;
			
		if (isMoon) habitableChance *= 0.7f;
		
		return habitableChance;
	}
		
	public void buildSystem(SectorAPI sector, int systemIndex)
	{
		// First we make a star system with random name
		int systemNameIndex = ExerelinUtils.getRandomInRange(0, possibleSystemNamesList.size() - 1);
		if (systemIndex == 0) systemNameIndex = 0;	// there is always a starSystem named Exerelin
		StarSystemAPI system = sector.createStarSystem((String)possibleSystemNamesList.get(systemNameIndex));
		possibleSystemNamesList.remove(systemNameIndex);
		String systemName = system.getName();
		String systemId = system.getId();
		EntityData capital = null;
		
		// Set starSystem/light colour/background
		SectorEntityToken star;
	
		int starType = 0;
		if(ExerelinConfig.useMultipleBackgroundsAndStars)
			starType = ExerelinUtils.getRandomInRange(0, 10);
		else
			starType = ExerelinUtils.getRandomInRange(0, 1);

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
		system.setBackgroundTextureFilename("graphics/" + starBackgrounds[ExerelinUtils.getRandomInRange(0, starBackgrounds.length - 1)]);

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
			numBasePlanets = ExerelinUtils.getRandomInRange(ExerelinConfig.minimumPlanets, maxPlanets);
		else
			numBasePlanets = maxPlanets;
		int distanceStepping = (ExerelinSetupData.getInstance().maxSystemSize-4000)/ExerelinUtils.getRandomInRange(numBasePlanets, maxPlanets+1);
		
		boolean gasPlanetCreated = false;
		int habitableCount = 0;
		List<EntityData> uninhabitables1To4 = new ArrayList<>();
		
		for(int i = 0; i < numBasePlanets; i = i + 1)
		{
			float habitableChance = getHabitableChance(i, false);
			
			boolean habitable = Math.random() <= habitableChance;
			String planetType = "";
			String owningFactionId = getRandomFaction();
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
		
		// make sure there are at least two planetData planets
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
			int planetNameIndex = ExerelinUtils.getRandomInRange(0, possiblePlanetNamesList.size() - 1);
			name = (String)(possiblePlanetNamesList.get(planetNameIndex));
			possiblePlanetNamesList.remove(planetNameIndex);

			id = name.replace(' ','_');
			
			if (planetData.habitable)
			{
				planetType = planetTypes[ExerelinUtils.getRandomInRange(0, planetTypes.length - 1)];
			}
			else
			{
				float gasGiantChance = 0.45f;
				if (planetData.planetNum == 3) gasGiantChance = 0.3f;
				else if (planetData.planetNum < 3) gasGiantChance = 0;
				
				isGasGiant = Math.random() < gasGiantChance;
				if (isGasGiant) planetType = planetTypesGasGiant[ExerelinUtils.getRandomInRange(0, planetTypesGasGiant.length - 1)];
				else planetType = planetTypesUninhabitable[ExerelinUtils.getRandomInRange(0, planetTypesUninhabitable.length - 1)];
			}
			
			float radius;
			float angle = ExerelinUtils.getRandomInRange(1, 360);
			float distance = 3000 + (distanceStepping * (planetData.planetNum)) + ExerelinUtils.getRandomInRange((distanceStepping/3)*-1, distanceStepping/3);
			float orbitDays = getOrbitalPeriod(star.getRadius(), distance + star.getRadius(), 1);
			if (isGasGiant)
			{
				radius = ExerelinUtils.getRandomInRange(325, 375);
				gasPlanetCreated = true;
			}
			else
				radius = ExerelinUtils.getRandomInRange(150, 250);

			// At least one gas giant per system
			if(!gasPlanetCreated && planetData.planetNum == numBasePlanets - 1)
			{
				planetType = planetTypesGasGiant[ExerelinUtils.getRandomInRange(0, planetTypesGasGiant.length - 1)];
				radius = ExerelinUtils.getRandomInRange(325, 375);
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
				for(int j = 0; j < ExerelinUtils.getRandomInRange(0, ExerelinSetupData.getInstance().maxMoonsPerPlanet - 1); j = j + 1)
				{
					String ext = "";
					if(j == 0)
						ext = "I";
					if(j == 1)
						ext = "II";
					if(j == 2)
						ext = "III";
					
					boolean moonInhabitable = Math.random() <= 0.4;	//getHabitableChance(planetData.planetNum, true);
					String moonType = "";
					if (moonInhabitable)
						moonType = moonTypes[ExerelinUtils.getRandomInRange(0, moonTypes.length - 1)];
					else
						moonType = moonTypesUninhabitable[ExerelinUtils.getRandomInRange(0, moonTypesUninhabitable.length - 1)];
						
					angle = ExerelinUtils.getRandomInRange(1, 360);
					distance = ExerelinUtils.getRandomInRange(650, 1300);
					float moonRadius = ExerelinUtils.getRandomInRange(50, 100);
					orbitDays = getOrbitalPeriod(star.getRadius(), distance + star.getRadius(), 2);
					PlanetAPI newMoon = system.addPlanet(name + " " + ext, newPlanet, name + " " + ext, moonType, angle, moonRadius, distance, orbitDays);
					
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
				int ringType = ExerelinUtils.getRandomInRange(0,3);

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
		}

		// Build asteroid belts
		// If the belt orbits a star, add it to a list so that we can seed belter stations later
		List<PlanetAPI> planets = system.getPlanets();
		List<Float> starBelts = new ArrayList<>();
		int numAsteroidBelts;
		if(ExerelinSetupData.getInstance().numSystems != 1)
			numAsteroidBelts = ExerelinUtils.getRandomInRange(ExerelinConfig.minimumAsteroidBelts, ExerelinSetupData.getInstance().maxAsteroidBelts);
		else
			numAsteroidBelts = ExerelinSetupData.getInstance().maxAsteroidBelts;

		for(int j = 0; j < numAsteroidBelts; j = j + 1)
		{
			PlanetAPI planet = planets.get(ExerelinUtils.getRandomInRange(0, planets.size() - 1));

			float orbitRadius;
			int numAsteroids;

			if (planet.getFullName().contains(" I") || planet.getFullName().contains(" II") || planet.getFullName().contains(" III"))
			{
				orbitRadius = ExerelinUtils.getRandomInRange(250, 350);
				numAsteroids = 2;
			}
			else if(planet.isGasGiant())
			{
				orbitRadius = ExerelinUtils.getRandomInRange(700, 900);
				numAsteroids = 10;
			}
			else if (planet.isStar())
			{
				orbitRadius = ExerelinUtils.getRandomInRange(1000, 8000);
				numAsteroids = 50;
			}
			else
			{
				orbitRadius = ExerelinUtils.getRandomInRange(400, 550);
				numAsteroids = 6;
			}

			float width = ExerelinUtils.getRandomInRange(10, 50);
			float baseOrbitDays = getOrbitalPeriod(planet.getRadius(), orbitRadius, planet.isStar() ? 1 : 2);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			system.addAsteroidBelt(planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
			if (planet.isStar()) starBelts.add(orbitRadius);
		}

		// Always put an asteroid belt around the sun
		do {
			float distance = ExerelinUtils.getRandomInRange(1500, 8000);
			float baseOrbitDays = getOrbitalPeriod(star.getRadius(), distance, 1);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			
			system.addAsteroidBelt(star, 25, distance, ExerelinUtils.getRandomInRange(10, 50), minOrbitDays, maxOrbitDays);
			starBelts.add(distance);

			// Another one if medium system size
			if(ExerelinSetupData.getInstance().maxSystemSize > 16000)
			{
				distance = ExerelinUtils.getRandomInRange(12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star.getRadius(), distance, 1);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				system.addAsteroidBelt(star, 50, distance, ExerelinUtils.getRandomInRange(50, 100), minOrbitDays, maxOrbitDays);
				starBelts.add(distance);
			}
			// And another one if a large system
			if(ExerelinSetupData.getInstance().maxSystemSize > 32000)
			{
				distance = ExerelinUtils.getRandomInRange(12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star.getRadius(), distance, 1);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				system.addAsteroidBelt(star, 75, distance, ExerelinUtils.getRandomInRange(100, 150),  minOrbitDays, maxOrbitDays);
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
			numStation = ExerelinUtils.getRandomInRange(ExerelinConfig.minimumStations, Math.min(ExerelinSetupData.getInstance().maxStations, numBasePlanets*2));
		else
			numStation = ExerelinSetupData.getInstance().maxStations;
		
		WeightedRandomPicker<EntityData> picker = new WeightedRandomPicker<>();
		//addListToPicker(entities, picker);
		for (EntityData entityData : entities)
		{
			float weight = 1f;
			if (entityData.type == EntityType.STAR) weight = 0.5f;
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
			if (isStar) stationData.orbitDistance = starBelts.get(ExerelinUtils.getRandomInRange(0, starBelts.size() - 1));
			
			boolean nameOK = false;
			String name = "";
			while(!nameOK)
			{
				name = possibleStationNames[ExerelinUtils.getRandomInRange(0, possibleStationNames.length - 1)];
				if (!alreadyUsedStationNames.contains(name))
					nameOK = true;
			}
			alreadyUsedStationNames.add(name);
			stationData.name = name;			
			stations.add(stationData);

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
		EntityType type = EntityType.PLANET;
		StarSystemAPI starSystem;
		EntityData primary;
		MarketAPI market;
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
	
	// ALGO
	/*
		First create planets according to the following rules:
		first planet: 40% planetData chance
		second planet: 70% planetData chance
		third planet: 90% planetData chance
		fourth planet: 70% planetData chance, 30% gas giant chance if fail habitability check
		fifth and higher planet: 30% planetData chance, 45% gas giant chance
		last planet will always be gas giant if one hasn't been added yet 
		moon: 70% planet's planetData chance
		if not at least two planetData entities, randomly pick planets 1-4 and force them to be planetData
		Don't actually generate PlanetAPIs until all EntityDatas have been created
	
		Next seed stations randomly around planets/moons or in asteroid belts
		
		Next go through all entities
		If planetData planet/moon, add to list of habitables
		If station orbiting uninhabitable, add to list of independent stations
		If station orbiting planetData, add to list of associated stations
	
		Next go through all inhabitables
		First off we go to Exerelin and give our faction a planet with HQ
		Next line up factions (exclude our own faction for this round), pick at random and remove from list
		Give this faction an inhabitable planet; add market to it
		If this is the first inhabitable in a starSystem system, mark this as regional capital
			Set minimum size and assign relay/jumpgate accordingly
		Once list is empty, refill with all factions (including ours) again
		Repeat until all habitables have been populated
	
		Do that again except for independent stations
	
		Lastly we go through all associated stations
		Associate them with the market of the planet/moon they orbit
	*/
}

