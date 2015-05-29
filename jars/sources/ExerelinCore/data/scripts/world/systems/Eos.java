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

public class Eos {

	public void generate(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("Eos");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI star = system.initStar("eos",
										 "star_white", // id in planets.json
										 750f); 		// radius (in pixels at default zoom)
		
		system.setLightColor(new Color(255, 255, 255)); // light color in entire system, affects all entities
		

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
		
		
		// TODO clean these up & tweak visuals from defaults
		PlanetAPI eos1 = system.addPlanet("phaosphoros", star, "Phaosphoros", "gas_giant", 100, 300, 2500, 40);
			eos1.getSpec().setAtmosphereColor(new Color(255,245,200,220));
			eos1.getSpec().setPlanetColor(new Color(245,250,255,255));
			eos1.applySpecChanges();
			eos1.setCustomDescriptionId("hot_gas_giant");
		PlanetAPI eos1a = system.addPlanet("lucifer", eos1, "Lucifer", "lava", 0, 60, 500, 18);
		
		PlanetAPI eos2 = system.addPlanet("tartessus", star, "Tartessus", "arid", 200, 180, 4400, 100);
		eos2.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "sindria"));
		eos2.getSpec().setGlowColor(new Color(245,255,250,255));
		eos2.getSpec().setUseReverseLightForGlow(true);
		eos2.applySpecChanges();
		eos2.setCustomDescriptionId("planet_tartessus");
		
		PlanetAPI eos2a = system.addPlanet("baetis", eos2, "Baetis", "barren-bombarded", 0, 35, 400, 30);
		eos2a.setCustomDescriptionId("planet_baetis");
		
		PlanetAPI eos3 = system.addPlanet("hesperus", star, "Hesperus", "rocky_ice", 0, 150, 8000, 200);
		eos3.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "sindria"));
		eos3.getSpec().setGlowColor(new Color(245,255,250,255));
		eos3.getSpec().setUseReverseLightForGlow(true);
		eos3.applySpecChanges();
		eos3.setCustomDescriptionId("planet_hesperus");
		
		PlanetAPI eos3a = system.addPlanet("ceyx", eos3, "Ceyx", "barren-bombarded", 0, 20, 500, 16);
		eos3a.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "barren"));
		eos3a.getSpec().setGlowColor(new Color(200,230,255,200));
		eos3a.getSpec().setUseReverseLightForGlow(true);
		eos3a.applySpecChanges();
		eos3a.setCustomDescriptionId("planet_ceyx");
		
		PlanetAPI eos3b = system.addPlanet("daedaleon", eos3, "Daedaleon", "radiated", 0, 50, 620, 33);
		eos3b.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
		eos3b.getSpec().setGlowColor(new Color(255,55,250,200));
		eos3b.getSpec().setUseReverseLightForGlow(true);
		eos3b.applySpecChanges();
		eos3b.setCustomDescriptionId("planet_daedaleon");

		SectorEntityToken relay = system.addCustomEntity("hesperus_relay", // unique id
				 "Hesperus Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "knights_of_ludd"); // faction
		relay.setCircularOrbit(system.getEntityById("hesperus"), 90, 1000, 45);

		// TODO descriptions!
		//m1.setCustomDescriptionId("planet_arcadia_i");
		
		/* TODO
		m1.getSpec().setPlanetColor(new Color(200,225,255,255));
		m1.getSpec().setAtmosphereColor(new Color(140,160,225,140));
		m1.getSpec().setCloudColor(new Color(120,140,220,200));
		m1.getSpec().setTilt(36);
		m1.applySpecChanges();
		*/
		
		/*
		 * addAsteroidBelt() parameters:
		 * 1. What the belt orbits
		 * 2. Number of asteroids
		 * 3. Orbit radius
		 * 4. Belt width
		 * 6/7. Range of days to complete one orbit. Value picked randomly for each asteroid. 
		 */
		//system.addAsteroidBelt(star, 100, 3300, 256, 150, 250);

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
		
		//system.addRingBand(star, "misc", "rings1", 256f, 2, Color.white, 256f, 3200, 80f);
		
		JumpPointAPI eos2JumpPoint = Global.getFactory().createJumpPoint("paladins_bridge", "Paladins' Bridge");
		OrbitAPI orbit = Global.getFactory().createCircularOrbit(eos2, 0, 640, 30);
		eos2JumpPoint.setOrbit(orbit);
		eos2JumpPoint.setRelatedPlanet(eos2);
		eos2JumpPoint.setStandardWormholeToHyperspaceVisual();
		system.addEntity(eos2JumpPoint);
		
		
		//SectorEntityToken station = system.addOrbitalStation(m31, 45, 300, 50, "Enterprise Station", "tritachyon");
		//initStationCargo(station);
		
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
	
	/*
	private void initStationCargo(SectorEntityToken station) {
		CargoAPI cargo = station.getCargo();
		addRandomWeapons(cargo, 5);
		
		cargo.addCrew(CrewXPLevel.VETERAN, 20);
		cargo.addCrew(CrewXPLevel.REGULAR, 500);
		cargo.addMarines(200);
		cargo.addSupplies(1000);
		cargo.addFuel(500);
		
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "conquest_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, "gladius_wing"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, "gladius_wing"));
	}
	
	private void addRandomWeapons(CargoAPI cargo, int count) {
		List weaponIds = Global.getSector().getAllWeaponIds();
		for (int i = 0; i < count; i++) {
			String weaponId = (String) weaponIds.get((int) (weaponIds.size() * Math.random()));
			int quantity = (int)(Math.random() * 4f + 2f);
			cargo.addWeapons(weaponId, quantity);
		}
	}
	*/
	
}
