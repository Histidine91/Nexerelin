package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SectorManager
{
	private SystemManager[] systemManagers;
	private DiplomacyManager diplomacyManager;
	private SectorAPI sectorAPI;
    private FactionDirector[] factionDirectors;

	private String[] factionsPossibleInSector;
	private boolean playerFreeTransfer;
	private boolean respawnFactions;
	private int respawnWaitDays;
	private int maxFactions;
	private String playerFactionId;
	private boolean builtOmnifactoryAndStorage;
	private boolean buildOmnifactory;
	private boolean playerMovedToSpawnLocation;
	private int maxSystemSize;

    private long lastFactionSpawnTime;

	public SectorManager(SectorAPI inSectorAPI)
	{
		sectorAPI = inSectorAPI;

		// Setup system managers for each system in the sector
		systemManagers = new SystemManager[sectorAPI.getStarSystems().size()];
		for(int i = 0; i < sectorAPI.getStarSystems().size(); i++)
			systemManagers[i] = new SystemManager(sectorAPI, (StarSystemAPI)sectorAPI.getStarSystems().get(i));

		// Setup a diplomacy manager for the sector
		diplomacyManager = new DiplomacyManager(sectorAPI);
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

	public void updateStations()
	{
		// Manage stations (1 update per 10 stations per day)
		for(int j = 0; j < this.systemManagers.length; j++)
		{
			SystemStationManager systemStationManager =  this.systemManagers[j].getSystemStationManager();
			int numStationsToUpdate = Math.max(1, ExerelinUtils.getRandomNearestInteger(systemStationManager.getStationRecords().length / 10f));
			for(int i = 0; i < numStationsToUpdate; i++)
				systemStationManager.updateStations();
		}
	}

	public void runEvents()
	{
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
			SectorEntityToken target = ExerelinUtils.getClosestEnemyStation(ExerelinData.getInstance().getPlayerFaction(), this.systemManagers[k].getStarSystemAPI(), sectorAPI, token);

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
				OutSystemStationAttackFleet omsaf = new OutSystemStationAttackFleet(sectorAPI, system, ExerelinData.getInstance().getPlayerFaction(), true);
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
        SectorEntityToken stationTarget = ExerelinUtils.getClosestEnemyStation(factionId, system, sectorAPI, token);

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
            OutSystemStationAttackFleet omsaf = new OutSystemStationAttackFleet(sectorAPI, system, factionId, true);
            omsaf.spawnFleet(stationTarget, token);
            FactionDirector.getFactionDirectorForFactionId(factionId).setHomeSystem(system);
        }

        lastFactionSpawnTime = Global.getSector().getClock().getTimestamp();

        System.out.println(" - - - - - - - - - - - - ");

	}

	public void payPlayerWages()
	{
		// Pay wages if needed
		if(!this.playerFreeTransfer)
		{
			if(this.getDiplomacyManager().isFactionAtWarAndHasTarget(this.diplomacyManager.playerRecord.getFactionId()))
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(5000f);
			else
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(2500f);
			Global.getSector().addMessage("Wages Paid", Color.green);
		}
	}

	public void doSetupChecks()
	{
		//NOTE: FOLLOWING CODE IS USED TO HANDLE SAVE DATA LOADING !!!!!! DEPRECATED APPROACH
		//NOTE: USE THE REFERENCE TO THE SECTORMANAGER IN EXERELINDATA RATHER THAN REFRESHING HERE

		// Refresh available factions to cache before doing anything
		if(!ExerelinData.getInstance().confirmedAvailableFactions)
		{
			ExerelinData.getInstance().setAvailableFactions(factionsPossibleInSector);
			ExerelinData.getInstance().confirmedAvailableFactions = true;
		}

		// Reset (needed for cross-loading saves
		ExerelinData.getInstance().confirmedAvailableFactions = false;

		// Refresh player faction to cache before doing anything
		if(!ExerelinData.getInstance().confirmedFaction)
		{
			ExerelinData.getInstance().setPlayerFaction(playerFactionId);
			ExerelinData.getInstance().confirmedFaction = true;
		}

		// Reset (needed for cross-loading saves)
		ExerelinData.getInstance().confirmedFaction = false;

		// Refresh free transfer setting to cache before doing anything
		if(!ExerelinData.getInstance().confirmedFreeTransfer)
		{
			ExerelinData.getInstance().playerOwnedStationFreeTransfer = playerFreeTransfer;
			ExerelinData.getInstance().confirmedFreeTransfer = true;
		}

		// Reset (needed for cross-loading saves)
		ExerelinData.getInstance().confirmedFreeTransfer = false;

		// END SAVE DATA LOADING

		// Load the current sector manager into the cache
		ExerelinData.getInstance().setSectorManager(this);

		// Build OmniFactory and Storage if not been built and players faction has a station
		if(!this.builtOmnifactoryAndStorage)
		{
            if(sectorAPI.getPlayerFleet().isInHyperspace())
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

				SectorEntityToken playerStation = ExerelinUtils.getRandomStationInSystemForFaction(ExerelinData.getInstance().getPlayerFaction(), system, sectorAPI);
                SectorEntityToken planet = playerStation.getOrbit().getFocus();

                if(this.buildOmnifactory)
                    new OutSystemStationConstructionFleet(sectorAPI,  system,  system, "neutral", planet, "omnifac").spawnFleet();

                if(!this.playerFreeTransfer)
                    new OutSystemStationConstructionFleet(sectorAPI,  system,  system, "neutral", planet, "storage").spawnFleet();

                this.builtOmnifactoryAndStorage = true;
			}
		}

		// Set player fleet to appropriate faction
		if(sectorAPI.getPlayerFleet().getFaction().getId().equalsIgnoreCase("player"))
		{
			sectorAPI.getPlayerFleet().setFaction(ExerelinData.getInstance().getPlayerFaction());
			SectorEntityToken station = null;
            if(SectorManager.getCurrentSectorManager().getFactionDirector(ExerelinData.getInstance().getPlayerFaction()).getHomeSystem() != null)
                station = ExerelinUtils.getRandomStationInSystemForFaction(ExerelinData.getInstance().getPlayerFaction(), SectorManager.getCurrentSectorManager().getFactionDirector(ExerelinData.getInstance().getPlayerFaction()).getHomeSystem(), sectorAPI);
			if(station != null)
				sectorAPI.getPlayerFleet().setLocation(station.getLocation().getX(),  station.getLocation().getY());

			if(playerMovedToSpawnLocation)
			{
				// Not initial fleet so set player fleet to have a faction specfic frigate
				CampaignFleetAPI dummyFleet = sectorAPI.createFleet(ExerelinData.getInstance().getPlayerFaction(), "exerelinGenericFleet");

				for(int i = 0; i < dummyFleet.getFleetData().getMembersListCopy().size(); i++)
				{
					if(((FleetMemberAPI)dummyFleet.getFleetData().getMembersListCopy().get(i)).isFrigate())
					{
						sectorAPI.getPlayerFleet().getFleetData().removeFleetMember(((FleetMemberAPI)sectorAPI.getPlayerFleet().getFleetData().getMembersListCopy().get(0)));
						sectorAPI.getPlayerFleet().getFleetData().addFleetMember(((FleetMemberAPI)dummyFleet.getFleetData().getMembersListCopy().get(i)));
						break;
					}
				}
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
            }
            ExerelinUtils.resetFleetCargoToDefaults(Global.getSector().getPlayerFleet(), 0.1f, 0.1f, CargoAPI.CrewXPLevel.GREEN);
		}

		// Move player to edge of system if start of game
		if(!playerMovedToSpawnLocation)
		{
            sectorAPI.setCurrentLocation(sectorAPI.getRespawnLocation());
            SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(Global.getSector().getPlayerFleet().getContainingLocation());
            sectorAPI.getPlayerFleet().setLocation(token.getLocation().getX(), token.getLocation().getY());
			playerMovedToSpawnLocation = true;
		}
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

    public SectorAPI getSectorAPI()
    {
        return sectorAPI;
    }
}
