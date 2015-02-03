package data.scripts.world;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.CoreCampaignPluginImpl;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.events.CoreEventProbabilityManager;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import data.scripts.world.exerelin.*;
import exerelin.plugins.*;
import data.scripts.world.exerelin.ExerelinSetupData;
import data.scripts.world.exerelin.ExerelinMarketConditionPicker;
import exerelin.*;
import exerelin.utilities.ExerelinConfig;

@SuppressWarnings("unchecked")
public class ExerelinSectorGen implements SectorGeneratorPlugin
{
    private boolean isStartSystemChosen = false;

	private String getRandomFaction()
	{
		String[] factionIds = new String[]{"sindrian_diktat", "tritachyon", "luddic_church", "pirates", "hegemony", "independent"};
        return factionIds[ExerelinUtils.getRandomInRange(0, 5)];
	}
	
	private MarketAPI addMarketToEntity(int i, SectorEntityToken entity, String owningFactionId, String planetType, boolean isStation)
	{
		int marketSize = 1;
		if (isStation) marketSize = ExerelinUtils.getRandomInRange(1, 3) + ExerelinUtils.getRandomInRange(1, 3);	// stations are on average smaller
		else marketSize = ExerelinUtils.getRandomInRange(1, 5) + ExerelinUtils.getRandomInRange(1, 5);
        MarketAPI newMarket = Global.getFactory().createMarket(entity.getId() + "_market", entity.getName(), 4);

        newMarket.setPrimaryEntity(entity);
        entity.setMarket(newMarket);
        newMarket.setFactionId(owningFactionId);
		
		// first planet in the system is a regional capital
		// it is always at least size 4 and has a military base
		if (i == 0)
		{
			if (marketSize < 4)
				marketSize = 4;
            newMarket.addCondition("regional_capital");
			newMarket.addCondition("military_base");
            newMarket.addSubmarket(Submarkets.GENERIC_MILITARY);
		}
        newMarket.setSize(marketSize);
        newMarket.setBaseSmugglingStabilityValue(0);
        newMarket.addCondition("population_" + marketSize);

		// planet type conditions
        if (planetType == "jungle") {
            newMarket.addCondition("jungle");

            if(ExerelinUtils.getRandomInRange(0, 3) == 0)
                newMarket.addCondition("orbital_burns");
        }
        if (planetType == "water")
            newMarket.addCondition("water");
        if (planetType == "arid")
            newMarket.addCondition("arid");
        if (planetType == "terran")
            newMarket.addCondition("terran");
        if (planetType == "desert")
            newMarket.addCondition("desert");

        if(marketSize < 4){
            newMarket.addCondition("frontier");
        }
        if(marketSize >=4 && marketSize < 7){
            //newMarket.addCondition("desert");
        }
        if(marketSize >= 7){
            //newMarket.addCondition("desert");
        }
		
		// add random market conditions
        ExerelinMarketConditionPicker picker = new ExerelinMarketConditionPicker();
		picker.AddMarketConditions(newMarket, marketSize, planetType, isStation);

		// add per-faction market conditions
		String factionId = newMarket.getFaction().getId();
        if(factionId == "sindrian_diktat") {
            newMarket.addCondition("urbanized_polity");
        }
        if(factionId == "tritachyon") {
            newMarket.addCondition("free_market");
        }
        if(factionId == "luddic_church") {
            newMarket.addCondition("luddic_majority");
            newMarket.addCondition("cottage_industry");
        }
        if(factionId == "pirates") {
            newMarket.addCondition("free_market");
        }
        if(factionId == "hegemony") {
            newMarket.addCondition("urbanized_polity");
        }
        //if(factionId  == "independent") {
        //    newMarket.addCondition("decivilized");
        //}

        newMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        newMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        newMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        Global.getSector().getEconomy().addMarket(newMarket);
		
		return newMarket;
	}
	
	public void generate(SectorAPI sector)
	{
        System.out.println("Starting generation...");

		// build systems
		for(int i = 0; i < ExerelinSetupData.getInstance().numSystems; i ++)
			buildSystem(sector);

        new Exerelin().generate(sector);

        sector.registerPlugin(new CoreCampaignPluginImpl());
        sector.addScript(new CoreScript());
        sector.addScript(new CoreEventProbabilityManager());
        sector.addScript(new EconomyFleetManager());

        sector.registerPlugin(new ExerelinCoreCampaignPlugin());

        System.out.println("Finished generation...");
	}

	public void buildSystem(SectorAPI sector)
	{
		String[] possibleSystemNames = new String[]{"Exerelin", "Askar", "Garil", "Yaerol", "Plagris", "Marot", "Caxort", "Laret", "Narbil", "Karit", "Raestal", "Bemortis", "Xanador", "Tralor", "Exoral", "Oldat", "Pirata", "Zamaror", "Servator", "Bavartis", "Valore", "Charbor", "Dresnen", "Firort", "Haidu", "Jira", "Wesmon", "Uxor"};

        // Create star system from next available name
        StarSystemAPI system = sector.createStarSystem(possibleSystemNames[sector.getStarSystems().size()]);
	String systemName = system.getName();
        int maxSectorSize = ExerelinSetupData.getInstance().maxSectorSize;

        if((ExerelinSetupData.getInstance().numSystems == sector.getStarSystems().size()
                || ExerelinUtils.getRandomInRange(0,2) == 0)
                && !isStartSystemChosen)
        {
            System.out.println("Setting start location " + systemName);
            sector.setRespawnLocation(system);
            sector.getRespawnCoordinates().set(-2500, -3500);
            //sector.setCurrentLocation(system);
            isStartSystemChosen = true;
        }

        // Set star/light colour/background
        SectorEntityToken star;
	
        int starType = 0;
        if(ExerelinConfig.useMultipleBackgroundsAndStars)
            starType = ExerelinUtils.getRandomInRange(0, 10);
        else
            starType = ExerelinUtils.getRandomInRange(0, 1);

        if(starType == 0)
        {
            star = system.initStar(systemName, "star_yellow", 500f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            //system.setLightColor(new Color(255, 180, 180));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
            else
                system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        }
        else if(starType == 1)
        {
            star = system.initStar(systemName, "star_red", 900f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(255, 180, 180));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/backgrounds/background3.jpg");
            else
                system.setBackgroundTextureFilename("graphics/backgrounds/background1.jpg");
        }
        else if(starType == 2)
        {
            star = system.initStar(systemName, "star_blue", 400f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(135,206,250));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/blue_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/blue_background2.jpg");
        }
        else if(starType == 3)
        {
            star = system.initStar(systemName, "star_white", 300f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            //system.setLightColor(new Color(185,185,240));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/white_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/white_background2.jpg");
        }
        else if(starType == 4)
        {
            star = system.initStar(systemName, "star_orange", 900f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(255,220,0));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/orange_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/orange_background2.jpg");
        }
        else if(starType == 5)
        {
            star = system.initStar(systemName, "star_yellowwhite", 400f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(255,255,224));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
            else
                system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");
        }
        else if(starType == 6)
        {
            star = system.initStar(systemName, "star_bluewhite", 400f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(135,206,250));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/bluewhite_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/bluewhite_background2.jpg");
        }
        else if(starType == 7)
        {
            star = system.initStar(systemName, "star_purple", 700f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(218,112,214));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/purple_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/purple_background2.jpg");
        }
        else if(starType == 8)
        {
            star = system.initStar(systemName, "star_dark", 700f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(105,105,105));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/dark_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/dark_background2.jpg");
        }
        else if(starType == 9)
        {
            star = system.initStar(systemName, "star_green", 600f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(240,255,240));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/green_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/green_background2.jpg");
        }
        else
        {
            star = system.initStar(systemName, "star_greenwhite", 600f, (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize), (float)ExerelinUtils.getRandomInRange(maxSectorSize*-1, maxSectorSize));
            system.setLightColor(new Color(240,255,240));
            if(ExerelinUtils.getRandomInRange(0,1) == 0)
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/greenwhite_background1.jpg");
            else
                system.setBackgroundTextureFilename("graphics/exerelin/backgrounds/greenwhite_background1.jpg");
        }


        // Build lists of possbile planet types, names and moon types
		String[] possiblePlanetTypes = new String[]	{"desert", "jungle", "gas_giant", "ice_giant", "terran", "arid", "water"};
		String[] possiblePlanetNames = new String[]	{"Baresh", "Zaril", "Vardu", "Drewler", "Trilar", "Polres", "Laret", "Erilatir", "Nambor", "Zat", "Raqueler", "Garret", "Carashil", "Qwerty", "Tyrian", "Savarra", "Yar", "Tyrel", "Tywin", "Arya", "Sword", "Centuri", "Heaven", "Hell", "Sanctuary", "Hyperion", "Zaphod", "Vagar", "Green", "Blond", "Gabrielle", "Masset", "Effecer", "Gunsa", "Patiota", "Rayma", "Origea", "Litsoa", "Bimo", "Plasert", "Pizzart", "Shaper", "Coruscent", "Hoth", "Gibraltar"};
		String[] possibleMoonTypes = new String[]	{"frozen", "barren", "lava", "toxic", "cryovolcanic", "rocky_metallic", "rocky_unstable", "rocky_ice"};

		// Build base planets
		int numBasePlanets;
        if(ExerelinSetupData.getInstance().numSystems != 1)
            numBasePlanets = ExerelinUtils.getRandomInRange(ExerelinConfig.minimumPlanets, ExerelinSetupData.getInstance().maxPlanets);
        else
            numBasePlanets = ExerelinSetupData.getInstance().maxPlanets;
		int distanceStepping = (ExerelinSetupData.getInstance().maxSystemSize-4000)/numBasePlanets;
		Boolean gasPlanetCreated = false;
		for(int i = 0; i < numBasePlanets; i = i + 1)
		{
			boolean inhabitable = Math.random() < 0.7;	// lower than it looks since gas giants are considered "inhabitable" at this stage
			String planetType = "";
			if (inhabitable)
				planetType = possiblePlanetTypes[ExerelinUtils.getRandomInRange(0, possiblePlanetTypes.length - 1)];
			else
				planetType = possibleMoonTypes[ExerelinUtils.getRandomInRange(0, possibleMoonTypes.length - 1)];

			String name = "";
			String id = "";
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
			id = name.replace(' ','_');

			float radius;
            float angle = ExerelinUtils.getRandomInRange(1, 360);
            float distance = 3000 + (distanceStepping * (i  + 1)) + ExerelinUtils.getRandomInRange((distanceStepping/3)*-1, distanceStepping/3);
            float orbitDays = distance / 16 * ExerelinUtils.getRandomInRange(1, 3);
			if(planetType.equalsIgnoreCase("gas_giant") || planetType.equalsIgnoreCase("ice_giant"))
			{
				radius = 350;
				name = name + " Gaseous";
				gasPlanetCreated = true;
				inhabitable = false;
			}
			else
				radius = ExerelinUtils.getRandomInRange(150, 250);

			// At least one gas giant per system
			if(!gasPlanetCreated && i == numBasePlanets - 1)
			{
				if (ExerelinUtils.getRandomInRange(0, 1) == 1)	planetType = "gas_giant";
				else planetType = "ice_giant";
				radius = 350;
				name = name + " Gaseous";
				gasPlanetCreated = true;
				inhabitable = false;
			}

			SectorEntityToken newPlanet = system.addPlanet(id, star, name, planetType, angle, radius, distance, orbitDays);

			// 50% Chance to build moons around planet
			if(ExerelinUtils.getRandomInRange(0, 1) == 1)
			{
				// Build moons
				for(int j = 0; j < ExerelinUtils.getRandomInRange(0, ExerelinSetupData.getInstance().maxMoonsPerPlanet - 1); j = j + 1)
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
					float moonRadius = ExerelinUtils.getRandomInRange(50, 100);
					orbitDays = distance / 16 * ExerelinUtils.getRandomInRange(1, 3);
					system.addPlanet(name + " " + ext, newPlanet, name + " " + ext, moonType, angle, moonRadius, distance, orbitDays);
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

            // Add market to inhabitable planets
            if(inhabitable)
            {			
                String owningFactionId = getRandomFaction();
                newPlanet.setFaction(owningFactionId);
				addMarketToEntity(i, newPlanet, owningFactionId, planetType, false);
            }
		}


		// Build asteroid belts
		List planets = system.getPlanets();
        int numAsteroidBelts;
        if(ExerelinSetupData.getInstance().numSystems != 1)
            numAsteroidBelts = ExerelinUtils.getRandomInRange(ExerelinConfig.minimumAsteroidBelts, ExerelinSetupData.getInstance().maxAsteroidBelts);
        else
            numAsteroidBelts = ExerelinSetupData.getInstance().maxAsteroidBelts;

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
			else if (planet.getFullName().equalsIgnoreCase(system.getStar().getFullName()))
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
		if(ExerelinSetupData.getInstance().maxSystemSize > 16000)
			system.addAsteroidBelt(star, 50, ExerelinUtils.getRandomInRange(12000, 25000), ExerelinUtils.getRandomInRange(50, 100), ExerelinUtils.getRandomInRange(480, 720), ExerelinUtils.getRandomInRange(720, 960));

		// And another one if a large system
		if(ExerelinSetupData.getInstance().maxSystemSize > 32000)
			system.addAsteroidBelt(star, 75, ExerelinUtils.getRandomInRange(25000, 35000), ExerelinUtils.getRandomInRange(100, 150), ExerelinUtils.getRandomInRange(960, 1440), ExerelinUtils.getRandomInRange(1440, 1920));

		// Build a list of possbile station names
		String[] possibleStationNames = new String[] {"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge", "Customs", "Nest", "Port", "Quey", "Terminal", "Exchange", "View", "Wall", "Habitat", "Shipyard", "Backwater"};

		// Build stations
		int numStation;
        if(ExerelinSetupData.getInstance().numSystems != 1)
            numStation = ExerelinUtils.getRandomInRange(ExerelinConfig.minimumStations, Math.min(ExerelinSetupData.getInstance().maxStations, numBasePlanets*2));
        else
            numStation = ExerelinSetupData.getInstance().maxStations;
		int currentPlanet = 0;
		int k = 0;
		while(k < numStation)
		{
			PlanetAPI planet = (PlanetAPI)planets.get(currentPlanet);
			currentPlanet = currentPlanet + 1;

			if(currentPlanet == planets.size())
				currentPlanet = 0;

			if(planet.isStar())
				continue; // Skip sun
				
			boolean isGasGiant = planet.isGasGiant();
			MarketAPI existingMarket = planet.getMarket();
			
			if (existingMarket == null)
			{
				if (Math.random() < 0.30)
					continue; // 30% chance to skip uninhabited planet
			}
			else
			{
				if (Math.random() < 0.5)
					continue; // 50% chance to skip an inhabited planet
			}

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
			if (planet.getFullName().contains(" I") || planet.getFullName().contains(" II") || planet.getFullName().contains(" III"))
				orbitRadius = 200;
			else if (isGasGiant)
				orbitRadius = 500;
			int orbitDays = orbitRadius / 25;	// ExerelinUtils.getRandomInRange(50, 100);
				
			String owningFactionId = getRandomFaction();
			FactionAPI planetFaction = planet.getFaction();
			if (planetFaction != null) owningFactionId = planetFaction.getId();
			name = planet.getFullName() + " " + name;
			String id = name.replace(' ','_');
			
			if (existingMarket == null)	// de novo station, probably orbiting a gas giant
			{
				SectorEntityToken newStation = system.addCustomEntity(id, name, "station_side02", owningFactionId);
				newStation.setCircularOrbitPointingDown(planet, angle, orbitRadius, orbitDays);
				addMarketToEntity(-1, newStation, owningFactionId, "", true);
			}
			else	// append a station to an existing inhabited planet
			{
				// these are smaller on the system map than the other ones when zoomed out
				SectorEntityToken newStation = system.addOrbitalStation(id, planet, angle, orbitRadius, orbitDays, name, owningFactionId);
				newStation.setMarket(existingMarket);
			}
			k = k + 1;
		}


        // Build hyperspace exits
        JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint(system.getId() + "_jump", "Jump Point Alpha");
        OrbitAPI orbit = Global.getFactory().createCircularOrbit(system.createToken(0,0), 0f, 1200, 120);
        jumpPoint.setOrbit(orbit);

        jumpPoint.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jumpPoint);

        system.autogenerateHyperspaceJumpPoints(true, true);

        // Build comm relay
        SectorEntityToken relay = system.addCustomEntity(system.getId() + "_relay", // unique id
                systemName + " Relay", // name - if null, defaultName from custom_entities.json will be used
                "comm_relay", // type of object, defined in custom_entities.json
                "neutral"); // faction
        relay.setCircularOrbit(star, 90, 1200, 180);
	}
}