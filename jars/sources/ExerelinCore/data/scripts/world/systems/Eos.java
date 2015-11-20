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
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.util.Misc;

public class Eos {

	public void generate(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("Eos Exodus");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background6.jpg");
		
		SectorEntityToken eos_nebula = Misc.addNebulaFromPNG("data/campaign/terrain/eos_nebula.png",
				  0, 0, // center of nebula
				  system, // location to add to
				  "terrain", "nebula_amber", // "nebula_blue", // texture to use, uses xxx_map for map
				  4, 4); // number of cells in texture
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI star = system.initStar("eos",
										 "star_white", // id in planets.json
										 750f, 		// radius (in pixels at default zoom)
										 500); // corona radius, from star edge
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
		PlanetAPI eos1 = system.addPlanet("phaosphoros", star, "Phaosphoros", "gas_giant", 240, 300, 2300, 40);
		eos1.getSpec().setAtmosphereColor(new Color(255,245,200,220));
		eos1.getSpec().setPlanetColor(new Color(245,250,255,255));
		eos1.applySpecChanges();
		eos1.setCustomDescriptionId("hot_gas_giant");
		eos1.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "aurorae"));
		eos1.getSpec().setGlowColor(new Color(245,50,20,100));
		eos1.getSpec().setUseReverseLightForGlow(true);
		eos1.getSpec().setAtmosphereThickness(0.5f);
		eos1.getSpec().setCloudRotation( 10f );
		eos1.getSpec().setAtmosphereColor(new Color(255,150,50,205));
			
			PlanetAPI eos1a = system.addPlanet("lucifer", eos1, "Lucifer", "lava", 0, 60, 650, 18);
			system.addAsteroidBelt(eos1, 30, 500, 100, 15, 25, Terrain.ASTEROID_BELT, null);
			
			// Phaosphoros trojans
			SectorEntityToken phaosphorosL4 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						300f, // min radius
						500f, // max radius
						10, // min asteroid count
						15, // max asteroid count
						4f, // min asteroid radius 
						12f, // max asteroid radius
						"Cherubim Asteroids")); // null for default name
			
			SectorEntityToken phaosphorosL5 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						300f, // min radius
						500f, // max radius
						10, // min asteroid count
						15, // max asteroid count
						4f, // min asteroid radius 
						12f, // max asteroid radius
						"Seraphim Asteroids")); // null for default name
			
			phaosphorosL4.setCircularOrbit(star, 240 + 60, 2300, 40);
			phaosphorosL5.setCircularOrbit(star, 240 - 60, 2300, 40);
			
		PlanetAPI eos2 = system.addPlanet("tartessus", star, "Tartessus", "arid", 200, 180, 4400, 120);
		eos2.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "sindria"));
		eos2.getSpec().setGlowColor(new Color(245,255,250,255));
		eos2.getSpec().setUseReverseLightForGlow(true);
		eos2.applySpecChanges();
		eos2.setCustomDescriptionId("planet_tartessus");
		
			system.addRingBand(eos2, "misc", "rings2", 256f, 1, new Color(245,235,255,150), 128f, 380, 30f, Terrain.RING, "The Grace of Tartessus"); 
			// 256f
		
			PlanetAPI eos2a = system.addPlanet("baetis", eos2, "Baetis", "barren-bombarded", 0, 35, 500, 30);
			eos2a.setCustomDescriptionId("planet_baetis");
		
			/*JumpPointAPI eos2JumpPoint = Global.getFactory().createJumpPoint("paladins_bridge", "Paladins' Bridge");
			OrbitAPI orbit = Global.getFactory().createCircularOrbit(eos2, 0, 750, 30);
			eos2JumpPoint.setOrbit(orbit);
			eos2JumpPoint.setRelatedPlanet(eos2);
			eos2JumpPoint.setStandardWormholeToHyperspaceVisual();
			system.addEntity(eos2JumpPoint);*/
			
			// Tartessus Jumppoint - Tartessus L5 (behind)
			JumpPointAPI eos2JumpPoint = Global.getFactory().createJumpPoint("paladins_bridge", "Paladins' Bridge");
			eos2JumpPoint.setCircularOrbit(system.getEntityById("eos"), 200-60, 4400, 120);
			eos2JumpPoint.setRelatedPlanet(eos2);
			
			eos2JumpPoint.setStandardWormholeToHyperspaceVisual();
			system.addEntity(eos2JumpPoint);
			
			// Eos Exodus Gate - Tartessus L4 (ahead)
			SectorEntityToken gate = system.addCustomEntity("eos_exodus_gate", // unique id
					 "Eos Exodus Gate", // name - if null, defaultName from custom_entities.json will be used
					 "inactive_gate", // type of object, defined in custom_entities.json
					 null); // faction
			gate.setCircularOrbit(system.getEntityById("eos"), 200+60, 4400, 120);
		
			
		// Asteroids - "The Pilgrims"
		system.addRingBand(star, "misc", "rings1", 256f, 2, Color.white, 256f, 6180, 205f, null, null);
		system.addAsteroidBelt(star, 150, 6200, 250, 150, 250, Terrain.ASTEROID_BELT, "The Pilgrims");
			
		PlanetAPI eos3 = system.addPlanet("hesperus", star, "Hesperus", "rocky_ice", 0, 150, 7800, 200);
		eos3.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "sindria"));
		eos3.getSpec().setGlowColor(new Color(245,255,250,255));
		eos3.getSpec().setUseReverseLightForGlow(true);
		eos3.applySpecChanges();
		eos3.setCustomDescriptionId("planet_hesperus");
		
			PlanetAPI eos3a = system.addPlanet("ceyx", eos3, "Ceyx", "barren-bombarded", 0, 20, 480, 16);
			eos3a.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "barren"));
			eos3a.getSpec().setGlowColor(new Color(200,230,255,200));
			eos3a.getSpec().setUseReverseLightForGlow(true);
			eos3a.applySpecChanges();
			eos3a.setCustomDescriptionId("planet_ceyx");
			
			PlanetAPI eos3b = system.addPlanet("daedaleon", eos3, "Daedaleon", "irradiated", 0, 50, 620, 33);
			eos3b.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
			eos3b.getSpec().setGlowColor(new Color(255,55,250,200));
			eos3b.getSpec().setUseReverseLightForGlow(true);
			eos3b.applySpecChanges();
			eos3b.setCustomDescriptionId("planet_daedaleon");
			SectorEntityToken eos3b_field = system.addTerrain(Terrain.MAGNETIC_FIELD,
					new MagneticFieldParams(200f, // terrain effect band width 
					160f, // terrain effect middle radius
					eos3b, // entity that it's around
					60f, // visual band start
					260f, // visual band end
					new Color(50, 20, 100, 50), // base color
					0.25f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
					new Color(90, 180, 140),
					new Color(130, 145, 190),
					new Color(165, 110, 225), 
					new Color(95, 55, 240), 
					new Color(45, 0, 250),
					new Color(20, 0, 240),
					new Color(10, 0, 150)));
			eos3b_field.setCircularOrbit(eos3b, 0, 0, 100);
		

		SectorEntityToken relay = system.addCustomEntity("hesperus_relay", // unique id
				 "Hesperus Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "knights_of_ludd"); // faction
		relay.setCircularOrbit(system.getEntityById("hesperus"), 90, 1000, 45);

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
