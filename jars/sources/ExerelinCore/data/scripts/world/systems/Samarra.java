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

public class Samarra {

	public void generate(SectorAPI sector) {
		StarSystemAPI system = sector.createStarSystem("Samarra");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI samarra_star = system.initStar("samarra", // unique id for this star 
										 "star_orange", // id in planets.json
										 400f, 		// radius (in pixels at default zoom)
										 500); // corona radius, from star edge
		system.setLightColor(new Color(255, 235, 205)); // light color in entire system, affects all entities
	
	// Tigra Ring
		system.addAsteroidBelt(samarra_star, 100, 3000, 1000, 100, 190, Terrain.ASTEROID_BELT, "Tigra Ring");
		system.addRingBand(samarra_star, "misc", "rings1", 256f, 2, Color.white, 256f, 3000, 201f, null, null);
		system.addRingBand(samarra_star, "misc", "rings2", 256f, 2, Color.white, 256f, 3100, 225f, null, null);
		
		SectorEntityToken tigra_city = system.addCustomEntity("tigra_city", "Tigra City", "station_side00", "hegemony");
		tigra_city.setCircularOrbitPointingDown(system.getEntityById("samarra"), 270, 3020, 185);		
		tigra_city.setCustomDescriptionId("station_tigra_city");
		tigra_city.setInteractionImage("illustrations", "hound_hangar");
		
	// Eventide
		PlanetAPI samarra1 = system.addPlanet("eventide", samarra_star, "Eventide", "terran-eccentric", 30, 150, 4000, 200);
		samarra1.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
		samarra1.getSpec().setGlowColor(new Color(255,255,255,255));
		samarra1.getSpec().setUseReverseLightForGlow(true);
		samarra1.getSpec().setRotation(0f);
		samarra1.applySpecChanges();
		samarra1.setCustomDescriptionId("planet_eventide");
		
			// Eventide mirror system 
			SectorEntityToken eventide_mirror1 = system.addCustomEntity("eventide_mirror1", "Eventide Stellar Mirror Alpha", "stellar_mirror", "hegemony");
			SectorEntityToken eventide_mirror2 = system.addCustomEntity("eventide_mirror2", "Eventide Stellar Mirror Beta", "stellar_mirror", "hegemony");	
			SectorEntityToken eventide_mirror3 = system.addCustomEntity("eventide_mirror3", "Eventide Stellar Mirror Gamma", "stellar_mirror", "hegemony");
			SectorEntityToken eventide_mirror4 = system.addCustomEntity("eventide_mirror4", "Eventide Stellar Mirror Delta", "stellar_mirror", "hegemony");
			SectorEntityToken eventide_mirror5 = system.addCustomEntity("eventide_mirror5", "Eventide Stellar Mirror Epsilon", "stellar_mirror", "hegemony");
			eventide_mirror1.setCircularOrbitPointingDown(system.getEntityById("eventide"), 30 -60, 350, 200);
			eventide_mirror2.setCircularOrbitPointingDown(system.getEntityById("eventide"), 30 -30, 350, 200);	
			eventide_mirror3.setCircularOrbitPointingDown(system.getEntityById("eventide"), 30 + 0, 350, 200);	
			eventide_mirror4.setCircularOrbitPointingDown(system.getEntityById("eventide"), 30 + 30, 350, 200);	
			eventide_mirror5.setCircularOrbitPointingDown(system.getEntityById("eventide"), 30 + 60, 350, 200);		
			eventide_mirror1.setCustomDescriptionId("stellar_mirror");
			eventide_mirror2.setCustomDescriptionId("stellar_mirror");
			eventide_mirror3.setCustomDescriptionId("stellar_mirror");
			eventide_mirror4.setCustomDescriptionId("stellar_mirror");
			eventide_mirror5.setCustomDescriptionId("stellar_mirror");
			
			PlanetAPI samarra1a = system.addPlanet("lumen", samarra1, "Lumen", "barren-bombarded", 30, 25, 600, 26);
			
			// Samarra Relay - L5 (behind)
			SectorEntityToken samarra_relay = system.addCustomEntity("samarra_relay", // unique id
					 "Samarra Relay", // name - if null, defaultName from custom_entities.json will be used
					 "comm_relay", // type of object, defined in custom_entities.json
					 "hegemony"); // faction
			samarra_relay.setCircularOrbit( system.getEntityById("samarra"), 30 - 60, 4000, 200);
	
			// Samarra Jump - L4 (ahead)
			JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("samarra_jump_point_alpha", "Samarra Jump Point");
			OrbitAPI orbit = Global.getFactory().createCircularOrbit(samarra_star,  30 + 60, 4000, 200);
			jumpPoint.setOrbit(orbit);
			jumpPoint.setRelatedPlanet(samarra1);
			jumpPoint.setStandardWormholeToHyperspaceVisual();
			system.addEntity(jumpPoint);
		
		// Samarra Gate
		SectorEntityToken samarra_gate = system.addCustomEntity("samarra_gate", // unique id
				 "Samarra Gate", // name - if null, defaultName from custom_entities.json will be used
				 "inactive_gate", // type of object, defined in custom_entities.json
				 null); // faction
		samarra_gate.setCircularOrbit(samarra_star, 210, 4250, 200);
		
	// Typhon System
		PlanetAPI samarra2 = system.addPlanet("typhon", samarra_star, "Typon", "gas_giant", 60, 350, 7000, 500);
		samarra2.getSpec().setPlanetColor(new Color(250,180,120,255));
		samarra2.getSpec().setCloudColor(new Color(250,180,120,150));
		samarra2.getSpec().setAtmosphereColor(new Color(250,180,120,150));
		samarra2.applySpecChanges();
		
			PlanetAPI samarra2a = system.addPlanet("chimera", samarra2, "Chimera", "toxic", 20, 50, 500, 12);
			PlanetAPI samarra2b = system.addPlanet("ladon", samarra2, "Ladon", "barren-bombarded", 40, 30, 620, 16);
			
			system.addRingBand(samarra2, "misc", "rings1", 256f, 1, Color.white, 256f, 850, 30f, Terrain.RING, null);
			system.addRingBand(samarra2, "misc", "rings1", 256f, 2, Color.white, 256f, 975, 33f, Terrain.RING, null);
			
			PlanetAPI samarra2c = system.addPlanet("orthrus", samarra2, "Orthrus", "rocky_ice", 40, 70, 1575, 41);
			// Orthrus Relay - L5 (behind)
			SectorEntityToken orthrus_relay = system.addCustomEntity("orthrus_relay", // unique id
					 "Orthrus Relay", // name - if null, defaultName from custom_entities.json will be used
					 "comm_relay", // type of object, defined in custom_entities.json
					 "independent"); // faction
			orthrus_relay.setCircularOrbit( samarra2, 40 -60, 1575, 41);
			
			PlanetAPI samarra3d = system.addPlanet("sphinx", samarra2, "Sphinx", "barren", 50, 60, 1800, 56);

			// Typhon trojans
			SectorEntityToken typhonL4 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						500f, // min radius
						700f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Typhon L4 Asteroids")); // null for default name
			
			SectorEntityToken typhonL5 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						500f, // min radius
						700f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Typhon L5 Asteroids")); // null for default name
			
			typhonL4.setCircularOrbit(samarra_star, 60 + 60, 7000, 500);
			typhonL5.setCircularOrbit(samarra_star, 60 - 60, 7000, 500);
			
		// generates hyperspace destinations for in-system jump points
		system.autogenerateHyperspaceJumpPoints(true, true);
		
	}
}
