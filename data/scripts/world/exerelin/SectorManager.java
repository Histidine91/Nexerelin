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

	private String[] factionsPossibleInSector;
	private boolean playerFreeTransfer;
	private boolean respawnFactions;
	private int respawnWaitMonths;
	private int maxFactions;
	private String playerFactionId;
	private boolean builtOmnifactoryAndStorage;
	private boolean buildOmnifactory;
	private boolean playerMovedToSpawnLocation;
	private int maxSystemSize;

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
				if(fleet.getFaction().getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) && fleet.getFullName().contains("Command Fleet"))
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
			}
			return;
		}
	}

	public void respawnRandomFaction()
	{
		if(!this.respawnFactions)
			return;

		if(respawnWaitMonths != 0)
		{
			respawnWaitMonths = respawnWaitMonths - 1;
			return;
		}

		for(int k = 0; k < this.systemManagers.length; k++)
		{
			SystemManager systemManager = this.systemManagers[k];
			StarSystemAPI systemAPI = systemManager.getStarSystemAPI();

			String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
			String[] factionsInSystem = SectorManager.getCurrentSectorManager().getFactionsInSector();

			if(factionsInSystem.length == 0)
				return;

			System.out.println(" - - - - - - - - - - - - ");
			System.out.println("Calling out-system station attack fleets for " + systemAPI.getName());

			if(systemManager.getSystemStationManager().getNumFactionsInSystem() >= maxFactions)
			{
				System.out.println(systemManager.getSystemStationManager().getNumFactionsInSystem() + " of " + maxFactions + " already in system.");
				System.out.println(" - - - - - - - - - - - - ");
				return;
			}

			// Chance to create out system attack fleet for a missing faction
			int attempts = 0;
			String factionId = null;
			while((factionId == null || factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction())) && attempts < 20)
			{
				attempts = attempts + 1;
				factionId = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];

				Boolean inSystem = false;
				for(int j = 0; j < factionsInSystem.length; j = j + 1)
				{
					if(factionId.equalsIgnoreCase(factionsInSystem[j]))
					{
						inSystem = true;
						break;
					}
				}
				if(inSystem)
					factionId = null;
			}

			if(factionId == null || factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			{
				System.out.println(" - - - - - - - - - - - - ");
				return;
			}

			SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(systemAPI);

			// Ensure faction only has target of leading faction
			diplomacyManager.declarePeaceWithAllFactions(factionId);
			diplomacyManager.createWarIfNoneExists(factionId);

			SectorEntityToken stationTarget = ExerelinUtils.getClosestEnemyStation(factionId, this.systemManagers[k].getStarSystemAPI(), sectorAPI, token);

			if(stationTarget == null)
			{
				System.out.println("Couldn't get out system attack fleet station target for " + factionId);
				System.out.println(" - - - - - - - - - - - - ");
				return;
			}

			int numStationAttackFleets = 1;
			CargoAPI cargo = stationTarget.getCargo();
			if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 800 || cargo.getMarines() > 400 || cargo.getSupplies() > 3200 || cargo.getFuel() > 800)
				numStationAttackFleets = numStationAttackFleets + 1;
			if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 1600 || cargo.getMarines() > 800 || cargo.getSupplies() > 6400 || cargo.getFuel() > 1600)
				numStationAttackFleets = numStationAttackFleets + 2;

			for(int h = 0; h < numStationAttackFleets; h = h + 1)
			{
				OutSystemStationAttackFleet omsaf = new OutSystemStationAttackFleet(sectorAPI, systemAPI, factionId, true);
				omsaf.spawnFleet(stationTarget, token);
			}
			System.out.println(" - - - - - - - - - - - - ");
		}
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
		//NOTE: USE THE REFERENCE TO THE SYSTEMMANAGER IN EXERELINDATA RATHER THAN REFRESHING HERE

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
			StarSystemAPI system = (StarSystemAPI)sectorAPI.getPlayerFleet().getContainingLocation();

			if(SectorManager.getCurrentSectorManager().isFactionInSector(ExerelinData.getInstance().getPlayerFaction()))
			{
				java.util.List planets = system.getPlanets();
				SectorEntityToken playerStation = ExerelinUtils.getRandomStationForFaction(ExerelinData.getInstance().getPlayerFaction(), this.getSystemManagers()[0].getStarSystemAPI(), sectorAPI); // TODO change
				for(int i = 0; i < planets.size(); i = i + 1)
				{
					SectorEntityToken planet = ((SectorEntityToken)planets.get(i));
					// Check if this planet has the player station
					if(playerStation.getFullName().contains(planet.getFullName()))
					{
						if(this.buildOmnifactory)
							new OutSystemStationConstructionFleet(sectorAPI,  system,  system, "neutral", planet, "omnifac").spawnFleet();

						if(!this.playerFreeTransfer)
							new OutSystemStationConstructionFleet(sectorAPI,  system,  system, "neutral", planet, "storage").spawnFleet();

						this.builtOmnifactoryAndStorage = true;
						break;
					}
				}
			}
		}

		// Set player fleet to appropriate faction
		if(sectorAPI.getPlayerFleet().getFaction().getId().equalsIgnoreCase("player"))
		{
			sectorAPI.getPlayerFleet().setFaction(ExerelinData.getInstance().getPlayerFaction());
			SectorEntityToken station = ExerelinUtils.getRandomStationForFaction(ExerelinData.getInstance().getPlayerFaction(), this.getSystemManagers()[0].getStarSystemAPI(), sectorAPI); //TODO - change
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
						sectorAPI.getPlayerFleet().getCargo().addCrew(CargoAPI.CrewXPLevel.GREEN, (int)sectorAPI.getPlayerFleet().getFlagship().getMinCrew());
						break;
					}
				}
			}

			// Set starting or respawn cargo
			CargoAPI fleetCargo = sectorAPI.getPlayerFleet().getCargo();
			fleetCargo.clear();
			FleetMemberAPI fmAPI = sectorAPI.getPlayerFleet().getFlagship();
			fleetCargo.addCrew(CargoAPI.CrewXPLevel.REGULAR,  (int)fmAPI.getMinCrew() + (int)((fmAPI.getMaxCrew() - fmAPI.getMinCrew())/2));
			fleetCargo.addMarines(((int)fmAPI.getMaxCrew() - (int)fmAPI.getMinCrew())/2);
			fleetCargo.addFuel(fmAPI.getFuelCapacity()/2);
			fleetCargo.addSupplies(fmAPI.getCargoCapacity()/2);
		}

		// Move player to their starting fleet if this is the start of the game
		if(!playerMovedToSpawnLocation)
		{
			if(ExerelinData.getInstance().playerOffMapFleetSpawnLocation != null)
			{
				sectorAPI.getPlayerFleet().setLocation(ExerelinData.getInstance().playerOffMapFleetSpawnLocation.getX(), ExerelinData.getInstance().playerOffMapFleetSpawnLocation.getY());
			}
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

	public void setRespawnWaitMonths(int inRespawnWaitMonths)
	{
		respawnWaitMonths = inRespawnWaitMonths;
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
		HashMap map = new HashMap();
		int currentLeaderCount = 0;
		String currentLeader = "";

		for(int i = 0; i < this.systemManagers.length; i++)
		{
			String leadingFactionTemp = this.systemManagers[i].getLeadingFactionId();
			Integer count = 0;
			if(map.containsKey(leadingFactionTemp))
			{
				count = Integer.parseInt((String)map.get(leadingFactionTemp)) + 1;
				map.remove(leadingFactionTemp);
			}
			else
				count = 1;

			map.put(leadingFactionTemp,  count.toString());

			if(currentLeaderCount < count)
			{
				currentLeader = leadingFactionTemp;
				currentLeaderCount = count;
			}
		}
        if(currentLeader == null)
            return "";
        else
		    return currentLeader;
	}

	public String getLosingFaction()
	{
		HashMap map = new HashMap();
		int currentLoserCount = 0;
		String currentLoser = "";

		for(int i = 0; i < this.systemManagers.length; i++)
		{
			String losingFactionTemp = this.systemManagers[i].getLosingFactionId();
			Integer count = 0;
			if(map.containsKey(losingFactionTemp))
			{
				count = Integer.parseInt((String)map.get(losingFactionTemp)) + 1;
				map.remove(losingFactionTemp);
			}
			else
				count = 1;

			map.put(losingFactionTemp,  count.toString());

			if(currentLoserCount < count)
			{
				currentLoser = losingFactionTemp;
				currentLoserCount = count;
			}
		}
        if(currentLoser == null)
            return "";
        else
		    return currentLoser;
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
