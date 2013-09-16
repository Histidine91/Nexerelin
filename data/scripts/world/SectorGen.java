package data.scripts.world;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;


public class SectorGen implements SectorGeneratorPlugin {

	public void generate(SectorAPI sector) {
		//ClassLoader cl = Global.getSettings().getScriptClassLoader();
		
		
		//StarSystemAPI system = sector.createStarSystem("Corvus");
		//system.getLocation().set(-100000, -100000);
		/*system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		
		//sector.setCurrentLocation(system);
		sector.setRespawnLocation(system);
		sector.getRespawnCoordinates().set(-2500, -3500);
		
		PlanetAPI star = system.initStar("star_yellow", 500f);
//		star.getSpec().setPlanetColor(new Color(255, 100, 100));
//		star.getSpec().setAtmosphereColor(new Color(255, 100, 100));
//		star.getSpec().setIconColor(new Color(255, 100, 100));
//		star.getSpec().setCloudColor(new Color(255, 100, 100));
//		star.applySpecChanges();
//		
//		system.setLightColor(new Color(255, 100, 100));
		
		
		SectorEntityToken corvusI = system.addPlanet(star, "Corvus I", "desert", 55, 150, 3000, 100);
		PlanetAPI corvusII = system.addPlanet(star, "Corvus II", "jungle", 235, 200, 4500, 200);
		
//		corvusII.getSpec().setPlanetColor(new Color(0,0,255));
//		corvusII.applySpecChanges();
		
		
		
		system.addAsteroidBelt(star, 500, 5500, 1000, 150, 300);
		
		SectorEntityToken corvusIII = system.addPlanet(star, "Corvus III", "gas_giant", 200, 300, 7500, 400);
		SectorEntityToken corvusIIIA = system.addPlanet(corvusIII, "Corvus IIIA", "cryovolcanic", 235, 120, 800, 20);
		system.addAsteroidBelt(corvusIII, 50, 1000, 200, 10, 45);
		SectorEntityToken corvusIIIB = system.addPlanet(corvusIII, "Corvus IIIB", "barren", 235, 100, 1300, 60);
		
		SectorEntityToken corvusIV = system.addPlanet(star, "Corvus IV", "barren", 0, 100, 10000, 700);
		SectorEntityToken corvusV = system.addPlanet(star, "Corvus V", "frozen", 330, 175, 12000, 500);
		
		//initFactionRelationships(sector);
		
		//new Askonia().generate(sector);
		
		//new Corvus().generate(sector);
		
//		PirateSpawnPoint pirateSpawn = new PirateSpawnPoint(sector, sector.getHyperspace(), 1, 15, system.getHyperspaceAnchor());
//		system.addSpawnPoint(pirateSpawn);
//		for (int i = 0; i < 2; i++) {
//			pirateSpawn.spawnFleet();
//		}
        */
        //system.autogenerateHyperspaceJumpPoints(true, true);
		
		//sector.registerPlugin(new CoreCampaignPluginImpl());
	}
	
	private void initFactionRelationships(SectorAPI sector) {
		FactionAPI hegemony = sector.getFaction("hegemony");
		FactionAPI tritachyon = sector.getFaction("tritachyon");
		FactionAPI pirates = sector.getFaction("pirates");
		FactionAPI independent = sector.getFaction("independent");
		FactionAPI player = sector.getFaction("player");
		
		player.setRelationship(hegemony.getId(), 0);
		player.setRelationship(tritachyon.getId(), 0);
		player.setRelationship(pirates.getId(), -1);
		player.setRelationship(independent.getId(), 0);
		

		hegemony.setRelationship(tritachyon.getId(), -1);
		hegemony.setRelationship(pirates.getId(), -1);
		
		tritachyon.setRelationship(pirates.getId(), -1);
		tritachyon.setRelationship(independent.getId(), -1);
		
		pirates.setRelationship(independent.getId(), -1);
		
//		independent.setRelationship(hegemony.getId(), 0);
//		independent.setRelationship(tritachyon.getId(), 0);
//		independent.setRelationship(pirates.getId(), 0);
//		independent.setRelationship(independent.getId(), 0);
//		independent.setRelationship(player.getId(), 0);
		
	}
}