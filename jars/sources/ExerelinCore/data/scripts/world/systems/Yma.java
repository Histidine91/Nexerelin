package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain.RingParams;

public class Yma {

	public void generate(SectorAPI sector) {
		
		StarSystemAPI system = sector.createStarSystem("Yma");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI yma_star = system.initStar("yma", // unique id for this star 
										    "star_blue",  // id in planets.json
										    900f, 		  // radius (in pixels at default zoom)
										    500); // corona radius, from star edge
		system.setLightColor(new Color(210, 240, 250)); // light color in entire system, affects all entities
		
		PlanetAPI yma1 = system.addPlanet("huascar", yma_star, "Huascar", "lava", 0, 120, 2000, 100);
		
		// Inner Asteroids
		system.addRingBand(yma_star, "misc", "rings1", 256f, 2, Color.white, 256f, 3700, 175f, null, null);
		system.addAsteroidBelt(yma_star, 75, 3700, 256, 150, 200, Terrain.ASTEROID_BELT, null);
		
		
		PlanetAPI yma2 = system.addPlanet("hanan_pacha", yma_star, "Hanan Pacha", "irradiated", 180, 200, 4400, 330);
		yma2.getSpec().setPlanetColor(new Color(220,245,255,255));
		yma2.getSpec().setAtmosphereColor(new Color(150,120,100,250));
		yma2.getSpec().setCloudColor(new Color(150,120,120,150));
		//yma2.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "barren"));
		yma2.setCustomDescriptionId("planet_hanan_pacha");
		
		PlanetAPI yma2a = system.addPlanet("killa", yma2, "Killa", "barren-bombarded", 90, 50, 380, 16);
		yma2a.setCustomDescriptionId("planet_killa");
		
		
		// Outer asteroids
		
		system.addRingBand(yma_star, "misc", "rings1", 256f, 0, Color.white, 256f, 5100, 475f, null, null);
		system.addAsteroidBelt(yma_star, 100, 5100, 256, 450, 500, Terrain.ASTEROID_BELT, null);
		
		PlanetAPI yma3 = system.addPlanet("chupi_orco", yma_star, "Chupi Orco", "gas_giant", 45, 300, 6800, 450);
		yma3.getSpec().setPlanetColor(new Color(200,235,245,255));
		yma3.getSpec().setAtmosphereColor(new Color(210,240,250,250));
		yma3.getSpec().setCloudColor(new Color(220,250,240,200));
		yma3.getSpec().setPitch(-22f);
		yma3.getSpec().setTilt(-9f);
		yma3.applySpecChanges();
		
			// Chupi Orco Siphon Project
			SectorEntityToken neutralStation = system.addCustomEntity("yma_abandoned_station", "Abandoned Siphon Station", "station_side05", "neutral");
			neutralStation.setCircularOrbitPointingDown(system.getEntityById("chupi_orco"), 45, 360, 50);		
			neutralStation.setCustomDescriptionId("station_chupi_orco");
			neutralStation.setInteractionImage("illustrations", "abandoned_station3");
			
			neutralStation.getMemory().set("$abandonedStation", true);
			MarketAPI market = Global.getFactory().createMarket("yma_abandoned_station_market", neutralStation.getName(), 0);
			market.setPrimaryEntity(neutralStation);
			market.setFactionId(neutralStation.getFaction().getId());
			market.addCondition(Conditions.ABANDONED_STATION);
			market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
			((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);
			neutralStation.setMarket(market);
	
			// Chupi Orco rings
			system.addRingBand(yma3, "misc", "rings1", 256f, 2, Color.white, 256f, 600, 31f);
			system.addRingBand(yma3, "misc", "rings1", 256f, 3, Color.white, 256f, 750, 35f);
			
			// add one ring that covers all of the above
			SectorEntityToken ring = system.addTerrain(Terrain.RING, new RingParams(150 + 256, 675, null, null));
			ring.setCircularOrbit(yma3, 0, 0, 100);
		
			PlanetAPI yma3a = system.addPlanet("viscacha", yma3, "Viscacha", "toxic_cold", 0, 70, 1000, 29);
			yma3a.getSpec().setPlanetColor(new Color(255,210,170,255));
			yma3a.applySpecChanges();
			yma3a.setCustomDescriptionId("planet_viscacha");
		
			// Chupi Orco trojans
			SectorEntityToken chupi_orcoL4 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						500f, // min radius
						700f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Chupi Orco L4 Asteroids")); // null for default name
			
			SectorEntityToken chupi_orcoL5 = system.addTerrain(Terrain.ASTEROID_FIELD,
					new AsteroidFieldParams(
						500f, // min radius
						700f, // max radius
						20, // min asteroid count
						30, // max asteroid count
						4f, // min asteroid radius 
						16f, // max asteroid radius
						"Chupi Orco L5 Asteroids")); // null for default name
			
			chupi_orcoL4.setCircularOrbit(yma_star, 45 + 60, 6800, 450);
			chupi_orcoL5.setCircularOrbit(yma_star, 45 - 60, 6800, 450);
			
			// Viscacha Jumppoint
			JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("viscacha_jump", "Viscacha Jumppoint");
			jumpPoint.setCircularOrbit( system.getEntityById("yma"), 45+60, 6800, 450);
			jumpPoint.setRelatedPlanet(yma3a);
			system.addEntity(jumpPoint);
			
			// Yma Gate
			SectorEntityToken gate = system.addCustomEntity("yma_gate", // unique id
					 "Yma Gate", // name - if null, defaultName from custom_entities.json will be used
					 "inactive_gate", // type of object, defined in custom_entities.json
					 null); // faction
			gate.setCircularOrbit(system.getEntityById("yma"), 345, 6800, 450);
		
	// YMA B
		PlanetAPI yma_star_b = system.addPlanet("Yma B", yma_star, "Yma B", "star_white", 270, 400, 11500, 800);
		
		PlanetAPI yma_b1 = system.addPlanet("qaras", yma_star_b, "Qaras", "tundra", 0, 140, 2000, 100);
		yma_b1.getSpec().setPitch(180f);
		yma_b1.getSpec().setTilt(-30f);
		yma_b1.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
		yma_b1.getSpec().setGlowColor(new Color(240,255,250,64));
		yma_b1.getSpec().setUseReverseLightForGlow(true);
		yma_b1.applySpecChanges();
		yma_b1.setCustomDescriptionId("planet_qaras");
		
			// Qaras mirror system 
			SectorEntityToken qaras_mirror1 = system.addCustomEntity("qaras_mirror1", "Qaras Stellar Mirror", "stellar_mirror", "tritachyon");
			qaras_mirror1.setCircularOrbitPointingDown(system.getEntityById("qaras"), 0, 220, 40);		
			qaras_mirror1.setCustomDescriptionId("stellar_mirror");
	
		system.addRingBand(yma_b1, "misc", "rings1", 256f, 1, Color.white, 256f, 400, 30f, Terrain.RING, null);
		
		// Qaras Relay - L5 (behind)
		SectorEntityToken qaras_relay = system.addCustomEntity("qaras_relay", // unique id
				 "Qaras Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "pirates"); // faction
		qaras_relay.setCircularOrbit( yma_star_b, 0 - 60, 2000, 100);
		
		// Yma B Jumppoint
		JumpPointAPI jumpPoint2 = Global.getFactory().createJumpPoint("viscacha_jump", "Yma B Jumppoint");
		jumpPoint2.setCircularOrbit( system.getEntityById("yma"), 270+60, 11500, 800);
		jumpPoint2.setRelatedPlanet(yma_b1);
		system.addEntity(jumpPoint2);
		
		system.autogenerateHyperspaceJumpPoints(true, true);
	}
}
