package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import data.scripts.world.exerelin.commandQueue.CommandQueue;
import data.scripts.world.exerelin.utilities.ExerelinConfig;
import data.scripts.world.exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unchecked")
public class SectorManager
{
    private SectorEventManager sectorEventManager;
	private SystemManager[] systemManagers;
	private DiplomacyManager diplomacyManager;
    private FactionDirector[] factionDirectors;

	private String[] factionsPossibleInSector;
	private boolean playerFreeTransfer;
	private boolean respawnFactions;
	private int respawnWaitDays;
	private int maxFactions;
	private String playerFactionId;
    private String playerStartShipVariant;
	private boolean builtOmnifactoryAndStorage;
	private boolean buildOmnifactory;
	private boolean playerMovedToSpawnLocation;
	private int maxSystemSize;

    private long lastFactionSpawnTime;
    private SectorEntityToken lastInteractionToken;
    private long lastInteractionTime;

    private CommandQueue commandQueue;

    private int currentUpdateStationStep = 0;

	public SectorManager(SectorAPI sector)
	{
        sectorEventManager = new SectorEventManager();

		// Setup system managers for each system in the sector
		systemManagers = new SystemManager[sector.getStarSystems().size()];
		for(int i = 0; i < sector.getStarSystems().size(); i++)
			systemManagers[i] = new SystemManager(sector, (StarSystemAPI)sector.getStarSystems().get(i));

		// Setup a diplomacy manager for the sector
		diplomacyManager = new DiplomacyManager(sector);
	}

    public void setupFactionDirectors()
    {
        // Setup a director for each faction
        factionDirectors = new FactionDirector[factionsPossibleInSector.length];
        for(int i = 0; i < factionsPossibleInSector.length; i++)
            factionDirectors[i] = new FactionDirector(factionsPossibleInSector[i], null);
    }

	public SystemManager getSystemManager(String systemName)
	{
		for(int i = 0; i < systemManagers.length; i++)
		{
			if(systemManagers[i].getStarSystemName().equalsIgnoreCase(systemName))
				return systemManagers[i];
		}

		return null;
	}

	public SystemManager getSystemManager(StarSystemAPI starSystemAPI)
	{
		return getSystemManager(starSystemAPI.getName());
	}

    public FactionDirector[] getFactionDirectors()
    {
        return this.factionDirectors;
    }

    public FactionDirector getFactionDirector(String factionId)
    {
        for(int i = 0; i < factionDirectors.length; i++)
        {
            if(factionDirectors[i].getFactionId().equalsIgnoreCase(factionId))
                return factionDirectors[i];
        }

        return null;
    }

	public DiplomacyManager getDiplomacyManager()
	{
		return diplomacyManager;
	}

    public void updateStationTargets()
    {
        // Update station targets (1 station per system per day)
        for(int j = 0; j < this.systemManagers.length; j++)
        {
            SystemStationManager systemStationManager =  this.systemManagers[j].getSystemStationManager();
            systemStationManager.deriveStationTargets();
        }
    }

	public void updateStationFleets()
	{
		// Manage stations (1 station per system per day)
		for(int j = 0; j < this.systemManagers.length; j++)
		{
			SystemStationManager systemStationManager =  this.systemManagers[j].getSystemStationManager();
			systemStationManager.updateStationFleets();
		}
	}

    public void updateAllStationResources()
    {
        SystemManager[] systemManagers = ExerelinData.getInstance().getSectorManager().getSystemManagers();
        for(int j = 0; j < systemManagers.length; j++)
        {
            SystemStationManager systemStationManager =  systemManagers[j].getSystemStationManager();
            for(int i = 0; i < systemStationManager.getStationRecords().length; i++)
            {
                systemStationManager.getStationRecords()[i].increaseResources();
            }
        }
    }

    public void updateStationResources(int stepSize)
    {
        if(currentUpdateStationStep == stepSize)
            currentUpdateStationStep = 0;

        // Upate 1/28th of stations per call
        int numSystems = SectorManager.getCurrentSectorManager().getSystemManagers().length;
        for(int i = 0; i < numSystems; i++)
        {
            SystemStationManager systemStationManager = SectorManager.getCurrentSectorManager().getSystemManagers()[i].getSystemStationManager();
            int numStations = systemStationManager.getStationRecords().length;

            double numberToUpdate = numStations/(double)stepSize;
            int startPoint = systemStationManager.getNextIncreaseResourceStationRecord();

            numberToUpdate = numberToUpdate*((double)(currentUpdateStationStep+1));
            numberToUpdate = numberToUpdate - startPoint;

            //System.out.println("numStations: " + numStations + ", stepSize: " + stepSize + ", Number to update: " + numberToUpdate + ", startpoint: " + startPoint);

            if(numberToUpdate < 1)
                numberToUpdate = 0;

            int numToUpdate = (int)numberToUpdate;

            for(int j = 0; j < numToUpdate; j++)
                systemStationManager.updateStationResources();
        }

        currentUpdateStationStep++;
    }

	public void runEvents()
	{
        // Manage sector events
        sectorEventManager.runEvents();

		// Manage system events
		for(int j = 0; j < this.systemManagers.length; j++)
		{
			this.systemManagers[j].getSystemEventManager().runEvents();
		}
	}

	public void checkPlayerHasStationOrStationAttackFleet()
	{
        // Check if player fleet is a boarding fleet
        if(ExerelinUtils.isValidBoardingFleet(Global.getSector().getPlayerFleet(),true))
            return;

		// Check that player has a station or a station attack fleet
		for(int k = 0; k < this.systemManagers.length; k++)
		{
			StarSystemAPI system = this.systemManagers[k].getStarSystemAPI();

			String[] factionsInSystem = SectorManager.getCurrentSectorManager().getFactionsInSector();
			if(factionsInSystem.length == 0)
				continue;

			for(int j = 0; j < factionsInSystem.length; j = j + 1)
			{
				if(factionsInSystem[j].equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					return;
			}

			List fleets = system.getFleets();
			for(int i = 0; i < fleets.size(); i = i + 1)
			{
				CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);
				if(ExerelinUtils.isValidBoardingFleet(fleet, true))
					return;
			}

			diplomacyManager.declarePeaceWithAllFactions(ExerelinData.getInstance().getPlayerFaction());
			diplomacyManager.createWarIfNoneExists(ExerelinData.getInstance().getPlayerFaction());
			SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(system);
			SectorEntityToken target = ExerelinUtils.getClosestEnemyStation(ExerelinData.getInstance().getPlayerFaction(), this.systemManagers[k].getStarSystemAPI(), Global.getSector(), token);

			if(target == null)
				return;

			int numStationAttackFleets = 1;
			CargoAPI cargo = target.getCargo();
			if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 1000 || cargo.getMarines() > 1000 || cargo.getSupplies() > 1000 || cargo.getFuel() > 1000)
				numStationAttackFleets = numStationAttackFleets + 1;
			if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 2000 || cargo.getMarines() > 2000 || cargo.getSupplies() > 2000 || cargo.getFuel() > 2000)
				numStationAttackFleets = numStationAttackFleets + 1;

			for(int h = 0; h < numStationAttackFleets; h = h + 1)
			{
				OutSystemStationAttackFleet omsaf = new OutSystemStationAttackFleet(Global.getSector(), system, ExerelinData.getInstance().getPlayerFaction(), true);
				omsaf.spawnFleet(target, token);
                FactionDirector.getFactionDirectorForFactionId(ExerelinData.getInstance().getPlayerFaction()).setHomeSystem(system);
			}
			return;
		}
	}

	public void respawnRandomFaction()
	{
		if(!this.respawnFactions)
			return; // No factions will respawn

		if(Global.getSector().getClock().getElapsedDaysSince(lastFactionSpawnTime) < this.respawnWaitDays)
			return; // Hasn't been long enough since the last respawn

        if(this.getFactionsInSector().length >= this.getMaxFactions())
            return; // Enough faction in sector already


        // Chance to create out system attack fleet for a missing faction
        int attempts = 0;
        String factionId = null;
        while((factionId == null || factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction())) && attempts < 40)
        {
            attempts = attempts + 1;
            factionId = this.getFactionsPossibleInSector()[ExerelinUtils.getRandomInRange(0, this.getFactionsPossibleInSector().length - 1)];

            if(this.isFactionInSector(factionId))
                factionId = null;
        }

        if(factionId == null || factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
            return;

        System.out.println("--- RESPAWNING FACTION ---");
        System.out.println("Faction: " + factionId);

        // Ensure faction only has target of leading faction
        diplomacyManager.declarePeaceWithAllFactions(factionId);
        diplomacyManager.createWarIfNoneExists(factionId);

        StarSystemAPI system = null;
        attempts = 0;
        while (system == null && attempts < 40)
        {
            attempts++;
            system = (StarSystemAPI)Global.getSector().getStarSystems().get(ExerelinUtils.getRandomInRange(0, Global.getSector().getStarSystems().size() - 1));
            System.out.println("Checking: " + system.getName());
            if(!ExerelinUtils.isFactionPresentInSystem(SectorManager.getCurrentSectorManager().getLeadingFaction(), system))
                system = null;
        }

        if(system == null)
            return;

        System.out.println("System: " + system.getName());

        SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(system);
        SectorEntityToken stationTarget = ExerelinUtils.getClosestEnemyStation(factionId, system, Global.getSector(), token);

        if(stationTarget == null)
            return;

        System.out.println("Station: " + stationTarget.getName());

        int numStationAttackFleets = 1;
        CargoAPI cargo = stationTarget.getCargo();
        if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 800 || cargo.getMarines() > 400 || cargo.getSupplies() > 3200 || cargo.getFuel() > 800)
            numStationAttackFleets = numStationAttackFleets + 1;
        if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 1600 || cargo.getMarines() > 800 || cargo.getSupplies() > 6400 || cargo.getFuel() > 1600)
            numStationAttackFleets = numStationAttackFleets + 2;

        for(int h = 0; h < numStationAttackFleets; h = h + 1)
        {
            OutSystemStationAttackFleet omsaf = new OutSystemStationAttackFleet(Global.getSector(), system, factionId, true);
            omsaf.spawnFleet(stationTarget, token);
            FactionDirector.getFactionDirectorForFactionId(factionId).setHomeSystem(system);
        }

        lastFactionSpawnTime = Global.getSector().getClock().getTimestamp();

        System.out.println(" - - - - - - - - - - - - ");

	}

	public void payPlayerWages()
	{
		// Pay wages if needed
		if(ExerelinConfig.playerBaseWage > 0)
		{
			if(this.getDiplomacyManager().getRecordForFaction(this.playerFactionId).isAtWar())
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(ExerelinConfig.playerBaseWage*2);
			else
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(ExerelinConfig.playerBaseWage);
		}
	}

	public void doEveryFrameChecks()
    {
        SectorAPI sector = Global.getSector();

		// Build OmniFactory and Storage if not been built and players faction has a station
		if(!this.builtOmnifactoryAndStorage)
		{
            if(sector.getPlayerFleet().isInHyperspace())
                return;

			if(SectorManager.getCurrentSectorManager().isFactionInSector(ExerelinData.getInstance().getPlayerFaction()))
			{
                StarSystemAPI system = null;
                for(int i = 0; i < Global.getSector().getStarSystems().size(); i++)
                {
                    system = (StarSystemAPI)Global.getSector().getStarSystems().get(i);
                    if(SystemManager.getSystemManagerForAPI(system).isFactionInSystem(ExerelinData.getInstance().getPlayerFaction()))
                        break;
                }

				SectorEntityToken playerStation = ExerelinUtils.getRandomStationInSystemForFaction(ExerelinData.getInstance().getPlayerFaction(), system);
                SectorEntityToken planet = playerStation.getOrbit().getFocus();

                if(!this.playerFreeTransfer)
                    new OutSystemStationConstructionFleet(sector,  system,  system, "neutral", planet, "storage").spawnFleet();

                if(ExerelinConfig.randomOmnifactoryLocation)
                {
                    // Choose a random location for the omnifactory
                    system = (StarSystemAPI)Global.getSector().getStarSystems().get(ExerelinUtils.getRandomInRange(0, Global.getSector().getStarSystems().size() - 1));
                    planet = (SectorEntityToken)system.getPlanets().get(ExerelinUtils.getRandomInRange(0, system.getPlanets().size() - 1));
                }

                if(this.buildOmnifactory)
                    new OutSystemStationConstructionFleet(sector,  system,  system, "neutral", planet, "omnifac").spawnFleet();

                this.builtOmnifactoryAndStorage = true;
			}
		}

		// Set player fleet to appropriate faction
		if(sector.getPlayerFleet().getFaction().getId().equalsIgnoreCase("player"))
		{
			sector.getPlayerFleet().setFaction(ExerelinData.getInstance().getPlayerFaction());

			if(playerMovedToSpawnLocation)
			{
				// Not initial fleet so set player fleet to have their starting frigate
                sector.getPlayerFleet().getFleetData().removeFleetMember(((FleetMemberAPI)sector.getPlayerFleet().getFleetData().getMembersListCopy().get(0)));
                sector.getPlayerFleet().getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, this.playerStartShipVariant));

                // Move them to one of their stations
                SectorEntityToken station = null;
                if(SectorManager.getCurrentSectorManager().getFactionDirector(ExerelinData.getInstance().getPlayerFaction()).getHomeSystem() != null)
                    station = ExerelinUtils.getRandomStationInSystemForFaction(ExerelinData.getInstance().getPlayerFaction(), SectorManager.getCurrentSectorManager().getFactionDirector(ExerelinData.getInstance().getPlayerFaction()).getHomeSystem());
                if(station != null)
                    sector.getPlayerFleet().setLocation(station.getLocation().getX(),  station.getLocation().getY());
			}
            else
            {
                // Initial fleet so add boarding ships
                CampaignFleetAPI dummyBoardingFleet = Global.getSector().createFleet(ExerelinData.getInstance().getPlayerFaction(), "exerelinInSystemStationAttackFleet");
                List members = dummyBoardingFleet.getFleetData().getMembersListCopy();
                for(int i = 0; i < members.size(); i++)
                    Global.getSector().getPlayerFleet().getFleetData().addFleetMember((FleetMemberAPI)members.get(i));

                // Start of game, player fleet is last to be spawned so set last faction spawn time as this
                this.lastFactionSpawnTime = Global.getSector().getClock().getTimestamp();
                FactionDirector.getFactionDirectorForFactionId(ExerelinData.getInstance().getPlayerFaction()).setHomeSystem((StarSystemAPI)Global.getSector().getPlayerFleet().getContainingLocation());

                // Move to appropriate start location edge of map
                sector.setCurrentLocation(sector.getRespawnLocation());
                SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(Global.getSector().getPlayerFleet().getContainingLocation());
                sector.getPlayerFleet().setLocation(token.getLocation().getX(), token.getLocation().getY());
                playerMovedToSpawnLocation = true;
            }
            ExerelinUtils.resetFleetCargoToDefaults(Global.getSector().getPlayerFleet(), 0.1f, 0.0f, CargoAPI.CrewXPLevel.GREEN);
		}

        if(Global.getSector().getPlayerFleet().getInteractionTarget() != null)
            this.setLastInteractionToken(Global.getSector().getPlayerFleet().getInteractionTarget());
        else if(Global.getSector().getClock().getElapsedDaysSince(this.getLastInteractionTime()) > 20)
            this.lastInteractionToken = null;
	}

	public boolean getRespawnFactions()
	{
		return respawnFactions;
	}

	public int getMaxSystemSize()
	{
		return maxSystemSize;
	}

	public String[] getFactionsPossibleInSector()
	{
		return factionsPossibleInSector;
	}

	public int getMaxFactions()
	{
		return maxFactions;
	}

	public void setPlayerFreeTransfer(Boolean inPlayerFreeTransfer)
	{
		playerFreeTransfer = inPlayerFreeTransfer;
	}

	public void setRespawnFactions(Boolean inRespawnFactions)
	{
		respawnFactions = inRespawnFactions;
	}

	public void setMaxFactions(int inMaxFactions)
	{
		maxFactions = inMaxFactions;
	}

	public void setPlayerFactionId(String inPlayerFactionId)
	{
		playerFactionId = inPlayerFactionId;
	}

	public void setFactionsPossibleInSector(String[] inFactionsPossibleInSector)
	{
		factionsPossibleInSector = inFactionsPossibleInSector;
	}

	public void setRespawnWaitDays(int inRespawnWaitDays)
	{
		respawnWaitDays = inRespawnWaitDays;
	}

    public int getRespawnWaitDays()
    {
        return this.respawnWaitDays;
    }

    public long getLastFactionSpawnTime()
    {
        return lastFactionSpawnTime;
    }

    public void setLastFactionSpawnTime(long timestamp)
    {
        lastFactionSpawnTime = timestamp;
    }

	public void setBuildOmnifactory(Boolean inBuildOmnifactory)
	{
		buildOmnifactory = inBuildOmnifactory;
	}

	public void setMaxSystemSize(int inMaxSystemSize)
	{
		maxSystemSize = inMaxSystemSize;
	}

	public static SectorManager getCurrentSectorManager()
	{
		return ExerelinData.getInstance().getSectorManager();
	}

	public SystemManager[] getSystemManagers()
	{
		return this.systemManagers;
	}

	public int getNumSystems()
	{
		return this.systemManagers.length;
	}


	public Boolean isFactionInSector(String factionId)
	{
		for(int i = 0; i < this.systemManagers.length; i++)
		{
			if(this.systemManagers[i].isFactionInSystem(factionId))
				return true;
		}

		return false;
	}


	public String getLeadingFaction()
	{
        float tempLeaderOwnership = 0.0f;
        String tempLeaderId = "";

        for(int i = 0; i < this.getFactionsInSector().length; i++)
        {
            String factionId = this.getFactionsInSector()[i];

            if(this.isFactionInSector(factionId))
            {
                if(this.getSectorOwnership(factionId) > tempLeaderOwnership)
                {
                    tempLeaderId = factionId;
                    tempLeaderOwnership = this.getSectorOwnership(factionId);
                }
            }
        }

        return tempLeaderId;
	}

	public String getLosingFaction()
	{
        float tempLoserOwnership = 1200.0f;
        String tempLoserId = "";

        for(int i = 0; i < this.getFactionsInSector().length; i++)
        {
            String factionId = this.getFactionsInSector()[i];
            //System.out.println("Checking : " + factionId + ", inSector: " + this.isFactionInSector(factionId) + ", ownership: " + this.getSectorOwnership(factionId));

            if(this.isFactionInSector(factionId))
            {
                if(this.getSectorOwnership(factionId) < tempLoserOwnership)
                {
                    tempLoserId = factionId;
                    tempLoserOwnership = this.getSectorOwnership(factionId);
                }
            }
        }

        return tempLoserId;
	}

	public float getSectorOwnership(String factionId)
	{
		float ownership = 0f;
		for(int i = 0; i < this.systemManagers.length; i++)
		{
			ownership = ownership + systemManagers[i].getSystemOwnership(factionId);
		}

		return ownership/this.systemManagers.length;
	}

	public String[] getFactionsInSector()
	{
		ArrayList foundFactions = new ArrayList(factionsPossibleInSector.length);

		for(int i = 0; i < this.systemManagers.length; i++)
		{
			String[] factionsInSystem = this.systemManagers[i].getFactionsInSystem();
			for(int j = 0; j < factionsInSystem.length; j++)
			{
				if(!foundFactions.contains(factionsInSystem[j]))
					foundFactions.add(factionsInSystem[j]);
			}
		}

		return (String[])foundFactions.toArray( new String[foundFactions.size()] );
	}

    /*public SectorAPI getSectorAPI()
    {
        return sectorAPI;
    }*/

    public void setPlayerStartShipVariant(String variantId)
    {
        this.playerStartShipVariant = variantId;
    }

    public void setCommandQueue(CommandQueue commandQueue)
    {
        this.commandQueue = commandQueue;
    }

    public CommandQueue getCommandQueue()
    {
        return this.commandQueue;
    }

    public SectorEntityToken getLastInteractionToken()
    {
        return lastInteractionToken;
    }

    public void setLastInteractionToken(SectorEntityToken token)
    {
        this.lastInteractionToken = token;
        this.lastInteractionTime = Global.getSector().getClock().getTimestamp();
    }

    public long getLastInteractionTime()
    {
        return  lastInteractionTime;
    }

    public String getPlayerFactionId()
    {
        return playerFactionId;
    }

    public SectorEventManager getSectorEventManager()
    {
        return this.sectorEventManager;
    }
}
