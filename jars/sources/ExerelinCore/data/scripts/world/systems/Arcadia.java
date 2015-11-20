package data.scripts.world.systems;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;

public class Arcadia {

	public void generate(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("Arcadia");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI star = system.initStar("arcadia", // unique id for star
										 "star_white", // id in planets.json
										 180f,		// radius (in pixels at default zoom)
										 300); // corona radius, from star edge
		
		system.setLightColor(new Color(200, 200, 200)); // light color in entire system, affects all entities
		star.setCustomDescriptionId("star_white_dwarf");
		
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
		
		
		
		PlanetAPI arcadia1 = system.addPlanet("nomios", star, "Nomios", "frozen", 90, 130, 3000, 100);
		//arcadia1.setCustomDescriptionId("planet_nomios");
		
		PlanetAPI arcadia2 = system.addPlanet("syrinx", star, "Syrinx", "ice_giant", 180, 300, 6000, 200);
		arcadia2.setCustomDescriptionId("planet_syrinx");
		
		SectorEntityToken relay = system.addCustomEntity("syrinx_relay", // unique id
				 "Syrinx Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "hegemony"); // faction
		relay.setCircularOrbit(system.getEntityById("syrinx"), 90, 1200, 45);
		
		// ship-wrecking & industrial stuff
		PlanetAPI arcadia2a = system.addPlanet("agreus", arcadia2, "Agreus", "barren", 0, 130, 1600, 50);
		arcadia2a.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "barren02"));
		arcadia2a.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "sindria"));
		arcadia2a.getSpec().setGlowColor(new Color(235,245,255,255));
		arcadia2a.getSpec().setUseReverseLightForGlow(true);
		arcadia2a.applySpecChanges();
		arcadia2a.setInteractionImage("illustrations", "industrial_megafacility"); // TODO something better for this.
		arcadia2a.setCustomDescriptionId("planet_agreus");
		
		system.addRingBand(arcadia2, "misc", "rings2", 256f, 2, new Color(170,210,255,255), 256f, 800, 40f, null, null);
		system.addAsteroidBelt(arcadia2, 20, 1000, 128, 40, 80, Terrain.ASTEROID_BELT, null);
		
//		SectorEntityToken arc_station = system.addOrbitalStation("arcadia_station", arcadia2, 45, 750, 30, "Citadel Arcadia", "hegemony");
//		arc_station.setCustomDescriptionId("station_arcadia"); 
//		initStationCargo(arc_station); 
		
		SectorEntityToken arc_station = system.addCustomEntity("arcadia_station", "Citadel Arcadia", "station_side02", "hegemony");
		arc_station.setCircularOrbitPointingDown(system.getEntityById("syrinx"), 45, 750, 30);		
		arc_station.setCustomDescriptionId("station_arcadia");
		arc_station.setInteractionImage("illustrations", "hound_hangar");
		
		/*
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
		
		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("syrinx_passage","Syrinx Passage");
		OrbitAPI orbit = Global.getFactory().createCircularOrbit(arcadia2, 0, 2200, 100);
		jumpPoint.setOrbit(orbit);	
		jumpPoint.setRelatedPlanet(arcadia2a);
		jumpPoint.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPoint);
		
		
		// example of using custom visuals below
//		a1.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "hull_breach", 800, 800));
//		jumpPoint.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "space_wreckage", 1200, 1200));
//		station.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "cargo_loading", 1200, 1200));
		
		// generates hyperspace destinations for in-system jump points
		system.autogenerateHyperspaceJumpPoints(true, true);
		
		//system.addScript(new IndependentTraderSpawnPoint(sector, hyper, 1, 10, hyper.createToken(-6000, 2000), station));
	}
		
	private void initStationCargo(SectorEntityToken station) {
		CargoAPI cargo = station.getCargo();
		addRandomWeapons(cargo, 5);
		
		cargo.addCrew(CrewXPLevel.VETERAN, 20);
		cargo.addCrew(CrewXPLevel.REGULAR, 500);
		cargo.addMarines(200);
		cargo.addSupplies(1000);
		cargo.addFuel(500);
		
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
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
	
}
