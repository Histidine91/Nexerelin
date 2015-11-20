package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class Valhalla {

	public void generate(SectorAPI sector) {
		
		StarSystemAPI system = sector.createStarSystem("Valhalla");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI star = system.initStar("valhalla",
										 "star_orange", // id in planets.json
										 400f,400*1.25f); 		// radius (in pixels at default zoom)
		
		system.setLightColor(new Color(255, 230, 220)); // light color in entire system, affects all entities
		
		
		/*
		 * addPlanet() parameters:
		 * 1. What the planet orbits (orbit is always circular)
		 * 2. Name
		 * 3. Planet type id in planets.json
		 * 4. Starting angle in orbit, i.e. 0 = to the right of the star
		 * 5. Planet radius, pixels at default zoom
		 * 6. Orbit radius, pixels at default zoom
		 * 7. Days it takes to complete an orbit. 1 day = 10 seconds.
		 */
		
		// Or: Grimnir   / The Einherjar / Heidrun / Eikthyrnir ?
		PlanetAPI val1 = system.addPlanet("glasnir", star, "Glasnir", "barren", 0, 90, 2000, 100);
		
		/* The Valkyries asteroid belt - some notable large ones? */ 
		system.addAsteroidBelt(star, 100, 3300, 256, 150, 250);
		system.addAsteroidBelt(star, 100, 3700, 256, 150, 250);
		
		PlanetAPI val2 = system.addPlanet("yggdrasil", star, "Yggdrasil", "gas_giant", 230, 350, 7500, 250);
		
		PlanetAPI val2a = system.addPlanet("nidhogg", val2, "Nidhogg", "lava_minor", 40, 40, 700, 22);
		val2a.setCustomDescriptionId("planet_nidhogg");
		
		PlanetAPI val2b = system.addPlanet("ratatosk", val2, "Ratatosk", "barren-bombarded", 50, 80, 1400, 45);
		val2b.setCustomDescriptionId("planet_ratatosk");
		val2b.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "asharu"));
		val2b.getSpec().setGlowColor(new Color(255,255,255,255));
		val2b.getSpec().setUseReverseLightForGlow(true);
		val2b.applySpecChanges();
			val2b.setInteractionImage("illustrations", "industrial_megafacility");
		
		PlanetAPI val2c = system.addPlanet("raesvelg", val2, "Raesvelg", "frozen", 50, 70, 2000, 40);
		val2c.setCustomDescriptionId("planet_raesvelg");
			val2c.setInteractionImage("illustrations", "vacuum_colony");
		
		PlanetAPI val3 = system.addPlanet("niflheim", star, "Niflheim", "ice_giant", 230, 250, 16000, 450);
		
//		SectorEntityToken mimir_station = system.addOrbitalStation("mimir_platform", val3, 45, 500, 50, "Mimir Siphon Platform", "tritachyon");
//		mimir_station.setCustomDescriptionId("station_mimir");
		
		SectorEntityToken mimir_station = system.addCustomEntity("mimir_platform", "Mimir Siphon Platform", "station_side05", "tritachyon");
		mimir_station.setCircularOrbitPointingDown(system.getEntityById("niflheim"), 45, 500, 50);		
		mimir_station.setCustomDescriptionId("station_mimir");
//		initStationCargo(mimir_station);
		
		PlanetAPI val3a = system.addPlanet("skathi", val3, "Skathi", "frozen", 50, 70, 1200, 60);
		val3a.setCustomDescriptionId("planet_skathi");
			val3a.setInteractionImage("illustrations", "cargo_loading");
		
		PlanetAPI val4 = system.addPlanet("ragnar", star, "Ragnar", "star_red", 45, 280, 20000, 1000);
		val4.setCustomDescriptionId("star_red_dwarf");
		system.addAsteroidBelt(val4, 50, 2000, 256, 200, 300);
		
//		SectorEntityToken ragnar_station = system.addOrbitalStation("ragnar_complex", val4, 45, 1500, 50, "Ragnar Complex", "hegemony");
//		ragnar_station.setCustomDescriptionId("station_ragnar");
	
		SectorEntityToken ragnar_station = system.addCustomEntity("ragnar_complex", "Ragnar Complex", "station_side02", "hegemony");
		ragnar_station.setCircularOrbitPointingDown(system.getEntityById("ragnar"), 45, 1500, 50);		
		ragnar_station.setCustomDescriptionId("station_ragnar");
		
		
		SectorEntityToken relay = system.addCustomEntity("ragnar_relay", // unique id
				 "Ragnar Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "hegemony"); // faction
		relay.setCircularOrbit(system.getEntityById("ragnar"), 90, 1000, 45);
		
		/*
		 * addRingBand() parameters:
		 * 1. What it orbits
		 * 2. Category under "graphics" in settings.json
		 * 3. Key in category
		 * 4. Width of band within the texture
		 * 5. Index of band
		 * 6. Color to apply to band
		 * 7. Width of band (in the game)
		 * 8. Orbit radius (of the middle of the band)
		 * 9. Orbital period, in days
		 */
		system.addRingBand(val2, "misc", "rings1", 256f, 2, Color.white, 256f, 1100, 40f);
		//system.addRingBand(a2, "misc", "rings1", 256f, 2, Color.white, 256f, 1100, 60f);
		//system.addRingBand(a2, "misc", "rings1", 256f, 2, Color.white, 256f, 1100, 80f);
		
//		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 1700, 50f);
//		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 1700, 70f);
//		system.addRingBand(a2, "misc", "rings1", 256f, 1, Color.white, 256f, 1700, 90f);
//		system.addRingBand(a2, "misc", "rings1", 256f, 1, Color.white, 256f, 1700, 110f);

		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("valkyrie_gate", "Valkyrie Gate");
		OrbitAPI orbit = Global.getFactory().createCircularOrbit(star, 0, 3500, 40);
		jumpPoint.setOrbit(orbit);
//		jumpPoint.setRelatedPlanet(star);
		jumpPoint.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPoint);
		
		
	/*
		SectorEntityToken station = system.addOrbitalStation(a1, 45, 300, 50, "Command & Control", "sindrian_diktat");
		initStationCargo(station);
	*/
	
		// example of using custom visuals below
//		a1.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "hull_breach", 800, 800));
//		jumpPoint.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "space_wreckage", 1200, 1200));
//		station.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "cargo_loading", 1200, 1200));
		
		// generates hyperspace destinations for in-system jump points
		system.autogenerateHyperspaceJumpPoints(true, true);
		
	/*
		DiktatPatrolSpawnPoint patrolSpawn = new DiktatPatrolSpawnPoint(sector, system, 5, 3, a1);
		system.addScript(patrolSpawn);
		for (int i = 0; i < 5; i++)
			patrolSpawn.spawnFleet();

		DiktatGarrisonSpawnPoint garrisonSpawn = new DiktatGarrisonSpawnPoint(sector, system, 30, 1, a1, a1);
		system.addScript(garrisonSpawn);
		garrisonSpawn.spawnFleet();
	*/
		
		//system.addScript(new IndependentTraderSpawnPoint(sector, hyper, 1, 10, hyper.createToken(-6000, 2000), station));
	}
	
}
