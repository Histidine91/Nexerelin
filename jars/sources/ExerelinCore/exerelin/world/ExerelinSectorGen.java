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
import com.fs.starfarer.api.impl.campaign.CoreScript;
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
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.econ.Exerelin_Hydroponics;
import data.scripts.campaign.econ.Exerelin_RecyclingPlant;
import data.scripts.world.systems.SSP_Arcadia;
import data.scripts.world.systems.SSP_Askonia;
import data.scripts.world.systems.SSP_Corvus;
import data.scripts.world.systems.SSP_Eos;
import data.scripts.world.systems.SSP_Magec;
import data.scripts.world.systems.SSP_Valhalla;
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
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.world.ExerelinMarketSetup.MarketArchetype;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollectionUtils.CollectionFilter;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.orbits.EllipticalOrbit;
import org.lazywizard.omnifac.OmniFac;

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
	
	protected static ArrayList<String> starBackgrounds = new ArrayList<>(Arrays.asList(starBackgroundsArray));

	protected List<String> possibleSystemNamesList = new ArrayList(Arrays.asList(possibleSystemNames));
	protected List<String> possiblePlanetNamesList = new ArrayList(Arrays.asList(possiblePlanetNames));
	protected List<String> possibleStationNamesList = new ArrayList(Arrays.asList(possibleStationNames));
	
	//protected static final String[] planetTypes = new String[] {"desert", "jungle", "frozen", "terran", "arid", "water", "rocky_metallic", "rocky_ice", "barren", "barren-bombarded"};
	protected static final String[] planetTypesUninhabitable = new String[] 
		{"desert", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "frozen", "rocky_ice", "radiated", "barren-bombarded"};
	protected static final String[] planetTypesGasGiant = new String[] {"gas_giant", "ice_giant"};
	//protected static final String[] moonTypes = new String[] {"frozen", "barren", "barren-bombarded", "rocky_ice", "rocky_metallic", "desert", "water", "jungle"};
	protected static final String[] moonTypesUninhabitable = new String[] 
		{"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice", "radiated", "barren-bombarded", "desert", "water", "jungle"};
	
	protected static final Map<String, String[]> stationImages = new HashMap<>();
	
	protected static final float REVERSE_ORBIT_CHANCE = 0.2f;
	protected static final float BINARY_STAR_DISTANCE = 11000;
	protected static final float BINARY_SYSTEM_PLANET_MULT = 1.25f;
	
	// extremely sensitive to small changes, avoid touching these for now
	// TODO externalise?
	protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MIN = 3.25f;	// needs to be ridiculously high to be affordable
	protected static final float SUPPLIES_SUPPLY_DEMAND_RATIO_MAX = 2.5f;	// yes, lower than min
	
	protected ExerelinMarketSetup marketSetup = new ExerelinMarketSetup();
	
	protected List<String> factionIds = new ArrayList<>();
	protected List<Integer[]> starPositions = new ArrayList<>();	
	protected EntityData homeworld = null;

	protected List<EntityData> habitablePlanets = new ArrayList<>();
	//protected List<EntityData> habitableMoons = new ArrayList<>();	// TODO
	protected List<EntityData> stations = new ArrayList<>();
	protected Map<String, String> systemToRelay = new HashMap();
	protected Map<String, String> planetToRelay = new HashMap();
	
	protected Map<MarketArchetype, Integer> numMarketsByArchetype = new HashMap<>();
	protected WeightedRandomPicker<MarketArchetype> marketArchetypeQueue = new WeightedRandomPicker<>();
	protected int marketArchetypeQueueNum = 0;
	protected double domesticGoodsDemand = 0;
	protected double domesticGoodsSupply = 0;
	protected double suppliesDemand = 0;
	protected double suppliesSupply = 0;
	protected double metalDemand = 0;
	protected double metalSupply = 0;
	protected double foodDemand = 0;
	protected double foodSupply = 0;
	
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
		stationImages.put("tiandong", new String[] {"tiandong_outpost"} );
	}
	
	protected void loadBackgrounds()
	{
		starBackgrounds = new ArrayList<>(Arrays.asList(starBackgroundsArray));
		List<String> factions = ExerelinSetupData.getInstance().getAvailableFactions();
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
			//starBackgrounds.add("neut/backgrounds/CoronaAustralis.jpg");
		}
		if (factions.contains("pn_colony"))
		{
			starBackgrounds.add("backgrounds/tolpbg.jpg");
		}
		if (factions.contains("SCY"))
		{
			starBackgrounds.add("SCY/backgrounds/SCY_acheron.jpg");
			starBackgrounds.add("SCY/backgrounds/SCY_tartarus.jpg");
		}
		if (factions.contains("shadow_industry"))
		{
			starBackgrounds.add("backgrounds/anarbg.jpg");
		}
		if (ExerelinUtils.isSSPInstalled())
		{
			starBackgrounds.add("ssp/backgrounds/ssp_arcade.png");
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

	protected String getRandomFaction()
	{
		return (String) ExerelinUtils.getRandomListElement(factionIds);
	}
	
	protected List<String> getStartingFactions()
	{
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		List<String> availableFactions = setupData.getAvailableFactions();
		int wantedFactionNum = setupData.numStartFactions;
		if (wantedFactionNum <= 0) return availableFactions;
		
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
		ExerelinSetupData.getInstance().resetAvailableFactions();
		factionIds = getStartingFactions();
		numMarketsByArchetype = new HashMap<>();
		marketArchetypeQueue.clear();
		marketArchetypeQueueNum = 0;
		numOmnifacs = 0;
		domesticGoodsDemand = 0;
		domesticGoodsSupply = 0;
		metalDemand = 0;
		metalSupply = 0;
		suppliesDemand = 0;
		suppliesSupply = 0;
	}
	
	protected void addListToPicker(List list, WeightedRandomPicker picker)
	{
		for (Object object : list)
		{
			picker.add(object);
		}
	}
	
	protected int getNumMarketsOfArchetype(MarketArchetype type)
	{
		if (!numMarketsByArchetype.containsKey(type))
		{
			numMarketsByArchetype.put(type, 0);
			return 0;
		}
		return numMarketsByArchetype.get(type);
	}
	
	// Tetris type archetype rotation
	protected void queueMarketArchetypes()
	{
		marketArchetypeQueueNum++;
		
		// always have at least one of each in the queue (usually)
		marketArchetypeQueue.add(MarketArchetype.AGRICULTURE);
		if (marketArchetypeQueueNum % 5 == 1)	// add an extra agriculture market every fifth round
			marketArchetypeQueue.add(MarketArchetype.AGRICULTURE);
		
		marketArchetypeQueue.add(MarketArchetype.ORE);
		//if (marketArchetypeQueueNum % 5 == 0)	// add an extra ore market every fifth round
		//	marketArchetypeQueue.add(MarketArchetype.ORE);
		
		if (marketArchetypeQueueNum % 6 != 3)	// skip every sixth organics market
			marketArchetypeQueue.add(MarketArchetype.ORGANICS);
		
		if (marketArchetypeQueueNum % 4 != 2)	// skip every fourth volatiles market
			marketArchetypeQueue.add(MarketArchetype.VOLATILES);
		
		marketArchetypeQueue.add(MarketArchetype.MANUFACTURING);
		
		if (marketArchetypeQueueNum % 4 != 3)	// skip every fourth heavy industry market
			marketArchetypeQueue.add(MarketArchetype.HEAVY_INDUSTRY);
		
		//marketArchetypeQueue.add(MarketArchetype.MIXED);
		
		// add up to three more distinct archetypes based on how many of each already exist
		/*
		int numAgriculture = getNumMarketsOfArchetype(MarketArchetype.AGRICULTURE) + 1;
		int numOre = getNumMarketsOfArchetype(MarketArchetype.ORE) + 1;
		int numOrganics = getNumMarketsOfArchetype(MarketArchetype.ORGANICS) + 1;
		int numVolatiles = getNumMarketsOfArchetype(MarketArchetype.VOLATILES) + 1;
		int numManufacturing = getNumMarketsOfArchetype(MarketArchetype.MANUFACTURING) + 1;
		int numHeavyIndustry = getNumMarketsOfArchetype(MarketArchetype.HEAVY_INDUSTRY) + 1;
		
		WeightedRandomPicker<MarketArchetype> picker = new WeightedRandomPicker<>();
		picker.add(MarketArchetype.AGRICULTURE, 10/numAgriculture);
		picker.add(MarketArchetype.ORE, 10/numOre);
		picker.add(MarketArchetype.ORGANICS, 8/numOrganics);
		picker.add(MarketArchetype.VOLATILES, 6/numVolatiles);
		picker.add(MarketArchetype.MANUFACTURING, 10/numManufacturing);
		picker.add(MarketArchetype.HEAVY_INDUSTRY, 8/numHeavyIndustry);
		
		for (int i=0; i<MathUtils.getRandomNumberInRange(2, 3); i++)
		{
			marketArchetypeQueue.add(picker.pickAndRemove());
		}
		*/
	}
	
	protected MarketArchetype pickMarketArchetype(boolean isStation)
	{
		int tries = 0;
		while (true)
		{
			tries++;
			if (marketArchetypeQueue.isEmpty())
				queueMarketArchetypes();
			MarketArchetype type = marketArchetypeQueue.pickAndRemove();
			if (tries < 5 && isStation && type == MarketArchetype.AGRICULTURE)
				continue;
			return type;
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
	
	// add enough light industrial complexes to balance out domestic good supply/demand
	// can also remove excess ones
	protected void balanceDomesticGoods(List<EntityData> candidateEntities)
	{
		final int HALFPOW5 = (int)Math.pow(10, 5)/2;
		final int HALFPOW4 = (int)Math.pow(10, 4)/2;
		
		log.info("Pre-balance domestic goods supply/demand: " + (int)domesticGoodsSupply + " / " + (int)domesticGoodsDemand);
		
		WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = 100 - (entity.bonusMarketPoints/(size-1));
			if (market.hasCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX)) 
			{
				// oversupply; remove this LIC and prioritise the market for any readding later
				if (domesticGoodsSupply > domesticGoodsDemand * 1.2)
				{
					market.removeCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
					domesticGoodsSupply -= ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS_MULT * ExerelinUtilsMarket.getPopulation(size) 
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
					weight *= 25;
					log.info("Removed balancing Light Industrial Complex from " + market.getName() + " (size " + size + ")");
				}
				else continue;
			}
			if (market.hasCondition(Conditions.COTTAGE_INDUSTRY)) weight *= 0.25f;
			
			switch (entity.archetype)
			{
				case AGRICULTURE:
					weight *= 0.5f;
					break;
				case MANUFACTURING:
					weight *= 4f;
					break;
				case HEAVY_INDUSTRY:
					weight *= 2f;
					break;
				case MIXED:
					weight *= 1.5f;
					break;
			}
			
			marketPicker.add(market, weight);
		}
		
		while ((domesticGoodsDemand * 0.9) > domesticGoodsSupply)
		{
			if (marketPicker.isEmpty())	break;	// fuck it, we give up
			
			int maxSize = 6;
			double shortfall = domesticGoodsDemand - domesticGoodsSupply;
			if (shortfall < HALFPOW4)
				maxSize = 4;
			else if (shortfall < HALFPOW5)
				maxSize = 5;
			
			MarketAPI market = marketPicker.pickAndRemove();
			int size = market.getSize();
			if (size > maxSize) continue;
			
			market.addCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
			domesticGoodsSupply += ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS_MULT * ExerelinUtilsMarket.getPopulation(size) 
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.DOMESTIC_GOODS);
			log.info("Added balancing Light Industrial Complex to " + market.getName() + " (size " + size + ")");
		}
		log.info("Final domestic goods supply/demand: " + (int)domesticGoodsSupply + " / " + (int)domesticGoodsDemand);
	}
	
	protected void balanceSuppliesAndMetal(List<EntityData> candidateEntities)
	{	
		log.info("Pre-balance supplies supply/demand: " + (int)suppliesSupply + " / " + (int)suppliesDemand);
		log.info("Pre-balance metal supply/demand: " + (int)metalSupply + " / " + (int)metalDemand);
		
		WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			if (size <= 3) continue;
			float weight = 100 - (entity.bonusMarketPoints/(size-1));
			
			//log.info("Testing entity for supply/metal balance: " + entity.entity.getName() + " | " + homeworld.entity.getName() + " | " + (entity == homeworld));
			
			if (market.hasCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY)) 
			{
				// not enough metal or too many supplies; remove this autofac and prioritise the market for any readding later
				if (entity.market != homeworld.market)
				{ 
					if((metalDemand > metalSupply + ConditionData.AUTOFAC_HEAVY_METALS * 1.25) || ((suppliesSupply / suppliesDemand) > SUPPLIES_SUPPLY_DEMAND_RATIO_MAX))
					{
						int autofacCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.AUTOFAC_HEAVY_INDUSTRY);
						market.removeCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);	// removes all
						for (int i=0; i<autofacCount - 1; i++)
							market.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);	// add back all but one
						
						suppliesSupply -= ConditionData.AUTOFAC_HEAVY_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
						metalDemand -= ConditionData.AUTOFAC_HEAVY_METALS;
						//if (metalDemand < 20000) metalDemand = 20000;
						weight *= 25;
						log.info("Removed balancing heavy autofac from " + market.getName());
					}
				}
			}
			else if (market.hasCondition(Conditions.SHIPBREAKING_CENTER)) 
			{
				// too many supplies or metal
				if((metalSupply > metalDemand + ConditionData.SHIPBREAKING_METALS * 1.25) || ((suppliesSupply / suppliesDemand) > SUPPLIES_SUPPLY_DEMAND_RATIO_MAX))
				{
					int shipbreakingCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.SHIPBREAKING_CENTER);
					market.removeCondition(Conditions.SHIPBREAKING_CENTER);	// removes all
					for (int i=0; i<shipbreakingCount - 1; i++)
						market.addCondition(Conditions.SHIPBREAKING_CENTER);	// add back all but one

					suppliesSupply -= ConditionData.SHIPBREAKING_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
					metalSupply -= ConditionData.SHIPBREAKING_METALS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
					weight *= 25;
					log.info("Removed balancing shipbreaking center from " + market.getName());
				}
			}
			
			switch (entity.archetype)
			{
				case MANUFACTURING:
					weight *= 1.5f;
					break;
				case HEAVY_INDUSTRY:
					weight *= 3f;
					break;
				case MIXED:
					weight *= 1f;
					break;
				default:
					weight = 0;
			}
			if (weight == 0) continue;
			marketPicker.add(market, weight);
		}
		
		while ((suppliesSupply/suppliesDemand) < SUPPLIES_SUPPLY_DEMAND_RATIO_MIN)
		{
			if (marketPicker.isEmpty())	break;	// fuck it, we give up
			
			MarketAPI market = marketPicker.pickAndRemove();
			if (metalSupply > metalDemand + ConditionData.AUTOFAC_HEAVY_METALS * 0.75f)
			{
				market.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
				suppliesSupply += ConditionData.AUTOFAC_HEAVY_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
				metalDemand += ConditionData.AUTOFAC_HEAVY_METALS;
				log.info("Added balancing heavy autofac to " + market.getName());
			}
			else if (market.getSize() >= 5)	// not enough metal to support an autofac; add a shipbreaking center instead
			{
				market.addCondition(Conditions.SHIPBREAKING_CENTER);
				suppliesSupply += ConditionData.SHIPBREAKING_SUPPLIES * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.SUPPLIES);
				metalSupply += ConditionData.SHIPBREAKING_METALS * ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.METALS);
				log.info("Added balancing shipbreaking center to " + market.getName());
			}
		}
		log.info("Final supplies supply/demand: " + (int)suppliesSupply + " / " + (int)suppliesDemand);
		log.info("Final metal supply/demand: " + (int)metalSupply + " / " + (int)metalDemand);
	}
	
	protected void balanceFood(List<EntityData> candidateEntities)
	{
		final int HALFPOW4 = (int)Math.pow(10, 4)/2;
		final int HALFPOW3 = (int)Math.pow(10, 3)/2;
		
		log.info("Pre-balance food supply/demand: " + (int)foodSupply + " / " + (int)foodDemand);
		
		WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
		for (EntityData entity:candidateEntities)
		{
			MarketAPI market = entity.market;
			if (market == null) continue;
			int size = market.getSize();
			float weight = 100 - (entity.bonusMarketPoints/(size-1));
			if (market.hasCondition("exerelin_hydroponics")) 
			{
				if (foodSupply > foodDemand)
				{
					int hydroponicsCount = ExerelinUtilsMarket.countMarketConditions(market, "exerelin_hydroponics");
					market.removeCondition("exerelin_hydroponics");	// removes all
					for (int i=0; i<hydroponicsCount - 1; i++)
						market.addCondition("exerelin_hydroponics");	// add back all but one
					foodSupply -= Exerelin_Hydroponics.HYDROPONICS_FOOD_POP_MULT * ExerelinUtilsMarket.getPopulation(size) 
							* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FOOD);
					weight *= 25;
					log.info("Removed balancing Hydroponics Lab from " + market.getName() + " (size " + size + ")");
				}
			}
			
			switch (entity.archetype)
			{
				case AGRICULTURE:
					weight *= 2.5f;
					break;
				case MIXED:
					weight *= 2f;
					break;
				default:
					weight *= 1.25f;
			}
			
			marketPicker.add(market, weight);
		}
		
		while ((foodDemand * 0.8) > foodSupply)
		{
			if (marketPicker.isEmpty())	break;	// fuck it, we give up
			
			int maxSize = 6;
			double shortfall = foodDemand - foodSupply;
			if (shortfall < HALFPOW3)
				maxSize = 4;
			else if (shortfall < HALFPOW4)
				maxSize = 5;
			//log.info("Shortfall: " + shortfall + ", max size: " + maxSize);
			
			MarketAPI market = marketPicker.pickAndRemove();
			int size = market.getSize();
			if (size > maxSize) continue;
			
			market.addCondition("exerelin_hydroponics");
			foodSupply += Exerelin_Hydroponics.HYDROPONICS_FOOD_POP_MULT * ExerelinUtilsMarket.getPopulation(size) 
					* ExerelinUtilsMarket.getCommoditySupplyMult(market, Commodities.FOOD);
			log.info("Added balancing Hydroponics Lab to " + market.getName() + " (size " + size + ")");
		}
		log.info("Final food supply/demand: " + (int)foodSupply + " / " + (int)foodDemand);
	}
	
	protected void addStartingMarketCommodities(MarketAPI market)
	{
		ExerelinUtilsCargo.addCommodityStockpile(market, "green_crew", 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "regular_crew", 0.45f, 0.55f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "veteran_crew", 0.1f, 0.2f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "marines", 0.8f, 1.0f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "supplies", 0.85f, 0.95f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "fuel", 0.85f, 0.95f);
		ExerelinUtilsCargo.addCommodityStockpile(market, "food", 0.8f, 0.9f);
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
	}
	
	protected MarketAPI addMarketToEntity(SectorEntityToken entity, EntityData data, String factionId)
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
			//newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);	// dependent on number of factions; bad idea
			//newMarket.addCondition("exerelin_recycling_plant");
			newMarket.addCondition("exerelin_recycling_plant");
			newMarket.addCondition("exerelin_hydroponics");
			if (data == homeworld) 
			{
				newMarket.addCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY);
				//newMarket.addCondition(Conditions.SHIPBREAKING_CENTER);
				newMarket.addCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION);
			}
		}
		else if (data.isCapital)
		{
			if (marketSize < 5) marketSize = 5;
			newMarket.addCondition("regional_capital");
			//newMarket.addCondition("exerelin_recycling_plant");
			newMarket.addCondition("exerelin_hydroponics");
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
		marketSetup.addMarketConditions(newMarket, data);

		if (isStation && marketSize >= 3)
		{
			newMarket.addCondition("exerelin_recycling_plant");
		}
				
		// add per-faction market conditions
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		
		newMarket.getTariff().modifyFlat("default_tariff", ExerelinConfig.baseTariff);
		if (config.freeMarket)
		{
			newMarket.addCondition("free_market");
			newMarket.getTariff().modifyMult("isFreeMarket", ExerelinConfig.freeMarketTariffMult);
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
		addStartingMarketCommodities(newMarket);
		//if (marketSize >= 4)
		//	ExerelinUtilsCargo.addCommodityStockpile(newMarket, "agent", marketSize);
		
		Global.getSector().getEconomy().addMarket(newMarket);
		entity.setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		// count some demand/supply values for market balancing
		int population = ExerelinUtilsMarket.getPopulation(marketSize);
		
		domesticGoodsDemand += ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.DOMESTIC_GOODS);
		//domesticGoodsSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.DOMESTIC_GOODS);
		//metalDemand += ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.METALS);
		//metalSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.METALS);
		//suppliesDemand += ExerelinUtilsMarket.getCommodityDemand(newMarket, Commodities.SUPPLIES);
		//suppliesSupply += ExerelinUtilsMarket.getCommoditySupply(newMarket, Commodities.SUPPLIES);

		int autofacCount = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.AUTOFAC_HEAVY_INDUSTRY);
		int shipbreakingCount = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.SHIPBREAKING_CENTER);
		int recyclingCount = ExerelinUtilsMarket.countMarketConditions(newMarket, "exerelin_recycling_plant");
		
		float dgSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.LIGHT_INDUSTRIAL_COMPLEX) * ConditionData.LIGHT_INDUSTRY_DOMESTIC_GOODS_MULT;
		dgSupply += ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.COTTAGE_INDUSTRY) * ConditionData.COTTAGE_INDUSTRY_DOMESTIC_GOODS_MULT;
		dgSupply *= ExerelinUtilsMarket.getPopulation(marketSize) * ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.DOMESTIC_GOODS);
		domesticGoodsSupply += dgSupply;
		
		float mSupply = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORE_REFINING_COMPLEX) * ConditionData.ORE_REFINING_METAL_PER_ORE * ConditionData.ORE_REFINING_ORE;
		mSupply += shipbreakingCount * ConditionData.SHIPBREAKING_METALS;
		mSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_METALS;
		mSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.METALS);
		metalSupply += mSupply;
		float mDemand = autofacCount * ConditionData.AUTOFAC_HEAVY_METALS;
		metalDemand += mDemand;
		
		float sSupply = autofacCount * ConditionData.AUTOFAC_HEAVY_SUPPLIES; 
		sSupply += shipbreakingCount * ConditionData.SHIPBREAKING_SUPPLIES;
		sSupply += recyclingCount * Exerelin_RecyclingPlant.RECYCLING_SUPPLIES;
		sSupply *= ExerelinUtilsMarket.getCommoditySupplyMult(newMarket, Commodities.SUPPLIES);
		suppliesSupply += sSupply;
		float sDemand = ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.SPACEPORT) * ConditionData.SPACEPORT_SUPPLIES * 0.6f;
		sDemand += ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.ORBITAL_STATION) * ConditionData.ORBITAL_STATION_SUPPLIES * 0.6f;
		sDemand += ExerelinUtilsMarket.countMarketConditions(newMarket, Conditions.MILITARY_BASE) * ConditionData.MILITARY_BASE_SUPPLIES;
		suppliesDemand += sDemand;
		
		foodSupply += getMarketBaseFoodSupply(newMarket);
		foodDemand += ConditionData.POPULATION_FOOD_MULT * ExerelinUtilsMarket.getPopulation(marketSize);
		
		//log.info("Cumulative domestic goods supply/demand thus far: " + (int)domesticGoodsSupply + " / " + (int)domesticGoodsDemand);
		
		
		data.market = newMarket;
		return newMarket;
	}
		
	protected void addOmnifactory()
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
		String image = (String) ExerelinUtils.getRandomArrayElement(images);
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
		omnifac.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(toOrbit, orbitDistance));
		omnifac.setInteractionImage("illustrations", "abandoned_station");
		omnifac.setCustomDescriptionId("omnifactory");

		MarketAPI market = Global.getFactory().createMarket("omnifactory_market", "Omnifactory", 0);
		SharedData.getData().getMarketsWithoutPatrolSpawn().add("omnifactory_market");
		SharedData.getData().getMarketsWithoutTradeFleetSpawn().add("omnifactory_market");
		market.setPrimaryEntity(omnifac);
		market.setFactionId("neutral");
		market.addCondition(Conditions.ABANDONED_STATION);
		omnifac.setMarket(market);
		Global.getSector().getEconomy().addMarket(market);
		
		omnifac.setFaction("player");
		
		OmniFac.initOmnifactory(omnifac);
	}
	
	protected void addPrismMarket()
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
		market.addCondition(Conditions.POPULATION_4);
		market.addCondition(Conditions.SPACEPORT);
		market.addCondition("exerelin_recycling_plant");
		market.addCondition("exerelin_recycling_plant");
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
		
		addStartingMarketCommodities(market);
		
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
	
	protected void addAvestaStation(StarSystemAPI system)
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
			LocationAPI hyperspace = Global.getSector().getHyperspace();
			SectorEntityToken toOrbit = system.getHyperspaceAnchor();
			avestaEntity = hyperspace.addCustomEntity("exipirated_avesta", "Avesta Station", "exipirated_avesta_station", "exipirated");
			//avestaEntity.setCircularOrbitWithSpin(toOrbit, MathUtils.getRandomNumberInRange(0, 360), 5000, 60, 30, 30);
			float ellipseMult = MathUtils.getRandomNumberInRange(1.4f, 1.8f);
			float orbitDist = 4500;
			float majorAxis = orbitDist * ellipseMult;
			float minorAxis = orbitDist * (2 - ellipseMult);
			float period = getOrbitalPeriod(500, orbitDist, 2);
			avestaEntity.setOrbit(new EllipticalOrbit(toOrbit, MathUtils.getRandomNumberInRange(0, 360), 
					majorAxis, minorAxis, MathUtils.getRandomNumberInRange(0, 360), period));
		}
		
		/*
		EntityData data = new EntityData(null);
		data.name = "Prism Freeport";
		data.type = EntityType.STATION;
		data.forceMarketSize = 4;
		
		MarketAPI market = addMarketToEntity(avestaEntity, data, "independent");
		*/

		MarketAPI market = Global.getFactory().createMarket("exipirated_avesta" + "_market", "Avesta Station", 4);
		market.setFactionId("exipirated");
		market.addCondition(Conditions.POPULATION_4);
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
		
		addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("default_tariff", 0.2f);
		market.getTariff().modifyMult("isFreeMarket", 0.5f);
		market.setPrimaryEntity(avestaEntity);
		avestaEntity.setMarket(market);
		avestaEntity.setFaction("exipirated");
		Global.getSector().getEconomy().addMarket(market);
	}
	
	/*
	protected void addShanghai(SectorEntityToken toOrbit)
	{	
		float radius = toOrbit.getRadius();
		float orbitDistance = radius + 150;
		SectorEntityToken shanghaiEntity = toOrbit.getContainingLocation().addCustomEntity("tiandong_shanghai", "Shanghai", "tiandong_shanghai", "tiandong");
		shanghaiEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, getOrbitalPeriod(toOrbit, orbitDistance));
		
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
		Global.getSector().getEconomy().addMarket(market);
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
			suppliesDemand += ConditionData.ORBITAL_STATION_SUPPLIES * 0.6f;
		}
		market.addSubmarket("tiandong_retrofit");
		toOrbit.addTag("shanghai");
		shanghaiEntity.addTag("shanghai");
		shanghaiEntity.setCustomDescriptionId("tiandong_shanghai");
	}
	
	// TODO: update when new SS+ comes out
	@Deprecated
	protected void generateSSPSector(SectorAPI sector)
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
		
		log.info("Resetting vars");
		resetVars();
		
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
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
			int numSystems = setupData.numSystems;
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
			for(int i = 0; i < numSystems; i ++)
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
			else VanillaSystemsGenerator.generate();
		}
		
		// use vanilla hyperspace map
		SectorEntityToken deep_hyperspace = Misc.addNebulaFromPNG("data/campaign/terrain/hyperspace_map.png",
		//SectorEntityToken deep_hyperspace = Misc.addNebulaFromPNG("data/campaign/terrain/hyperspace_map_filled.png",
			  0, 0, // center of nebula
			  Global.getSector().getHyperspace(), // location to add to
			  "terrain", "deep_hyperspace", // "nebula_blue", // texture to use, uses xxx_map for map
			  4, 4, Terrain.HYPERSPACE); // number of cells in texture
		
		//for (int i=0; i<OmniFacSettings.) // TODO: use Omnifactory's numberOfFactories setting when it's supported
		addOmnifactory();
		addPrismMarket();
		
		final String selectedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);
		
		log.info("Adding scripts and plugins");
		sector.addScript(new CoreScript());
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
		
		DiplomacyManager.setRandomFactionRelationships(setupData.randomStartRelationships);
		if (!corvusMode) 
		{
			SectorManager.reinitLiveFactions();
			DiplomacyManager.initFactionRelationships(false);
		}
		
		SectorManager.setSystemToRelayMap(systemToRelay);
		SectorManager.setPlanetToRelayMap(planetToRelay);
		SectorManager.setCorvusMode(corvusMode);
		SectorManager.setHardMode(setupData.hardMode);
		SectorManager.setHomeworld(homeworld.entity);
		
		// some cleanup
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : markets) {
			if (market.getFactionId().equals("templars"))
			{
				market.removeSubmarket(Submarkets.GENERIC_MILITARY); // auto added by military base; remove it
			}
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
	
	protected SectorEntityToken makeStation(EntityData data, String factionId)
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

		float orbitDays = getOrbitalPeriod(planet, orbitRadius + planet.getRadius());

		String name = planet.getName() + " " + data.name;
		String id = name.replace(' ','_');
		String[] images = stationImages.get("default");
		if (stationImages.containsKey(factionId))
			images = stationImages.get(factionId);
		
		String image = (String) ExerelinUtils.getRandomArrayElement(images);
		
		SectorEntityToken newStation = data.starSystem.addCustomEntity(id, name, image, factionId);
		newStation.setCircularOrbitPointingDown(planet, angle, orbitRadius, orbitDays);
		
		MarketAPI existingMarket = planet.getMarket();
		if (existingMarket != null)
		{
			if (!existingMarket.hasCondition(Conditions.SPACEPORT))
			{
				existingMarket.addCondition("orbital_station");
				suppliesDemand += ConditionData.ORBITAL_STATION_SUPPLIES * 0.6f;
			}
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
		
		data.entity = newStation;		
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
			
			if (alignedFactionId.equals("exipirated") && ExerelinConfig.enableAvesta)
				addAvestaStation(homeworld.starSystem);
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
				habitable.isHQ = true;
				if (factionId.equals("exipirated") && ExerelinConfig.enableAvesta)
					addAvestaStation(habitable.starSystem);
			}
			addMarketToEntity(habitable.entity, habitable, factionId);
			if (!hqsSpawned) 
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
		haveMarkets.addAll(stations);
		balanceDomesticGoods(haveMarkets);
		balanceSuppliesAndMetal(haveMarkets);
		balanceFood(haveMarkets);
	}

	public PlanetAPI createStarToken(int index, String systemId, StarSystemAPI system, String type, float size, boolean isSecondStar)
	{
		if (!isSecondStar) 
		{
			Integer[] pos = (Integer[])starPositions.get(index);
			int x = pos[0];
			int y = pos[1];
			return system.initStar(systemId, type, size, x, y, 500f);
		}
		else 
		{
			size = Math.min(size * 0.75f, system.getStar().getRadius()*0.8f);
			
			//int systemNameIndex = MathUtils.getRandomNumberInRange(0, possibleSystemNamesList.size() - 1);
			String name = system.getBaseName() + " B";	//possibleSystemNamesList.get(systemNameIndex);
			//possibleSystemNamesList.remove(systemNameIndex);
			
			PlanetAPI star = system.getStar();
			
			float angle = MathUtils.getRandomNumberInRange(1, 360);
			float distance = (BINARY_STAR_DISTANCE + star.getRadius()*5 + size*5) * MathUtils.getRandomNumberInRange(0.9f, 1.2f) ;
			float orbitDays = getOrbitalPeriod(star, distance + star.getRadius());
			
			PlanetAPI planet = system.addPlanet(systemId, star, name, type, angle, size, distance, orbitDays);
			addMagneticField(planet);
			
			return planet;
		}
	}
	
	protected PlanetAPI makeStar(int systemIndex, StarSystemAPI system, boolean isSecondStar)
	{
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
			star = createStarToken(systemIndex, systemId, system, "star_blue", 400f, isSecondStar);
			system.setLightColor(new Color(135,206,250));
		}
		else if(starType == 3)
		{
			star = createStarToken(systemIndex, systemId, system, "star_white", 300f, isSecondStar);
			//system.setLightColor(new Color(185,185,240));
		}
		else if(starType == 4)
		{
			star = createStarToken(systemIndex, systemId, system, "star_orange", 900f, isSecondStar);
			system.setLightColor(new Color(255,220,0));
		}
		else if(starType == 5)
		{
			star = createStarToken(systemIndex, systemId, system, "star_yellowwhite", 400f, isSecondStar);
			system.setLightColor(new Color(255,255,224));
		}
		else if(starType == 6)
		{
			star = createStarToken(systemIndex, systemId, system, "star_bluewhite", 400f, isSecondStar);
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
		
	protected void buildSystem(SectorAPI sector, int systemIndex)
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
		
		EntityData starData = new EntityData(system);
		EntityData starData2 = null;
		starData.entity = star;
		starData.type = EntityType.STAR;
		entities.add(starData);
		
		boolean isBinary = (Math.random() < ExerelinConfig.binarySystemChance) && (!star.getTypeId().equals("star_dark"));
		if (isBinary)
		{
			star2 = makeStar(systemIndex, system, true);
			starData2 = new EntityData(system);
			starData2.entity = star2;
			starData2.type = EntityType.STAR;
			entities.add(starData2);
		}
		
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
		
		int distanceStepping = (ExerelinSetupData.getInstance().maxSystemSize-4000)/MathUtils.getRandomNumberInRange(numBasePlanets, maxPlanets+1);
		
		if (isBinary) numBasePlanets *= BINARY_SYSTEM_PLANET_MULT;
		
		boolean gasPlanetCreated = false;
		int habitableCount = 0;
		List<EntityData> uninhabitables1To4 = new ArrayList<>();
		
		for(int i = 0; i < numBasePlanets; i = i + 1)
		{
			float habitableChance = getHabitableChance(i, false);
			
			boolean habitable = Math.random() <= habitableChance;
			EntityData entityData = new EntityData(system, i+1);
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
				planetData.archetype = pickMarketArchetype(false);
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
			float distance = 3000 + (distanceStepping * (planetData.planetNumByStar - 1) * MathUtils.getRandomNumberInRange(0.75f, 1.25f));
			distance = (int)distance;
			float orbitDays = getOrbitalPeriod(toOrbit, distance + toOrbit.getRadius());
			
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
				for(int j = 0; j < numMoons; j++)
				{
					String ext = "";
					if(j == 0)
						ext = "I";
					if(j == 1)
						ext = "II";
					if(j == 2)
						ext = "III";
					
					
					EntityData moonData = new EntityData(system);
					boolean moonInhabitable = Math.random() < getHabitableChance(planetData.planetNum, true);
					if (moonInhabitable)
					{
						moonData.archetype = pickMarketArchetype(false);
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
					PlanetAPI newMoon = system.addPlanet(name + " " + ext, newPlanet, name + " " + ext, moonData.planetType, angle, moonRadius, distance, orbitDays);
					log.info("Creating moon " + name + " " + ext);
					moonData.entity = newMoon;
					
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
			// add magnetic field
			if (isGasGiant)
			{
				addMagneticField(newPlanet);
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
			if (planetData.primary == starData2) 
			{
				if (systemIndex == 0) weight *= 0;
				else weight *= 0.67f;
			}
			capitalPicker.add(planetData, weight);
		}
		capital = capitalPicker.pick();
		capital.isCapital = true;
		if (systemIndex == 0)
		{
			homeworld = capital;
			homeworld.isHQ = true;
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
				orbitRadius = MathUtils.getRandomNumberInRange(1000, 8000) + planet.getRadius();
				numAsteroids = 100;
			}
			else
			{
				orbitRadius = MathUtils.getRandomNumberInRange(400, 550);
				numAsteroids = 15;
			}
			numAsteroids = (int)(numAsteroids * MathUtils.getRandomNumberInRange(0.75f, 1.25f));

			float width = MathUtils.getRandomNumberInRange(10, 50);
			float baseOrbitDays = getOrbitalPeriod(planet, orbitRadius);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			system.addAsteroidBelt(planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
			if (planet == star) starBelts1.add(orbitRadius);
			else if (planet == star2) starBelts2.add(orbitRadius);
			log.info("Added asteroid belt around " + planet.getName());
		}

		// Always put an asteroid belt around the sun
		do {
			float distance = MathUtils.getRandomNumberInRange(1000, 8000) + star.getRadius();
			float baseOrbitDays = getOrbitalPeriod(star, distance);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			
			system.addAsteroidBelt(star, 25, distance, MathUtils.getRandomNumberInRange(40, 60), minOrbitDays, maxOrbitDays);
			starBelts1.add(distance);
			
			// Another one if medium system size
			if(ExerelinSetupData.getInstance().maxSystemSize > 16000)
			{
				distance = MathUtils.getRandomNumberInRange(12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star, distance);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				system.addAsteroidBelt(star, 50, distance, MathUtils.getRandomNumberInRange(75, 125), minOrbitDays, maxOrbitDays);
				starBelts1.add(distance);
			}
			// And another one if a large system
			if(ExerelinSetupData.getInstance().maxSystemSize > 32000)
			{
				distance = MathUtils.getRandomNumberInRange(12000, 25000);
				baseOrbitDays = getOrbitalPeriod(star, distance);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				system.addAsteroidBelt(star, 75, distance, MathUtils.getRandomNumberInRange(100, 150),  minOrbitDays, maxOrbitDays);
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
		int numStations;
		int maxStations = ExerelinSetupData.getInstance().maxStations;
		if(ExerelinSetupData.getInstance().numSystems != 1)
		{
			int minStations = ExerelinConfig.minimumPlanets;
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

			EntityData stationData = new EntityData(system);
			stationData.primary = primaryData;
			stationData.type = EntityType.STATION;
			stationData.archetype = pickMarketArchetype(true);
			if (primaryData.entity == star) stationData.orbitDistance = (Float) ExerelinUtils.getRandomListElement(starBelts1);
			else if (primaryData.entity == star2) 
			{
				// make a belt for binary companion if we don't have one already
				if (starBelts2.isEmpty())
				{
					float distance = MathUtils.getRandomNumberInRange(1000, 8000) + star2.getRadius();
					float baseOrbitDays = getOrbitalPeriod(star2, distance);
					float minOrbitDays = baseOrbitDays * 0.75f;
					float maxOrbitDays = baseOrbitDays * 1.25f;

					system.addAsteroidBelt(star2, 25, distance, MathUtils.getRandomNumberInRange(30, 50), minOrbitDays, maxOrbitDays);
					starBelts2.add(distance);
				}
				
				stationData.orbitDistance = (Float) ExerelinUtils.getRandomListElement(starBelts2);
			}
			
			// name our station
			boolean nameOK = false;
			String name = "";
			while(!nameOK)
			{
				name = (String) ExerelinUtils.getRandomListElement(possibleStationNamesList);
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
			float orbitDays = getOrbitalPeriod(capitalToken, orbitDistance);
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
		float distance = star.getRadius() + MathUtils.getRandomNumberInRange(1200, 1800);
		relay.setCircularOrbit(star, (float)Math.random() * 360, distance, getOrbitalPeriod(star, distance));
		systemToRelay.put(system.getId(), system.getId() + "_relay");
		planetToRelay.put(capital.entity.getId(), system.getId() + "_relay");
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
	
	public static float getMarketBaseFoodSupply(MarketAPI market)
	{
		float pop = ExerelinUtilsMarket.getPopulation(market.getSize());
		float food = 0;
		
		// planet food
		if (market.hasCondition(Conditions.TERRAN))
			food += ConditionData.WORLD_TERRAN_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.ARID))
			food += ConditionData.WORLD_ARID_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.WATER))
		{
			float thisFood = ConditionData.WORLD_WATER_FARMING_MULT * pop;
			if (thisFood > ConditionData.WORLD_WATER_MAX_FOOD)
				thisFood = ConditionData.WORLD_WATER_MAX_FOOD;
			food += thisFood;
		}
		else if (market.hasCondition(Conditions.DESERT))
			food += ConditionData.WORLD_DESERT_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.JUNGLE))
			food += ConditionData.WORLD_JUNGLE_FARMING_MULT * pop;
		else if (market.hasCondition(Conditions.ICE))
			food += ConditionData.WORLD_ICE_FARMING_MULT * pop;
		else if (market.hasCondition("barren_marginal"))
			food += ConditionData.WORLD_BARREN_MARGINAL_FARMING_MULT * pop;
		else if (market.hasCondition("twilight"))
			food += ConditionData.WORLD_TWILIGHT_FARMING_MULT * pop;
		else if (market.hasCondition("tundra"))
			food += ConditionData.WORLD_TUNDRA_FARMING_MULT * pop;
		
		// market conditions
		int hydroponicsCount = ExerelinUtilsMarket.countMarketConditions(market, "exerelin_hydroponics");
		int hydroponicsVanillaCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.HYDROPONICS_COMPLEX);
		int aquacultureCount = ExerelinUtilsMarket.countMarketConditions(market, Conditions.AQUACULTURE);
		food += hydroponicsCount * Exerelin_Hydroponics.HYDROPONICS_FOOD_POP_MULT * pop;
		food += aquacultureCount * ConditionData.AQUACULTURE_FOOD_MULT * pop;
		food += hydroponicsVanillaCount * ConditionData.HYDROPONICS_COMPLEX_FOOD;
		
		food *= market.getCommodityData(Commodities.FOOD).getSupply().computeMultMod();
		
		return food;
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
		float orbitDistance = 0;	// only used for belter stations
		int marketPoints = 0;
		int bonusMarketPoints = 0;
		
		public EntityData(StarSystemAPI starSystem) 
		{
			this.starSystem = starSystem;
		}	  
		public EntityData(StarSystemAPI starSystem, int planetNum) 
		{
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

