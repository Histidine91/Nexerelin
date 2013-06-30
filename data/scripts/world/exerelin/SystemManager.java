package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.awt.*;
import java.util.GregorianCalendar;
import java.util.List;

public class SystemManager implements SpawnPointPlugin
{
	// Calendar stuff
	private static final float BASE_INTERVAL = 1.0f;
	private static final int FIRST_DAY_IN_WEEK = GregorianCalendar.SUNDAY;
	private float heartbeatInterval;
	private long lastHeartbeat;
	private GregorianCalendar calendar = new GregorianCalendar();

	private SectorAPI sectorAPI;

	public int monthsToWait = 100000;
	private boolean playerMovedToSpawnLocation = false;
	private boolean builtOmnifactoryAndStorage = false;

	// Managers
	public DiplomacyManager diplomacyManager;
	public StationManager stationManager;
	public EventManager eventManager;

	// Using SystemManager to handle these settings for saving game
	public String playerFactionId = "";
	public boolean respawnFactions = false;
	public boolean freeTransfer = false;
	public String[] availableFactions = null;
	public boolean omnifactoryPresent = false;
	public int maxFactionsInExerelin = 999;

	public SystemManager(SectorAPI sector)
	{
		// Synch the heartbeat to the sectorAPI clock
		this.sectorAPI = sector;
		lastHeartbeat = sector.getClock().getTimestamp();
		// The first heartbeat should happen at the start of day 1
		heartbeatInterval = (1.0f - (sector.getClock().getHour() / 24f));

		StarSystemAPI system = sector.getStarSystem("Exerelin");

		diplomacyManager = new DiplomacyManager(sector);
		stationManager = new StationManager(sector, system);
		eventManager = new EventManager(sector, system);
	}

	private void runDaily()
	{
		StarSystemAPI system = sectorAPI.getStarSystem("Exerelin");

		// Check player betrayal
		diplomacyManager.checkBetrayal();

		// Manage relationships
		diplomacyManager.updateRelationships();

		// Manage stations (1 update per 10 stations per day)
		for(int i = 0; i < Math.max(1, stationManager.getStationRecords().length/10); i++)
			stationManager.updateStations();

		// Manage system events
		eventManager.runEvents();

		// Check that player has a station or a station attack fleet
		String[] factionsInSystem = ExerelinUtils.getFactionsInSystem(system);
		if(factionsInSystem.length == 0)
			return;

		for(int j = 0; j < factionsInSystem.length; j = j + 1)
		{
			if(factionsInSystem[j].equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				return;
		}

		List fleets = system.getFleets();
		for(int i = 0; i < fleets.size(); i = i + 1)
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);
			if(fleet.getFaction().getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) && fleet.getFullName().contains("Station Attack Fleet"))
				return;
		}

		diplomacyManager.declarePeaceWithAllFactions(ExerelinData.getInstance().getPlayerFaction());
		diplomacyManager.createWarIfNoneExists(ExerelinData.getInstance().getPlayerFaction());
		SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(system);
		SectorEntityToken target = ExerelinUtils.getClosestEnemyStation(ExerelinData.getInstance().getPlayerFaction(), sectorAPI, token);

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
	}

	private void runWeekly()
	{
		// Pay wages if needed
		if(!this.freeTransfer)
		{
			if(diplomacyManager.playerRecord.hasWarTagetInSystem(false))
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(5000f);
			else
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(2500f);
			Global.getSector().addMessage("Wages Paid", Color.magenta);
		}
	}

	private void runMonthly()
	{
		if(!this.respawnFactions)
			return;

		if(monthsToWait != 0)
		{
			monthsToWait = monthsToWait - 1;
			return;
		}

		StarSystemAPI system = sectorAPI.getStarSystem("Exerelin");
		String[] factions = ExerelinData.getInstance().getAvailableFactions(sectorAPI);
		String[] factionsInSystem = ExerelinUtils.getFactionsInSystem(system);

		if(factionsInSystem.length == 0)
			return;

		System.out.println(" - - - - - - - - - - - - ");
		System.out.println("Calling out-system station attack fleets");

		if(stationManager.getNumFactionsInSystem() >= maxFactionsInExerelin)
		{
			System.out.println(stationManager.getNumFactionsInSystem() + " of " + maxFactionsInExerelin + " already in system.");
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

		SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(system);

		// Ensure faction only has target of leading faction
		diplomacyManager.declarePeaceWithAllFactions(factionId);
		diplomacyManager.createWarIfNoneExists(factionId);

		SectorEntityToken stationTarget = ExerelinUtils.getClosestEnemyStation(factionId, sectorAPI, token);

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
		if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 1600 || cargo.getMarines() > 800 || cargo.getSupplies() > 6400 || cargo.getFuel() > 800)
			numStationAttackFleets = numStationAttackFleets + 2;

		for(int h = 0; h < numStationAttackFleets; h = h + 1)
		{
			OutSystemStationAttackFleet omsaf = new OutSystemStationAttackFleet(sectorAPI, system, factionId, true);
			omsaf.spawnFleet(stationTarget, token);
		}
		System.out.println(" - - - - - - - - - - - - ");
	}

	private void runYearly()
	{

	}

	private void doIntervalChecks(long time)
	{
		lastHeartbeat = time;

		runDaily();

		calendar.setTimeInMillis(time);

		if (calendar.get(GregorianCalendar.DAY_OF_WEEK) == FIRST_DAY_IN_WEEK)
		{
			runWeekly();
		}

		if (calendar.get(GregorianCalendar.DAY_OF_MONTH) == 1)
		{
			runMonthly();

			if (calendar.get(GregorianCalendar.DAY_OF_YEAR) == 1)
			{
				runYearly();
			}
		}
	}

	private void checkSynched()
	{
		// Compensate for day-synch code in constructor
		if (heartbeatInterval != BASE_INTERVAL)
		{
			heartbeatInterval = BASE_INTERVAL;
		}
	}

	@Override
	public void advance(SectorAPI sector, LocationAPI location)
	{
		//NOTE: FOLLOWING CODE IS USED TO HANDLE SAVE DATA LOADING

		// Refresh available factions to cache before doing anything
		if(!ExerelinData.getInstance().confirmedAvailableFactions)
		{
			ExerelinData.getInstance().setAvailableFactions(availableFactions);
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
			ExerelinData.getInstance().playerOwnedStationFreeTransfer = freeTransfer;
			ExerelinData.getInstance().confirmedFreeTransfer = true;
		}

		// Reset (needed for cross-loading saves)
		ExerelinData.getInstance().confirmedFreeTransfer = false;

		// END SAVE DATA LOADING

		// Load the current system manager into the cache
		ExerelinData.getInstance().systemManager = this;

		// Build OmniFactory and Storage if not been built and player has a station
		if(!this.builtOmnifactoryAndStorage)
		{
			StarSystemAPI system = sector.getStarSystem("Exerelin");
			if(ExerelinUtils.numberStationsFactionOwns(ExerelinData.getInstance().getPlayerFaction(), system) > 0)
			{
				List planets = system.getPlanets();
				SectorEntityToken playerStation = ExerelinUtils.getRandomStationForFaction(ExerelinData.getInstance().getPlayerFaction(), sector);
				for(int i = 0; i < planets.size(); i = i + 1)
				{
					SectorEntityToken planet = ((SectorEntityToken)planets.get(i));
					// Check if this planet has the player station
					if(playerStation.getFullName().contains(planet.getFullName()))
					{
						if(this.omnifactoryPresent)
							new OutSystemStationConstructionFleet(sector,  system,  system, "neutral", planet, "omnifac").spawnFleet();

						if(!this.freeTransfer)
							new OutSystemStationConstructionFleet(sector,  system,  system, "neutral", planet, "storage").spawnFleet();

						this.builtOmnifactoryAndStorage = true;
						break;
					}
				}
			}
		}

		// Set player fleet to appropriate faction
		if(sector.getPlayerFleet().getFaction().getId().equalsIgnoreCase("player"))
		{
			sector.getPlayerFleet().setFaction(ExerelinData.getInstance().getPlayerFaction());
			SectorEntityToken station = ExerelinUtils.getRandomStationForFaction(ExerelinData.getInstance().getPlayerFaction(), sector);
			if(station != null)
				sector.getPlayerFleet().setLocation(station.getLocation().getX(),  station.getLocation().getY());

			if(playerMovedToSpawnLocation)
			{
				// Not initial fleet so set player fleet to have a faction specfic frigate
				CampaignFleetAPI dummyFleet = sector.createFleet(ExerelinData.getInstance().getPlayerFaction(), "exerelinGenericFleet");

				for(int i = 0; i < dummyFleet.getFleetData().getMembersListCopy().size(); i++)
				{
					if(((FleetMemberAPI)dummyFleet.getFleetData().getMembersListCopy().get(i)).isFrigate())
					{
						sector.getPlayerFleet().getFleetData().removeFleetMember(((FleetMemberAPI)sector.getPlayerFleet().getFleetData().getMembersListCopy().get(0)));
						sector.getPlayerFleet().getFleetData().addFleetMember(((FleetMemberAPI)dummyFleet.getFleetData().getMembersListCopy().get(i)));
						sector.getPlayerFleet().getCargo().addCrew(CargoAPI.CrewXPLevel.GREEN, (int)sector.getPlayerFleet().getFlagship().getMinCrew());
						break;
					}
				}
			}

			// Set starting or respawn cargo
			CargoAPI fleetCargo = sector.getPlayerFleet().getCargo();
			fleetCargo.clear();
			FleetMemberAPI fmAPI = sector.getPlayerFleet().getFlagship();
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
				sector.getPlayerFleet().setLocation(ExerelinData.getInstance().playerOffMapFleetSpawnLocation.getX(), ExerelinData.getInstance().playerOffMapFleetSpawnLocation.getY());
			}
			playerMovedToSpawnLocation = true;
		}

		// Events that run at set in-game intervals
		if (sector.getClock().getElapsedDaysSince(lastHeartbeat) >= heartbeatInterval)
		{
			doIntervalChecks(sector.getClock().getTimestamp());
			checkSynched();
		}
	}
}