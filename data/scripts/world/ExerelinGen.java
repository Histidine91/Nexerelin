package data.scripts.world;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import data.scripts.world.exerelin.*;
import data.scripts.plugins.*;

@SuppressWarnings("unchecked")
public class ExerelinGen implements SectorGeneratorPlugin
{
    private boolean isStartSystemChosen = false;

	public void generate(SectorAPI sector)
	{
        System.out.println("Starting generation...");

		// build systems
		for(int i = 0; i < ExerelinData.getInstance().numSystems; i ++)
			buildSystem(sector);

        new Exerelin().generate(sector);

        sector.registerPlugin(new CoreCampaignPluginImpl());
        sector.registerPlugin(new ExerelinCoreCampaignPlugin());
	}

	public void buildSystem(SectorAPI sector)
	{
		String[] possibleSystemNames = new String[]{"Exerelin", "Askar", "Garil", "Yaerol", "Plagris", "Marot", "Caxort", "Laret", "Narbil", "Karit", "Raestal", "Bemortis", "Xanador", "Tralor", "Exoral", "Oldat", "Pirata", "Zamaror", "Servator", "Bavartis", "Valore", "Charbor", "Dresnen", "Firort", "Haidu", "Jira", "Wesmon", "Uxor"};

        // Create star system from next available name
        StarSystemAPI system = sector.createStarSystem(possibleSystemNames[sector.getStarSystems().size()]);

        // Position star system randomely in hyperspace
        int maxSectorSize = ExerelinData.getInstance().maxSectorSize;
        system.getLocation().set(ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));

        if((ExerelinData.getInstance().numSystems == sector.getStarSystems().size()
                || ExerelinUtils.getRandomInRange(0,2) == 0)
                && !isStartSystemChosen)
        {
            sector.setCurrentLocation(system);
            sector.setRespawnLocation(system);
            sector.getRespawnCoordinates().set(-2500, -3500);
            isStartSystemChosen = true;
        }

        // Set star/light colour/background
        SectorEntityToken star;
        if(ExerelinUtils.getRandomInRange(0,1) == 0)
        {
            star = system.initStar("star_yellow", Color.yellow, 500f);
            //system.setLightColor(new Color(255, 180, 180));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
            else
                system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        }
        else
        {
            star = system.initStar("star_red", Color.red, 900f);
            system.setLightColor(new Color(255, 180, 180));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/backgrounds/background3.jpg");
            else
                system.setBackgroundTextureFilename("graphics/backgrounds/background1.jpg");
        }


        // Build lists of possbile planet types, names and moon types
		String[] possiblePlanetTypes = new String[]	{"desert", "jungle", "gas_giant", "ice_giant", "terran", "arid", "water"};
		String[] possiblePlanetNames = new String[]	{"Baresh", "Zaril", "Vardu", "Drewler", "Trilar", "Polres", "Laret", "Erilatir", "Nambor", "Zat", "Raqueler", "Garret", "Carashil", "Qwerty", "Tyrian", "Savarra", "Yar", "Tyrel", "Tywin", "Arya", "Sword", "Centuri", "Heaven", "Hell", "Sanctuary", "Hyperion", "Zaphod", "Vagar", "Green", "Blond", "Gabrielle", "Masset", "Effecer", "Gunsa", "Patiota", "Rayma", "Origea", "Litsoa", "Bimo", "Plasert", "Pizzart", "Shaper", "Coruscent", "Hoth", "Gibraltar"};
		String[] possibleMoonTypes = new String[]	{"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice"};

		// Build base planets
		int numBasePlanets = ExerelinUtils.getRandomInRange(3, ExerelinData.getInstance().maxPlanets);
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

            // Assign system name to planet as a prefix
            name = system.getStar().getName() + "-" + name;

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

			// 50% Chance to build moons around planet
			if(ExerelinUtils.getRandomInRange(0, 1) == 1)
			{
				// Build moons
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
					int moonRadius = ExerelinUtils.getRandomInRange(50, 100);
					orbitDays = distance / 16 * ExerelinUtils.getRandomInRange(1, 3);
					system.addPlanet(newPlanet, name + " " + ext, moonType, angle, moonRadius, distance, orbitDays);
				}
			}

            // 20% chance of rings around planet / 50% chance if a gas giant
            if(ExerelinUtils.getRandomInRange(0,4) == 0 || (name.contains("Gaseous") && ExerelinUtils.getRandomInRange(0,1) == 0))
            {
                int ringType = ExerelinUtils.getRandomInRange(0,3);

                if(ringType == 0)
                {
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*2, 40f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*2, 60f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*2, 80f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, (int)(radius*2.5), 80f);
                }
                else if (ringType == 1)
                {
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 2, Color.white, 256f, radius*3, 70f);
                    //system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*2.5), 90f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*3.5), 110f);
                }
                else if (ringType == 2)
                {
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, radius*3, 70f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*3), 90f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 3, Color.white, 256f, (int)(radius*3), 110f);
                }
                else if (ringType == 3)
                {
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, radius*2, 50f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, radius*2, 70f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 0, Color.white, 256f, radius*2, 80f);
                    system.addRingBand(newPlanet, "misc", "rings1", 256f, 1, Color.white, 256f, (int)(radius*2.5), 90f);
                }
            }
		}


		// Build asteroid belts
		List planets = system.getPlanets();
		int numAsteroidBelts = ExerelinUtils.getRandomInRange(0, ExerelinData.getInstance().maxAsteroidBelts);
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
		int numStation = ExerelinUtils.getRandomInRange(1, Math.min(ExerelinData.getInstance().maxStations, numBasePlanets*2));
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


        if(ExerelinData.getInstance().numSystems > 1)
        {
            JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("Jump Point Alpha");
            OrbitAPI orbit = Global.getFactory().createCircularOrbit(system.createToken(0,0), 0f, 1200, 120);
            jumpPoint.setOrbit(orbit);
            //jumpPoint.setRelatedPlanet(c2);

            jumpPoint.setStandardWormholeToHyperspaceVisual();
            system.addEntity(jumpPoint);

            system.autogenerateHyperspaceJumpPoints(true, true);
        }
	}
}