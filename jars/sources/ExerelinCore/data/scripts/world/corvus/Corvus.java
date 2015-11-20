package data.scripts.world.corvus;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;

@SuppressWarnings("unchecked")
public class Corvus { // implements SectorGeneratorPlugin {

	public void generate(SectorAPI sector) {
		final StarSystemAPI system = sector.getStarSystem("Corvus");

		PlanetAPI star = system.initStar("corvus", "star_yellow", 475f,
				500, // extent of corona outside star
				10f, // solar wind burn level
				1f, // flare probability
				3f); // CR loss multiplier, good values are in the range of 1-5
//		star.getSpec().setPlanetColor(new Color(255, 100, 100));
//		star.getSpec().setAtmosphereColor(new Color(255, 100, 100));
//		star.getSpec().setIconColor(new Color(255, 100, 100));
//		star.getSpec().setCloudColor(new Color(255, 100, 100));
//		star.applySpecChanges();
//		
//		system.setLightColor(new Color(0, 255, 0));
		
//		PlanetAPI corvusXa = system.addPlanet("test1", star, "Anomaly Alpha", "lava_minor", 240, 120, 2500, 120);
//		PlanetAPI corvusXb = system.addPlanet("test2", star, "Anomaly Beta", "lava", 250, 120, 2100, 120);
		
	// Asharu
		PlanetAPI corvusI = system.addPlanet("asharu", star, "Asharu", "desert", 55, 150, 2800, 100);
		corvusI.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "asharu"));
		corvusI.getSpec().setGlowColor(new Color(255,255,255,255));
		corvusI.getSpec().setUseReverseLightForGlow(true);
		corvusI.applySpecChanges();
		corvusI.setCustomDescriptionId("planet_asharu");
		
		// Asharu stellar shade - out of orbit, settled in one of Asharu's lagrangian points 
		SectorEntityToken asharu_shade = system.addCustomEntity("asharu_shade", "Asharu Stellar Shade", "stellar_shade", "neutral");
		asharu_shade.setCircularOrbitPointingDown(system.getEntityById("corvus"), 55 + 60, 2800, 100);		
		asharu_shade.setCustomDescriptionId("stellar_shade");
		
	// Jangala 
		PlanetAPI corvusII = system.addPlanet("jangala", star, "Jangala", "jungle", 245, 200, 4500, 200);		
		corvusII.setCustomDescriptionId("planet_jangala");
		corvusII.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
		corvusII.getSpec().setGlowColor(new Color(255,255,255,255));
		corvusII.getSpec().setUseReverseLightForGlow(true);
		corvusII.applySpecChanges();
		
			// Jangala Station 
			SectorEntityToken hegemonyStation = system.addCustomEntity("corvus_hegemony_station",
					"Jangala Station", "station_jangala_type", "hegemony");
			
			hegemonyStation.setCircularOrbitPointingDown(system.getEntityById("jangala"), 45 + 180, 300, 50);		
			hegemonyStation.setCustomDescriptionId("station_jangala");
			
			// Jangala Relay - L5 (behind)
			SectorEntityToken relay = system.addCustomEntity("corvus_relay", // unique id
					 "Jangala Relay", // name - if null, defaultName from custom_entities.json will be used
					 "comm_relay", // type of object, defined in custom_entities.json
					 "hegemony"); // faction
			relay.setCircularOrbitPointingDown(system.getEntityById("corvus"), 245-60, 4500, 200);
		
			// Jangala Jumppoint - L4 (ahead)
			JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("jangala_jump", "Jangala Jumppoint");
			jumpPoint.setCircularOrbit(system.getEntityById("corvus"), 245+60, 4500, 200);
			jumpPoint.setRelatedPlanet(corvusII);
			
			jumpPoint.setStandardWormholeToHyperspaceVisual();
			system.addEntity(jumpPoint);
		
		// Corvus Gate
		SectorEntityToken gate = system.addCustomEntity("jangala_gate", // unique id
				 "Corvus Gate", // name - if null, defaultName from custom_entities.json will be used
				 "inactive_gate", // type of object, defined in custom_entities.json
				 null); // faction
		gate.setCircularOrbit(system.getEntityById("corvus"), 0, 6000, 350);

	// Not-yet-named Asteroids // Let's try "Nemo"
		system.addAsteroidBelt(star, 90, 5650, 500, 150, 300, Terrain.ASTEROID_BELT,  "Nemo's Belt");
		system.addRingBand(star, "misc", "rings1", 256f, 2, Color.white, 256f, 5600, 305f, null, null);
		system.addRingBand(star, "misc", "rings2", 256f, 2, Color.white, 256f, 5720, 295f, null, null);
		
	// Barad system
		SectorEntityToken corvusIII = system.addPlanet("barad", star, "Barad", "gas_giant", 200, 300, 7800, 400);
		
		// Barad magnetic field
		SectorEntityToken barad_field = system.addTerrain(Terrain.MAGNETIC_FIELD,
				new MagneticFieldParams(corvusIII.getRadius() + 200f, // terrain effect band width 
						(corvusIII.getRadius() + 200f) / 2f, // terrain effect middle radius
						corvusIII, // entity that it's around
						corvusIII.getRadius() + 50f, // visual band start
						corvusIII.getRadius() + 50f + 250f, // visual band end
						new Color(50, 20, 100, 40), // base color
						0.5f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
						new Color(140, 100, 235),
						new Color(180, 110, 210),
						new Color(150, 140, 190),
						new Color(140, 190, 210),
						new Color(90, 200, 170), 
						new Color(65, 230, 160),
						new Color(20, 220, 70)
				));
		barad_field.setCircularOrbit(corvusIII, 0, 0, 100);
		
			SectorEntityToken corvusIIIA = system.addPlanet("corvus_IIIa", corvusIII, "Barad A", "cryovolcanic", 135, 100, 790, 20);
			corvusIIIA.setCustomDescriptionId("planet_barad_a");
			
			// Pirate Station
			SectorEntityToken pirateStation = system.addCustomEntity("corvus_pirate_station",
					"Hidden Pirate Base", "station_pirate_type", "pirates");
			pirateStation.setCircularOrbitPointingDown(system.getEntityById("corvus_IIIa"), 45, 300, 50);		
			pirateStation.setCustomDescriptionId("pirate_base_barad");
			pirateStation.setInteractionImage("illustrations", "pirate_station");
			
			//system.addAsteroidBelt(corvusIII, 50, 1000, 200, 10, 45, Terrain.ASTEROID_BELT, null);
			system.addRingBand(corvusIII, "misc", "rings1", 256f, 0, Color.white, 256f, 1050, 45, Terrain.RING, null);
			
			SectorEntityToken corvusIIIB = system.addPlanet("corvus_IIIb", corvusIII, "Barad B", "barren", 235, 100, 1300, 60);
			corvusIIIB.setInteractionImage("illustrations", "vacuum_colony");
				
			// Barad trojans
			SectorEntityToken baradL4 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						500f, // min radius
						700f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Barad L4 Asteroids")); // null for default name
			
			SectorEntityToken baradL5 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						500f, // min radius
						700f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Barad L5 Asteroids")); // null for default name
			
			baradL4.setCircularOrbit(star, 260f, 7800, 400);
			baradL5.setCircularOrbit(star, 140f, 7800, 400);
				
	// Outer system
		PlanetAPI corvusIV = system.addPlanet("corvus_IV", star, "Somnus", "barren-bombarded", 0, 160, 10000, 600);
		corvusIV.getSpec().setPlanetColor(new Color(225,255,245,255));
		corvusIV.applySpecChanges();
		
		PlanetAPI corvusV = system.addPlanet("corvus_V", star, "Mors", "frozen", 300, 135, 11800, 450);	
		corvusV.getSpec().setPlanetColor(new Color(225,255,245,255));
		corvusV.applySpecChanges();
		
		LocationAPI hyper = Global.getSector().getHyperspace();
		system.autogenerateHyperspaceJumpPoints(true, true);
		
		SectorEntityToken neutralStation = system.addOrbitalStation("corvus_abandoned_station", system.getEntityById("asharu"), 45, 300, 50, "Abandoned Terraforming Platform", "neutral");
		neutralStation.getMemory().set("$abandonedStation", true);
		MarketAPI market = Global.getFactory().createMarket("corvus_abandoned_station_market", neutralStation.getName(), 0);
		market.setPrimaryEntity(neutralStation);
		market.setFactionId(neutralStation.getFaction().getId());
//		market.addCondition("orbital_station");
//		market.addCondition("regional_capital");
//		market.addCondition("military_base");
//		market.addCondition("population_5");
		market.addCondition(Conditions.ABANDONED_STATION);
//		market.addSubmarket(Strings.SUBMARKET_OPEN);
//		market.addSubmarket(Strings.SUBMARKET_BLACK);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);
		neutralStation.setMarket(market);
		neutralStation.setCustomDescriptionId("asharu_platform");
		neutralStation.setInteractionImage("illustrations", "abandoned_station2");
		
		neutralStation.getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addMothballedShip(FleetMemberType.SHIP, "hermes_d_Hull", null);
		
		
		SectorEntityToken nebula = system.addTerrain(Terrain.NEBULA, new BaseTiledTerrain.TileParams(
				"   xx " +
				"  xx x" +
				" xxxx " +
				"xxxxxx" +
				"  xx  " +
				"    x ",
				6, 6, // size of the nebula grid, should match above string
				"terrain", "nebula", 4, 4));
		nebula.getLocation().set(corvusII.getLocation().x + 1000f, corvusII.getLocation().y);
		nebula.setCircularOrbit(star, 140f, 11000, 500);
		
		
		/*SectorEntityToken hyperChunk = hyper.addTerrain(Terrain.NEBULA, new BaseTiledTerrain.TileParams(
				"x         " +
				"xx        " +
				"xxx       " +
				"xxxx      " +
				"xxxxx     " +
				"xxxxxx    " +
				"xxxxxxx   " +
				"xxxxxxxx  " +
				"xxxxxxxxx " +
				"xxxxxxxxxx",
				10, 10, // size of the nebula grid, should match above string
				"terrain", "nebula_dark", 4, 4));
		hyperChunk.getLocation().set(system.getLocation().x + 3000f,
									 system.getLocation().y); */
		
		/*SectorEntityToken nebula2 = Misc.addNebulaFromPNG("data/campaign/terrain/nebula_test.png",
														  0, 0, // center of nebula
														  system, // location to add to
														  "terrain", "nebula", // texture to use, uses xxx_map for map
														  4, 4); // number of cells in texture
		 */
		//nebula2.setCircularOrbit(star, 0, 2000, 200f);
		
		/*
		SectorEntityToken asteroidField = system.addTerrain(Terrain.ASTEROID_FIELD,
						new AsteroidFieldParams(
								500f, // min radius
								700f, // max radius
								20, // min asteroid count
								30, // max asteroid count
								4f, // min asteroid radius 
								16f, // max asteroid radius
								"<name goes here>")); // null for default name
		asteroidField.setCircularOrbit(star, 180f, 4000, 500);
		
		*/
	}

	
}









