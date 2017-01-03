package exerelin.world;

import java.awt.Color;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.apache.log4j.Logger;
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
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.ExerelinModPlugin;
import data.scripts.campaign.AL_ChaosCrackFleetManager;
import data.scripts.campaign.ExigencyCommRelayAdder;
import data.scripts.world.exipirated.ExipiratedAvestaFleetManager;
import data.scripts.world.exipirated.ExipiratedAvestaMovement;
import data.scripts.world.exipirated.ExipiratedCollectorFleetManager;
import data.scripts.world.exipirated.ExipiratedPatrolFleetManager;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.plugins.*;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinCoreScript;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.missions.ConquestMissionCreator;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinMarketSetup.MarketArchetype;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

@SuppressWarnings("unchecked")

public class ExerelinSectorGen implements SectorGeneratorPlugin
{
	// NOTE: system names and planet names are overriden by planetNames.json
	protected static final String PLANET_NAMES_FILE = "data/config/exerelin/planetNames.json";
	// don't specify names here to make sure it crashes instead of failing silently if planetNames.json is broken
	protected static final String[] starBackgroundsArray = new String[]
	{
		"backgrounds/background1.jpg", "backgrounds/background2.jpg", "backgrounds/background3.jpg", "backgrounds/background4.jpg", "backgrounds/background5.jpg", "backgrounds/background6.jpg",
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

	protected List<String> possibleSystemNames = new ArrayList<>();
	protected List<String> possiblePlanetNames = new ArrayList<>();
	protected List<String> possibleStationNames = new ArrayList<>();
	
	//protected static final String[] planetTypes = new String[] {"desert", "jungle", "frozen", "terran", "arid", "water", "rocky_metallic", "rocky_ice", "barren", "barren-bombarded"};
	protected static final String[] planetTypesUninhabitable = new String[] 
		{"desert", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "frozen", "rocky_ice", "irradiated", "barren-bombarded", "barren-desert", "terran-eccentric"};
	protected static final String[] planetTypesGasGiant = new String[] {"gas_giant", "ice_giant"};
	//protected static final String[] moonTypes = new String[] {"frozen", "barren", "barren-bombarded", "rocky_ice", "rocky_metallic", "desert", "water", "jungle"};
	protected static final String[] moonTypesUninhabitable = new String[] 
		{"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice", "irradiated", "barren-bombarded", "desert", "water", "jungle", "barren-desert"};
	protected static final List<StarDef> starDefs = new ArrayList<>();
	protected static final WeightedRandomPicker<StarDef> starPicker = new WeightedRandomPicker<>();
	
	public static final List<String> stationImages = new ArrayList<>(Arrays.asList(
			new String[] {"station_side00", "station_side02", "station_side04", "station_jangala_type"}));
	
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
	
	// this proportion of TT markets with no military bases will have Cabal submarkets (SS+)
	public static final float CABAL_MARKET_MULT = 0.4f;	
	// this is the chance a market with a military base will still be a candidate for Cabal markets
	public static final float CABAL_MILITARY_MARKET_CHANCE = 0.5f;
	
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
	
	protected void loadStars(){
		starDefs.clear();
		starDefs.add(new StarDef("Yellow Star", "star_yellow", 500, 1));
		starDefs.add(new StarDef("Orange Star", "star_orange", 400, new Color(255, 235, 205), 1));
		starDefs.add(new StarDef("Yellow-White Star", "star_yellowwhite", 600, new Color(255,255,224), 1));
		starDefs.add(new StarDef("White Dwarf", "star_white", 200, new Color(168, 168, 168), 1));
		starDefs.add(new StarDef("White Star (large)", "star_white", 700, 1));
		starDefs.add(new StarDef("Red Giant", "star_red", 900, new Color(255, 210, 200), 1));
		starDefs.add(new StarDef("Blue Star", "star_blue", 800, new Color(200, 240, 255), 1));
		starDefs.add(new StarDef("Blue-White Star", "star_bluewhite", 650, new Color(210,236,255), 1));
		
		if (ExerelinUtilsFaction.doesFactionExist("tiandong")) {
			starDefs.add(new StarDef("Red Dwarf", "tiandong_shaanxi", 250, new Color(200, 125, 125), 2));
		}
		else if (ExerelinUtilsFaction.doesFactionExist("mayorate")) {
			starDefs.add(new StarDef("Red Dwarf", "star_red_dwarf", 250, new Color(200, 125, 125), 2));
		}
		
		if (!ExerelinConfig.realisticStars) {
			starDefs.add(new StarDef("Purple Star", "star_purple", 700, new Color(250, 192, 244), 1));
			starDefs.add(new StarDef("Dark Star", "star_dark", 100, new Color(155, 155, 155), 1));
			starDefs.add(new StarDef("Green Star", "star_green", 500, new Color(225, 255, 230), 1));
			starDefs.add(new StarDef("Green-White Star", "star_greenwhite", 600, new Color(240, 255, 245), 1));
			if (ExerelinUtilsFaction.doesFactionExist("exigency")) {
				starDefs.add(new StarDef("Black Hole", "exigency_black_hole", 320, 0.5f));
			}
		}
		
		starPicker.clear();
		for (StarDef def : starDefs) {
			starPicker.add(def, def.chance);
		}
	}
	
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
		if (ExerelinUtils.isSSPInstalled(true))
		{
			starBackgrounds.add("ssp/backgrounds/ssp_arcade.png");
			starBackgrounds.add("ssp/backgrounds/ssp_atopthemountain.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_conflictofinterest.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_corporateindirection.jpg");
			starBackgrounds.add("ssp/backgrounds/ssp_overreachingexpansion.jpg");
		}
		if (ExerelinModPlugin.HAVE_SWP)
		{
			starBackgrounds.add("swp/backgrounds/swp_arcade.png");
			starBackgrounds.add("swp/backgrounds/swp_atopthemountain.jpg");
			starBackgrounds.add("swp/backgrounds/swp_conflictofinterest.jpg");
			starBackgrounds.add("swp/backgrounds/swp_corporateindirection.jpg");
			starBackgrounds.add("swp/backgrounds/swp_overreachingexpansion.jpg");
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
		nebulaMaps.add("nexerelin/gemstone_nebula.png");
		
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
	
	protected void addPrismMarket(SectorAPI sector)
	{
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
			prismEntity.setCircularOrbitPointingDown(toOrbit, MathUtils.getRandomNumberInRange(1, 360), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		}
		else
		{
			LocationAPI hyperspace = sector.getHyperspace();
			prismEntity = hyperspace.addCustomEntity("prismFreeport", "Prism Freeport", "exerelin_freeport_type", "independent");
			float xpos = 2000;
			if (!ExerelinSetupData.getInstance().corvusMode) xpos = -2000;
			prismEntity.setCircularOrbitWithSpin(hyperspace.createToken(xpos, 0), ExerelinUtilsAstro.getRandomAngle(), 150, 60, 30, 30);
		}
		
		prismEntity.addTag(ExerelinConstants.TAG_UNINVADABLE);
		
		/*
		EntityData data = new EntityData(null);
		data.name = "Prism Freeport";
		data.type = EntityType.STATION;
		data.forceMarketSize = 4;
		
		MarketAPI market = addMarketToEntity(prismEntity, data, "independent");
		*/

		MarketAPI market = Global.getFactory().createMarket("prismFreeport" /*+ "_market"*/, "Prism Freeport", 5);
		market.setFactionId(Factions.INDEPENDENT);
		market.addCondition(Conditions.POPULATION_5);
		market.addCondition(Conditions.SPACEPORT);
		market.addCondition("exerelin_recycling_plant");
		//market.addCondition("exerelin_recycling_plant");
		market.addCondition("exerelin_supply_workshop");
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
		prismEntity.setFaction(Factions.INDEPENDENT);
		sector.getEconomy().addMarket(market);
		
		//pickEntityInteractionImage(prismEntity, market, "", EntityType.STATION);
		//prismEntity.setInteractionImage("illustrations", "space_bar");
		prismEntity.setCustomDescriptionId("exerelin_prismFreeport");
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
			Global.getSector().getPersistentData().put("exipirated_movementScript", avestaMovementScript);
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
		
		market.getTariff().modifyFlat("default_tariff", 0.2f);
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
		  new MagneticFieldParams(chaosCrack.getRadius() + 1000f, // terrain effect band width 
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
		  new MagneticFieldParams(chaosCrack.getRadius() + 1400f, // terrain effect band width 
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
                4, 4, "AL_primenebula");
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
	
	@Override
	public void generate(SectorAPI sector)
	{
		log.info("Starting sector generation...");
		// load planet/star names from config
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
		
		log.info("Loading stars and backgrounds");
		loadStars();
		loadBackgrounds();
		loadNebulaMaps();
		
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
			picker.addAll(StarLocations.SPOT);
			if (StarLocations.SPOT.size() < numSystems + numSystemsEmpty)
				picker.addAll(StarLocations.SPOT_EXTENDED);
			
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
			hyperMap = "data/campaign/terrain/nexerelin/hyperspace_map_rot.png";
		}
		SectorEntityToken deep_hyperspace = Misc.addNebulaFromPNG(hyperMap,
			  0, 0, // center of nebula
			  sector.getHyperspace(), // location to add to
			  "terrain", "deep_hyperspace", // "nebula_blue", // texture to use, uses xxx_map for map
			  4, 4, Terrain.HYPERSPACE); // number of cells in texture
		
		if (ExerelinSetupData.getInstance().prismMarketPresent) {
			if (!corvusMode || !ExerelinUtilsFaction.doesFactionExist("SCY"))
				addPrismMarket(sector);
		}
		
		// add Cabal submarkets
		if (ExerelinUtils.isSSPInstalled(true) || ExerelinModPlugin.HAVE_UNDERWORLD)
		{
			List<MarketAPI> cabalCandidates = new ArrayList<>();
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
			{
				if (!market.getFactionId().equals(Factions.TRITACHYON)) continue;
				if (market.hasCondition(Conditions.MILITARY_BASE) && Math.random() > CABAL_MILITARY_MARKET_CHANCE) 
					continue;
				
				//log.info("Cabal candidate added: " + market.getName() + " (size " + market.getSize() + ")");
				cabalCandidates.add(market);
			}
			
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
					log.info("Added Cabal submarket to " + market.getName() + " (size " + market.getSize() + ")");
				}
			} catch (RuntimeException rex) {
				// old SS+ version, do nothing
			}
		}
		final String selectedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);
		
		log.info("Adding scripts and plugins");
		sector.addScript(new ExerelinCoreScript());
		sector.registerPlugin(new ExerelinCoreCampaignPlugin());
		
		if (!ExerelinUtils.isSSPInstalled(false))
		{
			sector.addScript(new CoreEventProbabilityManager());
		}
		sector.addScript(new EconomyFleetManager());
		sector.addScript(new MercFleetManager());
		sector.addScript(new LuddicPathFleetManager());
		sector.addScript(new PirateFleetManager());
		sector.addScript(new BountyPirateFleetManager());
		sector.addScript(new MarketProcurementMissionCreator());
		sector.addScript(new FactionCommissionMissionCreator());
		sector.addScript(new ConquestMissionCreator());
		
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
	
	
	
	/**
	 *	Given a list of EntityDatas representing planets, and min/max orbit radius, 
	 *  find an orbit that avoids overlapping with that of any planet
	 */
	public static float getRandomOrbitRadiusBetweenPlanets(List<ExerelinSectorGen.EntityData> planets, float minDist, float maxDist)
	{		
		float orbitRadius = 0;
		List<ExerelinSectorGen.EntityData> validPlanets = new ArrayList<>();
		for (ExerelinSectorGen.EntityData data : planets)
		{
			//log.info("Random orbit radius finder trying entity " + data.name);
			if (data.type != ExerelinSectorGen.EntityType.PLANET) continue;
			if (data.orbitRadius < minDist) continue;
			if (data.orbitRadius > maxDist) continue;
			validPlanets.add(data);
		}
		if (validPlanets.isEmpty())
		{
			log.info("Couldn't find valid planets for random orbit radius finder, just getting a random one");
			return (MathUtils.getRandomNumberInRange(minDist, maxDist) + MathUtils.getRandomNumberInRange(minDist, maxDist))/2;
		}
		
		int index = 0;
		if (validPlanets.size() > 1) index = MathUtils.getRandomNumberInRange(0, validPlanets.size() - 1);
		float min = minDist;
		float max = maxDist;
		ExerelinSectorGen.EntityData planet = validPlanets.get(index);
		if (index == 0)
		{
			if (validPlanets.size() > 1)
			{
				ExerelinSectorGen.EntityData nextPlanet = validPlanets.get(1);
				max = nextPlanet.orbitRadius - nextPlanet.clearRadius;
			}
			min = Math.max(minDist, planet.primary.clearRadius);
		}
		else if (index == validPlanets.size() - 1)
		{
			min = planet.orbitRadius + planet.clearRadius;
		}
		else
		{
			ExerelinSectorGen.EntityData prevPlanet = validPlanets.get(index - 1);
			min = prevPlanet.orbitRadius + prevPlanet.clearRadius;
			max = planet.orbitRadius - planet.clearRadius;
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
	
	// end utility functions
	// =========================================================================
	
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

	public PlanetAPI createStarToken(int index, String systemId, StarSystemAPI system, StarDef def, boolean isSecondStar)
	{
		float coronaMult = 1;
		float radius = def.radius;
		String type = def.type;
		if (def.name.equalsIgnoreCase("Red Giant")) coronaMult = 2;
		else if (type.equals("exigency_black_hole")) coronaMult = 0;
		else if (def.name.contains("Dwarf")) coronaMult = 0.5f;
		
		radius *= MathUtils.getRandomNumberInRange(1 - STAR_SIZE_VARIATION, 1 + STAR_SIZE_VARIATION);
		PlanetAPI created = null;
		
		if (!isSecondStar) 
		{
			Integer[] pos = starPositions.get(index);
			int x = pos[0];
			int y = pos[1];
			created = system.initStar(systemId + "_star", type, radius, x, y, 500 * coronaMult);
			if (type.equals("exigency_black_hole"))
			{
				created.setCustomDescriptionId("exigency_black_hole");
				for (CampaignTerrainAPI terrain : system.getTerrainCopy())
				{
					if (terrain.getType().contentEquals("corona"))
					{
						system.removeEntity(terrain);
						break;
					}
				}
			}
			else if (type.equals("star_red_dwarf"))	// Mayorate red dwarf
				created.setCustomDescriptionId("star_red_dwarf");
			else if (def.name.equalsIgnoreCase("White Dwarf"))
				created.setCustomDescriptionId("star_white_dwarf");
		}
		else 
		{
			radius = Math.min(radius * 0.75f, system.getStar().getRadius()*0.8f);
			
			//int systemNameIndex = MathUtils.getRandomNumberInRange(0, possibleSystemNames.size() - 1);
			String name = system.getBaseName() + " B";	//possibleSystemNamesList.get(systemNameIndex);
			//possibleSystemNamesList.remove(systemNameIndex);
			
			PlanetAPI primary = system.getStar();
			
			float angle = MathUtils.getRandomNumberInRange(1, 360);
			float distance = (BINARY_STAR_DISTANCE + primary.getRadius()*5 + radius*5) * MathUtils.getRandomNumberInRange(0.95f, 1.1f) ;
			float orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(primary, distance + primary.getRadius());
			
			created = system.addPlanet(systemId + "_star_b", primary, name, type, angle, radius, distance, orbitDays);
			ExerelinUtilsAstro.setOrbit(created, primary, distance, true, ExerelinUtilsAstro.getRandomAngle(), orbitDays);
			if (coronaMult != 0)
				system.addCorona(created, 300 * coronaMult, 2f, 0.1f, 1f);
		}
		if (type.equals("exigency_black_hole"))
		{
			created.setCustomDescriptionId("exigency_black_hole");
			system.addTerrain(Terrain.MAGNETIC_FIELD,
				new MagneticFieldTerrainPlugin.MagneticFieldParams(920f, // terrain effect band width
				radius*1.5f, // terrain effect middle radius
				created, // entity that it's around
				0f, // visual band start
				radius*3, // visual band end
				new Color(0, 0, 0, 0), // base color
				0f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
				new Color(0, 0, 0, 0),
				new Color(0, 0, 0, 0)));
			created.setInteractionImage("illustrations", "tasserus_illustration");
			system.addRingBand(created, "misc", "accretion", 256f, 1, new Color(255, 155, 130, 255), 256, 250, -34f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(255, 115, 110, 255), 256, 330, -15f);
			system.addRingBand(created, "misc", "accretion", 256f, 2, Color.white, 256, 400, -10f);
			system.addRingBand(created, "misc", "accretion", 256f, 2, new Color(255, 115, 110, 255), 256, 410, -24f);
			system.addRingBand(created, "misc", "accretion", 256f, 2, new Color(215, 25, 10, 255), 256, 440, -22f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(75, 15, 15, 125), 256, 480, -35f);
			system.addRingBand(created, "misc", "accretion", 256f, 3, new Color(255, 105, 105, 155), 256, 525, -40f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(155, 15, 15, 255), 256, 565, -50f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(175, 95, 35, 255), 256, 615, -50f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(125, 45, 15, 255), 256, 665, -50f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(175, 75, 15, 255), 256, 695, -50f);
			system.addRingBand(created, "misc", "accretion", 256f, 3, new Color(75, 55, 45, 215), 256, 710, -60f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(65, 39, 35, 125), 256, 730, -70f);
			system.addRingBand(created, "misc", "accretion", 256f, 1, new Color(75, 45, 35, 255), 256f, 750, -86f);
			system.addRingBand(created, "misc", "accretion", 256f, 2, new Color(85, 64, 45, 215), 256, 771, -90f);
			system.addRingBand(created, "misc", "accretion", 256f, 0, new Color(75, 55, 35, 175), 256, 791, -100f);
			system.addRingBand(created, "misc", "accretion", 256f, 1, new Color(55, 35, 15, 165), 256f, 825, -110f);
			system.addRingBand(created, "misc", "accretion", 256f, 1, new Color(185, 175, 131, 135), 256f, 855, -115f);
			system.addRingBand(created, "misc", "accretion", 256f, 3, new Color(135, 125, 91, 115), 256f, 895, -125f);
			system.addRingBand(created, "misc", "accretion", 256f, 2, new Color(45, 25, 5, 75), 256f, 920, -130f);
		}
		
		return created;
	}
	
	protected PlanetAPI makeStar(int systemIndex, StarSystemAPI system, boolean isSecondStar)
	{
		log.info("Creating star for system " + system.getBaseName());
		PlanetAPI star;
		String systemId = system.getId();
		
		StarDef def = starPicker.pick();
		star = createStarToken(systemIndex, systemId, system, def, isSecondStar);
		if (def.lightColor != null)
			system.setLightColor(def.lightColor);
		
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
		int systemNameIndex = MathUtils.getRandomNumberInRange(0, possibleSystemNames.size() - 1);
		if (systemIndex == 0) systemNameIndex = 0;	// there is always a starSystem named Exerelin
		StarSystemAPI system = sector.createStarSystem(possibleSystemNames.get(systemNameIndex));
		possibleSystemNames.remove(systemNameIndex);
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
		starData.clearRadius = star.getRadius() * 4;
		entities.add(starData);
		
		boolean isBinary = (Math.random() < ExerelinConfig.binarySystemChance) && (star.getRadius() >= MIN_RADIUS_FOR_BINARY);
		if (isBinary)
		{
			star2 = makeStar(systemIndex, system, true);
			starData2 = new EntityData(star2.getName(), system);
			starData2.entity = star2;
			starData2.type = EntityType.STAR;
			starData2.clearRadius = star2.getRadius() * 4;
			entities.add(starData2);
		}
		
		float ellipseAngle = ExerelinUtilsAstro.getRandomAngle();
		float ellipseMult = 1;	//MathUtils.getRandomNumberInRange(1.05f, 1.2f);
		
		// now let's start seeding planets
		// note that we don't create the PlanetAPI right away, but set up EntityDatas first
		// so we can check that the system has enough of the types of planets we want
		int numBasePlanets;
		int maxPlanets = ExerelinSetupData.getInstance().maxPlanets;
		if(ExerelinSetupData.getInstance().numSystems != 1)
		{
			int minPlanets = ExerelinConfig.minimumPlanets;
			if (minPlanets > maxPlanets) maxPlanets = minPlanets;
			numBasePlanets = MathUtils.getRandomNumberInRange(minPlanets, maxPlanets);
		}
		else
			numBasePlanets = maxPlanets;
		
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
			picker.addAll(uninhabitables1To4);
			for (int i=habitableCount; i < 2; i++)
			{
				picker.pickAndRemove().habitable = true;
			}	
		}
		
		List<EntityData> moons = new ArrayList<>();
				
		// okay, now we can actually create the planets
		int divMin = numBasePlanets + 1;
		int divMax = maxPlanets + 1;
		if (divMin > divMax) divMin = divMax - 1;
		final int distanceStepping = (ExerelinSetupData.getInstance().baseSystemSize + numBasePlanets * 600)/MathUtils.getRandomNumberInRange(divMin, divMax);
		float lastDistance = 0;
		float clearRadius = 0;	// try to make sure next planet's orbit is at least this far away from the previous one
		for(EntityData planetData : entities)
		{
			if (planetData.type == EntityType.STAR) continue;
			
			String planetType = "";
			boolean isGasGiant = false;
			
			String name = "";
			String id = "";
			int planetNameIndex = MathUtils.getRandomNumberInRange(0, possiblePlanetNames.size() - 1);
			name = possiblePlanetNames.get(planetNameIndex);
			possiblePlanetNames.remove(planetNameIndex);
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
			if (distance < lastDistance + clearRadius)
				distance = lastDistance + clearRadius;
			distance = (int)distance;
			lastDistance = distance;
			float orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, distance);
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
			planetData.startAngle = ExerelinUtilsAstro.setOrbit(newPlanet, toOrbit, distance, ExerelinUtilsAstro.getRandomAngle(), !isBinary, ellipseAngle, ellipseMult, orbitDays);
			planetData.entity = newPlanet;
			planetData.planetType = planetType;
			clearRadius = radius * 4;
			
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
				float ellipseAngleMoon = ExerelinUtilsAstro.getRandomAngle();
				
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
					orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(newPlanet, distance + newPlanet.getRadius());
					
					PlanetAPI newMoon = system.addPlanet(moonId, newPlanet, moonName, moonData.planetType, angle, moonRadius, distance, orbitDays);
					moonData.startAngle = angle;	// ExerelinUtilsAstro.setOrbit(newMoon, newPlanet, distance, false, 0, orbitDays);
					log.info("Creating moon " + moonName);
					moonData.entity = newMoon;
					moonData.orbitRadius = distance;
					moonData.orbitPeriod = orbitDays;
					
					// concurrency exception - don't add it direct; add to another list and merge
					//entities.add(moonData);
					moons.add(moonData);
					clearRadius = Math.max(clearRadius, distance * 1.5f);
				}
			}
			
			// 20% chance of rings around planet / 50% chance if a gas giant
			float ringChance = (planetType.equalsIgnoreCase("gas_giant") || planetType.equalsIgnoreCase("ice_giant")) ? 0.5f : 0.2f;
			if(Math.random() < ringChance)
			{
				int ringType = MathUtils.getRandomNumberInRange(0,3);
				float radiusMult = MathUtils.getRandomNumberInRange(2, 3.5f);
				int ringRadius = (int)(radius*radiusMult);

				if (ringType == 0)
				{
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, ringRadius, 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius, .9f, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, ringRadius, 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, .9f, true);
				}
				else if (ringType == 1)
				{
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, ringRadius, 1, true);
					//ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*2.5), true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, .9f, true);
				}
				else if (ringType == 2)
				{
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, ringRadius*1.25f, 1, true);
				}
				else if (ringType == 3)
				{
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, (int)(radius*radiusMult), 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, (int)(radius*radiusMult), 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, (int)(radius*radiusMult), 1, true);
					ExerelinUtilsAstro.addRingBand(system, newPlanet, "misc", "rings1", 256f, 1, Color.white, 256f, (int)(radius*radiusMult*1.25), 1.1f, true);
				}
				clearRadius = Math.max(clearRadius, ringRadius * 1.5f);
			}
			// add magnetic field
			if (isGasGiant && Math.random() < MAGNETIC_FIELD_CHANCE)
			{
				addMagneticField(newPlanet);
			}
			
			// add Lagrange asteroids
			if (isGasGiant && Math.random() < LAGRANGE_ASTEROID_CHANCE)
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
				ExerelinUtilsAstro.setLagrangeOrbit(l4asteroids, toOrbit, newPlanet, 4, planetData.startAngle, planetData.orbitRadius, 0, 
						planetData.orbitPeriod, !isBinary, ellipseAngle, ellipseMult);
				ExerelinUtilsAstro.setLagrangeOrbit(l5asteroids, toOrbit, newPlanet, 5, planetData.startAngle, planetData.orbitRadius, 0, 
						planetData.orbitPeriod, !isBinary, ellipseAngle, ellipseMult);
			}
			
			planetData.clearRadius = clearRadius;
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
				//if (systemIndex == 0) weight *= 0;
				//else weight *= 0.67f;
				weight *= 0.67;
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
			if (data.type == EntityType.MOON) continue;	//weight = 0.5f;
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
				orbitRadius = getRandomOrbitRadiusBetweenPlanets(entities, 1000 + star.getRadius(), 14000 + star.getRadius());
				numAsteroids = (int)(orbitRadius/25);
			}
			else
			{
				orbitRadius = MathUtils.getRandomNumberInRange(400, 550);
				numAsteroids = 15;
			}
			numAsteroids = (int)(numAsteroids * MathUtils.getRandomNumberInRange(0.75f, 1.25f));

			float width = MathUtils.getRandomNumberInRange(160, 200);
			float baseOrbitDays = ExerelinUtilsAstro.getOrbitalPeriod(planet, orbitRadius);
			float minOrbitDays = baseOrbitDays * 0.75f;
			float maxOrbitDays = baseOrbitDays * 1.25f;
			ExerelinUtilsAstro.addAsteroidBelt(system, planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
			if (planet == star) starBelts1.add(orbitRadius);
			else if (planet == star2) starBelts2.add(orbitRadius);
			log.info("Added asteroid belt around " + planet.getName());
		}
		
		// add nebula
		boolean hasNebula = false;
		if (Math.random() < NEBULA_CHANCE)
		{
			SectorEntityToken nebula = Misc.addNebulaFromPNG((String)ExerelinUtils.getRandomListElement(nebulaMaps),	// nebula texture
					  0, 0, // center of nebula
					  system, // location to add to
					  "terrain", "nebula_" + ExerelinUtils.getRandomArrayElement(nebulaColors), // texture to use, uses xxx_map for map
					  4, 4); // number of cells in texture
			hasNebula = true;
		}

		// Usually put an extra asteroid belt around the sun
		do {
			// system will always have asteroid belt if it doesn't have nebula, it just looks wrong otherwise
			if (Math.random() < STELLAR_BELT_CHANCE || !hasNebula) {
				float distance = getRandomOrbitRadiusBetweenPlanets(entities, 3000 + star.getRadius(), 14000 + star.getRadius());
				float baseOrbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star, distance);
				float minOrbitDays = baseOrbitDays * 0.75f;
				float maxOrbitDays = baseOrbitDays * 1.25f;

				ExerelinUtilsAstro.addAsteroidBelt(system, star, (int)(distance/25), distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
				starBelts1.add(distance);
			}
			
			// Another one if medium system size
			/*
			if(ExerelinSetupData.getInstance().baseSystemSize > 16000)
			{
				distance = getRandomOrbitRadiusBetweenPlanets(entities, 12000, 25000);
				baseOrbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star, distance);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				ExerelinUtilsAstro.addAsteroidBelt(system, star, 75, distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
				starBelts1.add(distance);
			}
			// And another one if a large system
			if(ExerelinSetupData.getInstance().baseSystemSize > 32000)
			{
				distance = getRandomOrbitRadiusBetweenPlanets(entities, 18000, 35000);
				baseOrbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star, distance);
				minOrbitDays = baseOrbitDays * 0.75f;
				maxOrbitDays = baseOrbitDays * 1.25f;
				ExerelinUtilsAstro.addAsteroidBelt(system, star, 100, distance, MathUtils.getRandomNumberInRange(160, 200),  minOrbitDays, maxOrbitDays);
				starBelts1.add(distance);
			}
			*/
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
				
				if (primaryData.entity == star) {
					
					// make a belt for star if we don't have one already
					/*
					if (starBelts1.isEmpty())
					{
						float distance = getRandomOrbitRadiusBetweenPlanets(entities, 3000 + star.getRadius(), 10000 + star.getRadius());
						float baseOrbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star2, distance);
						float minOrbitDays = baseOrbitDays * 0.75f;
						float maxOrbitDays = baseOrbitDays * 1.25f;

						ExerelinUtilsAstro.addAsteroidBelt(system, star2, 75, distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
						starBelts1.add(distance);
					}
					*/
					
					stationData.orbitRadius = (Float) ExerelinUtils.getRandomListElement(starBelts1);
				}
				else if (primaryData.entity == star2) 
				{
					// make a belt for binary companion if we don't have one already
					/*
					if (starBelts2.isEmpty())
					{
						float distance = getRandomOrbitRadiusBetweenPlanets(entities, 3000 + star.getRadius(), 10000 + star.getRadius());
						float baseOrbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star2, distance);
						float minOrbitDays = baseOrbitDays * 0.75f;
						float maxOrbitDays = baseOrbitDays * 1.25f;

						ExerelinUtilsAstro.addAsteroidBelt(system, star2, 75, distance, MathUtils.getRandomNumberInRange(160, 200), minOrbitDays, maxOrbitDays);
						starBelts2.add(distance);
					}
					*/

					stationData.orbitRadius = (Float) ExerelinUtils.getRandomListElement(starBelts2);
				}

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

		// Build hyperspace exits
		int jumpPointLNum = Math.random() < 0.5 ? 4 : 5;
		if (true || ExerelinSetupData.getInstance().numSystems > 1)
		{
			// capital jump point
			/*
			SectorEntityToken capitalToken = capital.entity;
			JumpPointAPI capitalJumpPoint = Global.getFactory().createJumpPoint(capitalToken.getId() + "_jump", capitalToken.getName() + " Jumppoint");
			float radius = capitalToken.getRadius();
			float orbitRadius = radius + 250f;
			float orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(capitalToken, orbitRadius);
			capitalJumpPoint.setCircularOrbit(capitalToken, ExerelinUtilsAstro.getRandomAngle(), orbitRadius, orbitDays);
			capitalJumpPoint.setRelatedPlanet(capitalToken);
			system.addEntity(capitalJumpPoint);
			capitalJumpPoint.setStandardWormholeToHyperspaceVisual();
			*/
			
			// capital L4/L5 jump point
			EntityData jumpLink = capital;
			if (jumpLink.type == EntityType.MOON) jumpLink = jumpLink.primary;	// L4/L5 of the planet instead of the moon
			JumpPointAPI capitalJumpPoint = Global.getFactory().createJumpPoint(jumpLink.entity.getId() + "_jump", jumpLink.name + " Bridge");
			log.info("Creating jump point at " + jumpLink.name + ", has primary? " + (jumpLink.primary != null));
			ExerelinUtilsAstro.setLagrangeOrbit(capitalJumpPoint, jumpLink.primary.entity, jumpLink.entity, 
					jumpPointLNum, jumpLink.startAngle, jumpLink.orbitRadius, 0, jumpLink.orbitPeriod, 
					!isBinary, ellipseAngle, ellipseMult);
			system.addEntity(capitalJumpPoint);
			capitalJumpPoint.setStandardWormholeToHyperspaceVisual();
			
			// loose star jump point
			/*
			JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint(star.getId() + "_jump", star.getName() + " Jumppoint");
			orbitRadius = MathUtils.getRandomNumberInRange(2500, 3500) + star.getRadius();
			orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star, orbitRadius);
			ExerelinUtilsAstro.setOrbit(jumpPoint, star, orbitRadius, !isBinary, ellipseAngle, orbitDays);
			system.addEntity(jumpPoint);
			jumpPoint.setStandardWormholeToHyperspaceVisual();
			
			// another one for binary companion
			if (isBinary)
			{
				JumpPointAPI jumpPoint2 = Global.getFactory().createJumpPoint(star2.getId() + "_jump", star2.getName() + " Jumppoint");
				orbitRadius = MathUtils.getRandomNumberInRange(2000, 3000) + star.getRadius();
				orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(star, orbitRadius);
				ExerelinUtilsAstro.setOrbit(jumpPoint2, star2, orbitRadius, !isBinary, ellipseAngle, orbitDays);
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
				float orbitPeriod = ExerelinUtilsAstro.getOrbitalPeriod(secondJumpFocus, orbitRadius);
				ExerelinUtilsAstro.setOrbit(jumpPoint2, secondJumpFocus, orbitRadius, !isBinary, ellipseAngle, orbitPeriod);
				system.addEntity(jumpPoint2);
				jumpPoint2.setStandardWormholeToHyperspaceVisual();
			}
			system.autogenerateHyperspaceJumpPoints(true, true);
		}

		// Build comm relay
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
		
		// add stellar ring
		if (Math.random() < STELLAR_RING_CHANCE)
		{
			float distance = getRandomOrbitRadiusBetweenPlanets(entities, 2500 + star.getRadius(), 8000 + star.getRadius());
			float totalWidth = 240;
			if (Math.random() < 0.6) {	// dust ring
				// ring (adapted from Magec.java)
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 2, Color.white, 256f, distance - 120, 1);
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 3, Color.white, 256f, distance - 40, .9f);
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 2, Color.white, 256f, distance + 40, 1);
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 3, Color.white, 256f, distance + 120, .9f);
			}
			else {	// ice ring
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 0, Color.white, 256f, distance - 120, 1);
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 0, Color.white, 256f, distance - 40, 1);
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 0, Color.white, 256f, distance + 40, 1);
				ExerelinUtilsAstro.addRingBand(system, star, "misc", "rings1", 256f, 1, Color.white, 256f, distance + 120, 1.1f);
			}

			// add one ring that covers all of the above
			SectorEntityToken ring = system.addTerrain(Terrain.RING, new BaseRingTerrain.RingParams(totalWidth + 160, distance, null));
			ring.setCircularOrbit(star, 0, 0, ExerelinUtilsAstro.getOrbitalPeriod(star, distance));
		}
		
		// add dead gate
		if (systemIndex == 0)
		{
			SectorEntityToken gate = system.addCustomEntity(system.getBaseName() + "_gate", // unique id
					 system.getBaseName() + " Gate", // name - if null, defaultName from custom_entities.json will be used
					 "inactive_gate", // type of object, defined in custom_entities.json
					 null); // faction
			float gateDist = MathUtils.getRandomNumberInRange(4000, 7000) + star.getRadius();
			ExerelinUtilsAstro.setOrbit(gate, star, gateDist, !isBinary, ellipseAngle, ExerelinUtilsAstro.getOrbitalPeriod(star, gateDist));
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
	
	public enum EntityType {
		STAR, PLANET, MOON, STATION
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
		MarketArchetype archetype = MarketArchetype.MIXED;
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
	
	public static class StarDef {
		String name = "";
		String type = "";
		float radius = 500;
		Color lightColor = null;
		float chance = 1;
		
		public StarDef(String name, String type, float radius, float chance)
		{
			this.name = name;
			this.type = type;
			this.radius = radius;
			this.chance = chance;
		}
		
		public StarDef(String name, String type, float radius, Color lightColor, float chance)
		{
			this.name = name;
			this.type = type;
			this.radius = radius;
			this.lightColor = lightColor;
			this.chance = chance;
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
			Now assign homeworlds for each faction
			Make list of all remaining unassigned planets + stations, shuffle
			For all planets + stations in the above list:
				If containing system is in list of already-checked systems, skip
				Add containing system to list of already-checked systems
				Assign this market to a rotated pirate faction
				Increment # of markets owned by that pirate faction

			For all remaining planets:
				Shuffle
				Each faction has a "share" (default 1)
				Number of planets each faction gets = round(faction share / total shares) * number of habitable planets
					This includes the free planets pirates got earlier
					For number of planets faction gets:
						Give planet to faction; remove planet from list
				After each faction has gotten their share, assign any remaining planets to random factions
	
			Repeat the above for stations not attached to a market
				Stations attached to an existing market are also generated, but not given their own market
		
		Finally juggle market conditions to try and balance commodity supply–demand
	*/
}