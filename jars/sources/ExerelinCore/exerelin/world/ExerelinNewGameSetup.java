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
import org.lazywizard.lazylib.MathUtils;

@SuppressWarnings("unchecked")

public class ExerelinNewGameSetup implements SectorGeneratorPlugin
{
	
	//protected float numOmnifacs = 0;
	
	public static Logger log = Global.getLogger(ExerelinNewGameSetup.class);
	
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
		
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean corvusMode = setupData.corvusMode;
		
		if (corvusMode)
		{
			VanillaSystemsGenerator.generate(sector);
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
			  4, 4, Terrain.HYPERSPACE, StarAge.ANY); // number of cells in texture
		
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
		if (!corvusMode) 
		{
			
		}
		
		
		SectorManager.setCorvusMode(corvusMode);
		SectorManager.setHardMode(setupData.hardMode);
		SectorManager.setFreeStart(setupData.freeStart);
		
		// Remove any data stored in ExerelinSetupData
		//resetVars();
		//ExerelinSetupData.resetInstance();
		
		log.info("Finished sector generation");
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