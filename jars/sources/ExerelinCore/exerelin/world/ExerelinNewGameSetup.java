package exerelin.world;

import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetManager;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.events.CoreEventProbabilityManager;
import com.fs.starfarer.api.impl.campaign.fleets.BountyPirateFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.LuddicPathFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.MercFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.PirateFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.CustomConstellationParams;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.fleets.DS_BountyPirateFleetManager;
import data.scripts.campaign.fleets.DS_LuddicPathFleetManager;
import data.scripts.campaign.fleets.DS_MercFleetManager;
import data.scripts.campaign.fleets.DS_PirateFleetManager;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.plugins.*;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.fleets.PatrolFleetManagerReplacer;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.Random;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

@SuppressWarnings("unchecked")
public class ExerelinNewGameSetup implements SectorGeneratorPlugin
{
	//protected float numOmnifacs = 0;
	public static final Vector2f SECTOR_CENTER = new Vector2f(0, -6000);
	public static Logger log = Global.getLogger(ExerelinNewGameSetup.class);
	
	protected Random rand = null;
	
	protected void addPrismMarket(SectorAPI sector)
	{
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
					orbitDistance = radius + 2000 + rand.nextFloat() * 500;
				}
			}
			prismEntity = toOrbit.getContainingLocation().addCustomEntity("prismFreeport", "Prism Freeport", "exerelin_freeport_type", "independent");
			prismEntity.setCircularOrbitPointingDown(toOrbit, ExerelinUtilsAstro.getRandomAngle(rand), orbitDistance, ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitDistance));
		}
		else
		{
			LocationAPI hyperspace = sector.getHyperspace();
			prismEntity = hyperspace.addCustomEntity("prismFreeport", "Prism Freeport", "exerelin_freeport_type", "independent");
			float xpos = 2000;
			if (!ExerelinSetupData.getInstance().corvusMode) xpos = -2000;
			prismEntity.setCircularOrbitWithSpin(hyperspace.createToken(xpos, 0), ExerelinUtilsAstro.getRandomAngle(rand), 150, 60, 30, 30);
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
		//market.addCondition("exerelin_recycling_plant");
		//market.addCondition("exerelin_recycling_plant");
		//market.addCondition("exerelin_supply_workshop");
		//market.addCondition("exerelin_hydroponics");
		market.addCondition(Conditions.HYDROPONICS_COMPLEX);
		market.addCondition(Conditions.TRADE_CENTER);
		market.addCondition(Conditions.STEALTH_MINEFIELDS);
		market.addCondition(Conditions.CRYOSANCTUM);
		market.addCondition(Conditions.MILITARY_BASE);
		market.addCondition(Conditions.FREE_PORT);
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.setBaseSmugglingStabilityValue(0);
		market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMORY_KEY_UNINVADABLE, true);
		
		ExerelinMarketBuilder.addStartingMarketCommodities(market);
		
		market.getTariff().modifyFlat("generator", sector.getFaction(Factions.INDEPENDENT).getTariffFraction());
		ExerelinUtilsMarket.setTariffs(market);
		market.addSubmarket("exerelin_prismMarket");
		market.setPrimaryEntity(prismEntity);
		prismEntity.setMarket(market);
		prismEntity.setFaction(Factions.INDEPENDENT);
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);	// not doing this makes market condition tooltips fail to appear
		sector.getEconomy().addMarket(market);
		
		//pickEntityInteractionImage(prismEntity, market, "", EntityType.STATION);
		//prismEntity.setInteractionImage("illustrations", "space_bar");
		prismEntity.setCustomDescriptionId("exerelin_prismFreeport");
	}
	
	
	
	@Override
	public void generate(SectorAPI sector)
	{
		log.info("Starting sector generation...");
		rand = new Random(ExerelinUtils.getStartingSeed());
		
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean corvusMode = setupData.corvusMode;
		
		// use vanilla hyperspace map
		String hyperMap = "data/campaign/terrain/hyperspace_map.png";
		SectorEntityToken deep_hyperspace = Misc.addNebulaFromPNG(hyperMap,
			  0, 0, // center of nebula
			  sector.getHyperspace(), // location to add to
			  "terrain", "deep_hyperspace", // "nebula_blue", // texture to use, uses xxx_map for map
			  4, 4, Terrain.HYPERSPACE, StarAge.ANY); // number of cells in texture
		
		if (corvusMode)
		{
			VanillaSystemsGenerator.generate(sector);
		}
		else
		{
			// make core system
			CustomConstellationParams params = new CustomConstellationParams(StarAge.ANY);
			int num = ExerelinSetupData.getInstance().numSystems;
			params.minStars = 12;	//num;
			params.maxStars = 18;	//num + (int)Math.max(num * 1.2f, 2);
			params.location = SECTOR_CENTER;
			new ExerelinCoreSystemGenerator(params).generate();
			
			SectorEntityToken coreLabel = Global.getSector().getHyperspace().addCustomEntity("core_label_id", null, "core_label", null);
			coreLabel.setFixedLocation(SECTOR_CENTER.getX(), SECTOR_CENTER.getY());
		}
		
		if (setupData.prismMarketPresent) {
			if (!corvusMode || !ExerelinUtilsFaction.doesFactionExist("SCY"))
				addPrismMarket(sector);
		}
		
		final String selectedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);
		
		log.info("Adding scripts and plugins");
		sector.addScript(new CoreScript());
		sector.addScript(new PatrolFleetManagerReplacer());
		sector.registerPlugin(new CoreCampaignPluginImpl());
		sector.registerPlugin(new ExerelinCoreCampaignPlugin());
		sector.addScript(new CoreEventProbabilityManager());
		sector.addScript(new EconomyFleetManager());
		if (ExerelinModPlugin.HAVE_DYNASECTOR)
		{
			sector.addScript(new DS_MercFleetManager());
			sector.addScript(new DS_LuddicPathFleetManager());
			sector.addScript(new DS_PirateFleetManager());
			sector.addScript(new DS_BountyPirateFleetManager());
		}
		else
		{
			sector.addScript(new MercFleetManager());
			sector.addScript(new LuddicPathFleetManager());
			sector.addScript(new PirateFleetManager());
			sector.addScript(new BountyPirateFleetManager());
		}			
		
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
		DiplomacyManager.initFactionRelationships(false);
		
		SectorManager.setCorvusMode(corvusMode);
		SectorManager.setHardMode(setupData.hardMode);
		SectorManager.setFreeStart(setupData.freeStart);
		
		// Remove any data stored in ExerelinSetupData
		//resetVars();
		//ExerelinSetupData.resetInstance();
		
		log.info("Finished sector generation");
	}
}