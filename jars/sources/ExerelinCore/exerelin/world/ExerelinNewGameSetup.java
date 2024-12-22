package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.events.CoreEventProbabilityManager;
import com.fs.starfarer.api.impl.campaign.fleets.DisposableLuddicPathFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.DisposablePirateFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.MercFleetManagerV2;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.CustomConstellationParams;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.*;
import exerelin.plugins.ExerelinCampaignPlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

@SuppressWarnings("unchecked")
public class ExerelinNewGameSetup implements SectorGeneratorPlugin
{
	//protected float numOmnifacs = 0;
	public static final Vector2f SECTOR_CENTER = new Vector2f(-4000, -6000);
	public static final Vector2f PRISM_LOC = new Vector2f(-8005, -3785);
	public static Logger log = Global.getLogger(ExerelinNewGameSetup.class);
	
	// don't use random, since seeds should generate the same result in Nex as in vanilla
	// but it turns out this isn't enough
	//protected Random rand = StarSystemGenerator.random;
	
	// runcode new exerelin.world.ExerelinNewGameSetup().addPrismMarket(Global.getSector(), false);
	public SectorEntityToken addPrismMarket(SectorAPI sector, boolean newGame)
	{
		String name = StringHelper.getString("nex_world", "prismSystem_stationName");
		SectorEntityToken prismEntity;
		
		if (ExerelinSetupData.getInstance().numSystems == 1)
		{
			// FIXME 
			SectorEntityToken toOrbit = Global.getSector().getEntityById("jangala");	//null;
			float radius = toOrbit.getRadius();
			float orbitDistance = radius + 150;
			if (toOrbit instanceof PlanetAPI)
			{
				PlanetAPI planet = (PlanetAPI)toOrbit;
				if (planet.isStar()) 
				{
					//orbitDistance = radius + 2000 + rand.nextFloat() * 500;
					orbitDistance = radius + 2500;
				}
			}
			prismEntity = toOrbit.getContainingLocation().addCustomEntity("nex_prismFreeport", name, "exerelin_freeport_type", Factions.INDEPENDENT);
			prismEntity.setCircularOrbitPointingDown(toOrbit, 343, orbitDistance, NexUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		}
		else if (!NexConfig.prismInHyperspace)
		{
			prismEntity = generatePrismInOwnSystem();
			if (!newGame) clearDeepHyper(prismEntity.getStarSystem().getHyperspaceAnchor(), 400);
		}
		else
		{
			LocationAPI hyperspace = sector.getHyperspace();
			prismEntity = hyperspace.addCustomEntity("nex_prismFreeport", name, "exerelin_freeport_type", "independent");
			prismEntity.setCircularOrbitWithSpin(hyperspace.createToken(PRISM_LOC), 343, 150, 60, 30, 30);
			clearDeepHyper(prismEntity, 400);
		}
		
		/*
		EntityData data = new EntityData(null);
		data.name = "Prism Freeport";
		data.type = EntityType.STATION;
		data.forceMarketSize = 4;
		
		MarketAPI market = addMarketToEntity(prismEntity, data, "independent");
		*/

		MarketAPI market = Global.getFactory().createMarket("nex_prismFreeport" /*+ "_market"*/, name, 5);
		market.setFactionId(Factions.INDEPENDENT);
		market.addCondition(Conditions.POPULATION_5);
		market.addIndustry(Industries.POPULATION);
		market.addIndustry("commerce");
		market.addIndustry(Industries.LIGHTINDUSTRY);
		//market.addIndustry(Industries.MILITARYBASE);
		market.addIndustry(Industries.PATROLHQ);
		market.addIndustry(Industries.MEGAPORT);
		//market.addIndustry(Industries.HEAVYINDUSTRY);
		market.addIndustry(Industries.WAYSTATION);
		market.addIndustry(Industries.HEAVYBATTERIES);
		market.addIndustry(Industries.STARFORTRESS_HIGH);	// Arrays.asList(new String[]{Commodities.ALPHA_CORE}));
		//market.addIndustry(Industries.CRYOSANCTUM);
		
		if (Global.getSettings().getModManager().isModEnabled("IndEvo")) {
			market.addIndustry("IndEvo_PrivatePort");
		}
		
		market.setFreePort(true);
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		//market.addSubmarket(Submarkets.GENERIC_MILITARY);
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		market.getTariff().modifyFlat("generator", sector.getFaction(Factions.INDEPENDENT).getTariffFraction());
		NexUtilsMarket.setTariffs(market);
		market.addSubmarket("exerelin_prismMarket");
		market.setPrimaryEntity(prismEntity);
		prismEntity.setMarket(market);
		prismEntity.setFaction(Factions.INDEPENDENT);
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);	// not doing this makes market condition tooltips fail to appear
		sector.getEconomy().addMarket(market, true);
		
		if (market.getContainingLocation().isHyperspace()) 
			market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMORY_KEY_UNINVADABLE, true);
		
		//pickEntityInteractionImage(prismEntity, market, "", EntityType.STATION);
		//prismEntity.setInteractionImage("illustrations", "space_bar");
		prismEntity.setCustomDescriptionId("exerelin_prismFreeport");
		
		
		// add important people
		if (!newGame) 
		{
			NexUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.SPACE_CAPTAIN, Ranks.POST_STATION_COMMANDER, true);
			NexUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.CITIZEN, Ranks.POST_PORTMASTER, true);
			NexUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.SPACE_COMMANDER, Ranks.POST_SUPPLY_OFFICER, true);
			ColonyManager.reassignAdminIfNeeded(market, market.getFaction(), market.getFaction());
		}
		
		return prismEntity;
	}
	
	public SectorEntityToken generatePrismInOwnSystem() {
		int dist = 1050;
		int orbitPeriod = 361;
		
		String name = StringHelper.getString("nex_world", "prismSystem_name");
		String systemName = name;	// will be used later
		StarSystemAPI system = Global.getSector().createStarSystem(name);
		system.setType(StarSystemGenerator.StarSystemType.NEBULA);
		system.setAge(StarAge.YOUNG);
		system.getLocation().set(PRISM_LOC);
		ProcgenUsedNames.notifyUsed(name);
		
		// temporarily create a "star" (needed for some things)
		PlanetAPI star = system.initStar("nex_prism_center", "nebula_center_young", 0, 0);
		star.setSkipForJumpPointAutoGen(true);
		star.addTag(Tags.AMBIENT_LS);
		StarSystemGenerator.addSystemwideNebula(system, StarAge.YOUNG);
		
		// jump point
		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("nex_prism_jump", 
				StringHelper.getString("nex_world", "prismSystem_jumpName"));
		jumpPoint.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPoint);
		
		// remove the star
		system.removeEntity(star);
		StarCoronaTerrainPlugin coronaPlugin = Misc.getCoronaFor(star);
		if (coronaPlugin != null) {
			system.removeEntity(coronaPlugin.getEntity());
		}
		system.setStar(null);
		SectorEntityToken center = system.initNonStarCenter();
		center.addTag(Tags.AMBIENT_LS);
		jumpPoint.setCircularOrbit(center, 0, dist, orbitPeriod);
		
		system.autogenerateHyperspaceJumpPoints(true, false);
		
		system.setStar(star);
		
		// comm relay
		SectorEntityToken relay = system.addCustomEntity("nex_prism_relay", null, 
				Entities.COMM_RELAY_MAKESHIFT, Factions.INDEPENDENT);
		int radiusRatio = 2;
		int period2 = (int)Math.round(Math.sqrt(Math.pow(radiusRatio, 3)) * orbitPeriod);
		relay.setCircularOrbitPointingDown(star, 105, dist*radiusRatio, period2);
		
		// station
		name = StringHelper.getString("nex_world", "prismSystem_stationName");
		SectorEntityToken prism = system.addCustomEntity("nex_prismFreeport", name, "exerelin_freeport_type", Factions.INDEPENDENT);
		prism.setCircularOrbitWithSpin(center, 240, dist, orbitPeriod, 30, 30);
		
		// gate (I give in)
		name = StringHelper.getString("nex_world", "prismSystem_gateName");
		SectorEntityToken gate = system.addCustomEntity("nex_prism_gate",
						 name,
						 "inactive_gate", // type of object, defined in custom_entities.json
						 null); // faction
		radiusRatio = 6;
		int period3 = (int)Math.round(Math.sqrt(Math.pow(radiusRatio, 3)) * orbitPeriod);
		gate.setCircularOrbitPointingDown(star, 225, dist*radiusRatio, period3);
		
		// do it last to ensure correct naming of the system
		system.setBaseName(systemName);
		
		return prism;
	}
	
	public void validateLocation(SectorAPI sector, StarSystemAPI system, Vector2f startLoc) 
	{
		Random random = new Random(NexUtils.getStartingSeed());
		float minDist = 100;
		try_again:
		for (int i = 0; i < 100; i++) {
			Vector2f loc = new Vector2f(startLoc);
			if (i > 0) {
				Vector2f mod = new Vector2f(0, 120*i);
				mod = VectorUtils.rotate(mod, random.nextFloat() * 360);
				loc.x += mod.x;
				loc.y += mod.y;
			}
			
			for (StarSystemAPI sys : sector.getStarSystems()) {
				float otherRadius = sys.getMaxRadiusInHyperspace();
				if (MathUtils.getDistance(sys.getLocation(), loc) < minDist + otherRadius) {
					log.info("Prism invalid location attempt " + i);
					continue try_again;
				}
			}
			system.getLocation().set(loc.x, loc.y);
			break;
		}
		
		clearDeepHyper(system.getHyperspaceAnchor(), 350);
	}
	
	public static void clearDeepHyper(SectorEntityToken entity, float radius) {
		// deep hyperspace removal (copypasted from UW)
		HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
		NebulaEditor editor = new NebulaEditor(plugin);

		float minRadius = plugin.getTileSize() * 2f;
		editor.clearArc(entity.getLocation().x, entity.getLocation().y, 0, radius + minRadius * 0.5f, 0, 360f);
		editor.clearArc(entity.getLocation().x, entity.getLocation().y, 0, radius + minRadius, 0, 360f, 0.25f);
	}
	
	protected void addAntiochPart1(SectorAPI sector)
	{
		ProcgenUsedNames.notifyUsed("Antioch");
		ProcgenUsedNames.notifyUsed("Ascalon");
		String className = "data.scripts.world.templars.TEM_Antioch";
		String toExecute = className + ".generatePt1(sector)";
		String[] paramNames = {"sector"};
		try {
			NexUtils.runCode(toExecute, paramNames, null, sector);
		} catch (Exception ex) {
			log.error("Failed to add Antioch to random sector", ex);
		}
	}

	public void generateStarSystems(SectorAPI sector) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean corvusMode = setupData.corvusMode;
		boolean grandSector = Global.getSettings().getModManager().isModEnabled("ZGrand Sector");
		boolean adjustedSector = Global.getSettings().getModManager().isModEnabled("Adjusted Sector");

		Global.getSector().getMemoryWithoutUpdate().set(ExerelinConstants.MEMORY_KEY_RANDOM_SECTOR, !corvusMode);

		// use vanilla hyperspace map
		String hyperMap = "data/campaign/terrain/hyperspace_map.png";
		if (Global.getSettings().getModManager().isModEnabled("Vast Expanse")) {
			hyperMap = "data/campaign/terrain/Big_Hyperspace_Map.png";
		}
		else if (grandSector) {
			boolean generateHS = Global.getSettings().getBoolean("GrandSectorBoolHyperstorms");
			//log.info("Generating Grand Sector hyper map, hyperstorms: " + generateHS);
			if (generateHS)
				hyperMap = "data/campaign/terrain/anon_hyperspace.png";
			else
				hyperMap = "data/campaign/terrain/clear_skies.png";
		}
		else if (adjustedSector) {
			boolean generateHS = Global.getSettings().getBoolean("AdjustedSectorHS");
			if (generateHS)
				hyperMap = "data/campaign/terrain/hyperspace_new.png";
			else {
				// AS no longer has a no-storms mode
				//hyperMap = "data/campaign/terrain/no_storms.png";
			}
		}

		SectorEntityToken deep_hyperspace = Misc.addNebulaFromPNG(hyperMap,
				0, 0, // center of nebula
				sector.getHyperspace(), // location to add to
				"terrain", "deep_hyperspace", // "nebula_blue", // texture to use, uses xxx_map for map
				4, 4, Terrain.HYPERSPACE, StarAge.ANY); // number of cells in texture

		// make Prism before core systems, unless we're in random sector with one system
		// in which case we'll need to populate that system and then put Prism in it
		// FIXME: this is not actually implemented
		boolean prismBeforeSystems = corvusMode || setupData.numSystems > 1;
		SectorEntityToken prism = null;
		if (setupData.prismMarketPresent && prismBeforeSystems) {
			prism = addPrismMarket(sector, true);
		}

		if (corvusMode)
		{
			VanillaSystemsGenerator.generate(sector);
			if (grandSector || adjustedSector) {
				// ensure area around stars is clear
				// no need to do it in random sector, since ExerelinCoreSystemGenerator has its own clearer
				HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
				NebulaEditor editor = new NebulaEditor(plugin);
				float minRadius = plugin.getTileSize() * 2f;
				for (StarSystemAPI curr : sector.getStarSystems()) {
					float radius = curr.getMaxRadiusInHyperspace() * 0.5f;
					editor.clearArc(curr.getLocation().x, curr.getLocation().y, 0, radius + minRadius * 0.5f, 0, 360f);
					editor.clearArc(curr.getLocation().x, curr.getLocation().y, 0, radius + minRadius, 0, 360f, 0.25f);
				}
			}
		}
		else
		{
			// make core constellation
			CustomConstellationParams params = new CustomConstellationParams(StarAge.ANY);
			int num = ExerelinSetupData.getInstance().numSystems;
			int min = num + 1;
			int max = (int)Math.max(num * 1.2f, num + 3);
			if (max < 16) max = 16;
			if (max > 24) max = 24;
			if (min > max - 3) min = max - 3;
			if (min < 12) min = 12;

			log.info(String.format("Generating system with %s min, %s max stars", min, max));

			params.minStars = min;
			params.maxStars = max;
			params.location = SECTOR_CENTER;
			ExerelinCoreSystemGenerator gen = new ExerelinCoreSystemGenerator(params);
			gen.generate();

			//SectorEntityToken coreLabel = Global.getSector().getHyperspace().addCustomEntity("core_label_id", null, "core_label", null);
			//coreLabel.setFixedLocation(SECTOR_CENTER.getX(), SECTOR_CENTER.getY());

			if (ExerelinSetupData.getInstance().randomAntiochEnabled && (setupData.factions.containsKey("templars")
					&& setupData.factions.get("templars")))
				addAntiochPart1(sector);

			if (setupData.prismMarketPresent && !prismBeforeSystems) {
				prism = addPrismMarket(sector, true);
			}
		}

		if (prism != null && prism.getStarSystem() != null) {
			validateLocation(sector, prism.getStarSystem(), PRISM_LOC);
		}
	}

	public void setupPlugins(SectorAPI sector) {
		log.info("Adding scripts and plugins");

		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean corvusMode = setupData.corvusMode;

		sector.registerPlugin(new CoreCampaignPluginImpl());
		sector.registerPlugin(new ExerelinCampaignPlugin());
		sector.addScript(new CoreScript());
		sector.addScript(new CoreEventProbabilityManager());
		sector.addScript(new EconomyFleetRouteManager());

		//sector.addScript(new PatrolFleetManagerReplacer());

		if (ExerelinModPlugin.HAVE_DYNASECTOR)
		{
			// TODO if DS ever comes back
			/*
			sector.addScript(new DS_MercFleetManager());
			sector.addScript(new DS_LuddicPathFleetManager());
			sector.addScript(new DS_PirateFleetManager());
			//sector.addScript(new DS_BountyPirateFleetManager());
			*/
		}
		else
		{
			sector.addScript(new MercFleetManagerV2());
			sector.addScript(new DisposablePirateFleetManager());
			sector.addScript(new DisposableLuddicPathFleetManager());
			//sector.addScript(new BountyPirateFleetManager());
		}

		ExerelinModPlugin.addScripts();

		StatsTracker.getOrCreateTracker();

		DiplomacyManager.getManager().setStartRelationsMode(setupData.startRelationsMode);
		DiplomacyManager.getManager().setApplyStartRelationsModeToPirates(setupData.applyStartRelationsModeToPirates);
		DiplomacyManager.initFactionRelationships(false);

		SectorManager.getManager().setCorvusMode(corvusMode);
		SectorManager.getManager().setHardMode(setupData.hardMode);
		SectorManager.getManager().setFreeStart(setupData.freeStart);

		String factionId = PlayerFactionStore.getPlayerFactionIdNGC();
		sector.getCharacterData().getMemoryWithoutUpdate().set(PlayerFactionStore.STARTING_FACTION_ID_MEMKEY, factionId);
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		if (conf.spawnAsFactionId != null && !conf.spawnAsFactionId.isEmpty())
		{
			factionId = conf.spawnAsFactionId;
		}

		// commission
		if (!factionId.equals(Factions.PLAYER)) {
			NexUtilsFaction.grantCommission(factionId);
		}
	}
	
	@Override
	public void generate(SectorAPI sector)
	{
		log.info("Starting sector generation...");
		
		generateStarSystems(sector);
		setupPlugins(sector);
				
		log.info("Finished sector generation");
	}
}