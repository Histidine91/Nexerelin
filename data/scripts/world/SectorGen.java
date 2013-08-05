package data.scripts.world;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import data.scripts.world.exerelin.*;

@SuppressWarnings("unchecked")
public class SectorGen implements SectorGeneratorPlugin
{

	public void generate(SectorAPI sectorAPI)
	{
		// Build initial system
		buildSystem(sectorAPI);

		// build any additional systems
		for(int i = 0; i < ExerelinData.getInstance().numSystems - 1; i ++)
			buildSystem(sectorAPI);


		ExerelinData.getInstance().resetAvailableFactions();

		// Build a sector manager to run things
		SectorManager sectorManager = new SectorManager(sectorAPI);

		// Set starting conditions needed later for saving into the save file
		sectorManager.setPlayerFreeTransfer(ExerelinData.getInstance().playerOwnedStationFreeTransfer);
		sectorManager.setRespawnFactions(ExerelinData.getInstance().respawnFactions);
		sectorManager.setMaxFactions(ExerelinData.getInstance().maxFactionsInExerelinAtOnce);
		sectorManager.setPlayerFactionId(ExerelinData.getInstance().getPlayerFaction());
		sectorManager.setFactionsPossibleInSector(ExerelinData.getInstance().getAvailableFactions(sectorAPI));
		sectorManager.setRespawnWaitMonths(ExerelinData.getInstance().respawnDelay);
		sectorManager.setBuildOmnifactory(ExerelinData.getInstance().omniFacPresent);
		sectorManager.setMaxSystemSize(ExerelinData.getInstance().maxSystemSize);

		// Add to cache
		ExerelinData.getInstance().setSectorManager(sectorManager);

		// Build and add a time mangager
		TimeManager timeManger = new TimeManager();
		timeManger.sectorManagerRef = sectorManager;
		sectorAPI.getStarSystem("Exerelin").addSpawnPoint(timeManger); //TODO - change
	}

	public void buildSystem(SectorAPI sectorAPI)
	{
		String[] possibleSystemNames = new String[]{"Exerelin"};

		StarSystemAPI system = sectorAPI.createStarSystem(possibleSystemNames[sectorAPI.getStarSystems().size()]);
		SectorEntityToken star = system.initStar("star_yellow", Color.white, 500f);

		// Build lists of possbile planet types, names and moon types
		String[] possiblePlanetTypes = new String[]	{"desert", "jungle", "gas_giant", "ice_giant", "terran", "arid"};
		String[] possiblePlanetNames = new String[]	{"Baresh", "Zaril", "Vardu", "Drewler", "Trilar", "Polres", "Laret", "Erilatir", "Nambor", "Zat", "Raqueler", "Garret", "Carashil", "Qwerty", "Tyrian", "Savarra", "Yar", "Tyrel", "Tywin", "Arya", "Sword", "Centuri", "Heaven", "Hell", "Sanctuary", "Hyperion", "Zaphod", "Vagar", "Green", "Blond", "Gabrielle", "Masset", "Effecer", "Gunsa", "Patiota", "Rayma", "Origea", "Litsoa", "Bimo", "Plasert", "Pizzart", "Shaper", "Coruscent", "Hoth", "Gibraltar"};
		String[] possibleMoonTypes = new String[]	{"frozen", "barren", "lava", "toxic", "cryovolcanic"};


		// Build base planets
		int numBasePlanets = ExerelinData.getInstance().numPlanets;
		int distanceStepping = (ExerelinData.getInstance().maxSystemSize-4000)/numBasePlanets;
		Boolean gasPlanetCreated = false;
		for(int i = 0; i < numBasePlanets; i = i + 1)
		{
			String planetType = possiblePlanetTypes[ExerelinUtils.getRandomInRange(0, possiblePlanetTypes.length - 1)];

			String name = "";
			Boolean nameInUse = false;
			while(name.equalsIgnoreCase("") || nameInUse)
			{
				name = possiblePlanetNames[ExerelinUtils.getRandomInRange(0, possiblePlanetNames.length - 1)];
				nameInUse = false;

				List builtPlanets = system.getPlanets();
				for(int k = 0; k < builtPlanets.size(); k = k + 1)
				{
					if(((SectorEntityToken)builtPlanets.get(k)).getFullName().contains(name))
					{
						nameInUse = true;
						break;
					}
				}
			}

			int radius;
			int angle = ExerelinUtils.getRandomInRange(1, 360);
			int distance = 3000 + (distanceStepping * (i  + 1)) + ExerelinUtils.getRandomInRange((distanceStepping/3)*-1, distanceStepping/3);
			int orbitDays = distance / 16 * ExerelinUtils.getRandomInRange(1, 3);
			if(planetType.equalsIgnoreCase("gas_giant") || planetType.equalsIgnoreCase("ice_giant"))
			{
				radius = 350;
				name = name + " Gaseous";
				gasPlanetCreated = true;
			}
			else
				radius = ExerelinUtils.getRandomInRange(150, 250);

			if(!gasPlanetCreated && i == numBasePlanets - 1)
			{
				planetType = "gas_giant";
				radius = 350;
				name = name + " Gaseous";
				gasPlanetCreated = true;
			}

			SectorEntityToken newPlanet = system.addPlanet(star, name, planetType, angle, radius, distance, orbitDays);

			// Chance to build moons around planet
			if(ExerelinUtils.getRandomInRange(0, 1) == 1)
			{
				// Build 0 - 2 moons
				for(int j = 0; j < ExerelinUtils.getRandomInRange(0, ExerelinData.getInstance().maxMoonsPerPlanet - 1); j = j + 1)
				{
					String ext = "";
					if(j == 0)
						ext = "I";
					if(j == 1)
						ext = "II";
					if(j == 2)
						ext = "III";

					String moonType = possibleMoonTypes[ExerelinUtils.getRandomInRange(0, possibleMoonTypes.length - 1)];
					angle = ExerelinUtils.getRandomInRange(1, 360);
					distance = ExerelinUtils.getRandomInRange(650, 1300);
					radius = ExerelinUtils.getRandomInRange(50, 100);
					orbitDays = distance / 16 * ExerelinUtils.getRandomInRange(1, 3);
					system.addPlanet(newPlanet, name + " " + ext, moonType, angle, radius, distance, orbitDays);
				}
			}
		}


		// Build asteroid belts
		List planets = system.getPlanets();
		int numAsteroidBelts = ExerelinData.getInstance().numAsteroidBelts;
		for(int j = 0; j < numAsteroidBelts; j = j + 1)
		{
			SectorEntityToken planet = null;
			while(planet == null)
				planet = (SectorEntityToken)planets.get(ExerelinUtils.getRandomInRange(0, planets.size() - 1));

			float orbitRadius;
			int numAsteroids;

			if (planet.getFullName().contains(" I") || planet.getFullName().contains(" II") || planet.getFullName().contains(" III"))
			{
				orbitRadius = ExerelinUtils.getRandomInRange(250, 350);
				numAsteroids = 1;
			}
			else if(planet.getFullName().contains("Gaseous"))
			{
				orbitRadius = ExerelinUtils.getRandomInRange(700, 900);
				numAsteroids = 2;
			}
			else if (planet.getFullName().contains(system.getStar().getFullName()))
			{
				orbitRadius = ExerelinUtils.getRandomInRange(1000, 8000);
				numAsteroids = 50;
			}
			else
			{
				orbitRadius = ExerelinUtils.getRandomInRange(400, 550);
				numAsteroids = 1;
			}


			float width = ExerelinUtils.getRandomInRange(10, 50);
			float minOrbitDays = ExerelinUtils.getRandomInRange(240, 360);
			float maxOrbitDays = ExerelinUtils.getRandomInRange(360, 480);
			system.addAsteroidBelt(planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
		}

		// Always put an asteroid belt around the sun
		system.addAsteroidBelt(star, 25, ExerelinUtils.getRandomInRange(1000, 8000), ExerelinUtils.getRandomInRange(10, 50), ExerelinUtils.getRandomInRange(240, 360), ExerelinUtils.getRandomInRange(360, 480));

		// Another one if medium system size
		if(ExerelinData.getInstance().maxSystemSize > 15000)
			system.addAsteroidBelt(star, 50, ExerelinUtils.getRandomInRange(15000, 25000), ExerelinUtils.getRandomInRange(50, 100), ExerelinUtils.getRandomInRange(480, 720), ExerelinUtils.getRandomInRange(720, 960));

		// And another one if a large system
		if(ExerelinData.getInstance().maxSystemSize > 30000)
			system.addAsteroidBelt(star, 75, ExerelinUtils.getRandomInRange(15000, 25000), ExerelinUtils.getRandomInRange(100, 150), ExerelinUtils.getRandomInRange(960, 1440), ExerelinUtils.getRandomInRange(1440, 1920));

		// Build a list of possbile station names
		String[] possibleStationNames = new String[] {"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge", "Customs", "Nest", "Port", "Quey", "Terminal", "Exchange", "View", "Wall", "Habitat", "Shipyard", "Backwater"};

		// Build stations
		int numStation = ExerelinData.getInstance().numStations;

		int currentPlanet = 0;
		int k = 0;
		while(k < numStation)
		{
			SectorEntityToken planet = (SectorEntityToken)planets.get(currentPlanet);

			currentPlanet = currentPlanet + 1;

			if(currentPlanet == planets.size())
				currentPlanet = 0;

			if(planet.getFullName().equalsIgnoreCase(system.getStar().getFullName()))
				continue; // Skip sun

			if(ExerelinUtils.getRandomInRange(0,3) == 0)
				continue; // 25% chance to skip this planet

			Boolean nameOK = false;
			String name = "";
			while(!nameOK)
			{
				name = possibleStationNames[ExerelinUtils.getRandomInRange(0, possibleStationNames.length - 1)];
				nameOK = true;
				for(int l = 0; l < system.getOrbitalStations().size(); l++)
				{
					String possibleName = planet.getFullName() + " " + name;
					if(((SectorEntityToken)system.getOrbitalStations().get(l)).getFullName().contains(possibleName))
						nameOK = false;

				}
			}

			int angle = ExerelinUtils.getRandomInRange(1, 360);
			int orbitRadius = 300;
			int orbitDays = ExerelinUtils.getRandomInRange(50, 100);
			if (planet.getFullName().contains(" I") || planet.getFullName().contains(" II") || planet.getFullName().contains(" III"))
				orbitRadius = 200;
			else if(planet.getFullName().contains("Gaseous"))
				orbitRadius = 500;

			SectorEntityToken newStation = system.addOrbitalStation(planet, angle, orbitRadius, orbitDays, planet.getFullName() + " " + name, "abandoned");
			k = k + 1;
		}
	}
}