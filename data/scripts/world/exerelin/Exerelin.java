package data.scripts.world.exerelin;

import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import exerelin.commandQueue.CommandQueue;
import exerelin.*;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinMessageManager;

import java.util.Collections;

@SuppressWarnings("unchecked")
public class Exerelin //implements SectorGeneratorPlugin
{
	public void generate(SectorAPI sector)
	{
        System.out.println("Starting sector setup...");

        ExerelinSetupData.getInstance().resetAvailableFactions();

        // Set sector manager reference in persistent storage
        SectorManager sectorManager = new SectorManager();
        Global.getSector().getPersistentData().put("SectorManager", sectorManager);

        // Set starting conditions needed later for saving into the save file
        sectorManager.setPlayerFreeTransfer(ExerelinSetupData.getInstance().playerOwnedStationFreeTransfer);
        sectorManager.setRespawnFactions(ExerelinSetupData.getInstance().respawnFactions);
        sectorManager.setMaxFactions(ExerelinSetupData.getInstance().maxFactionsInExerelinAtOnce);
        sectorManager.setPlayerFactionId(ExerelinSetupData.getInstance().getPlayerFaction());
        sectorManager.setFactionsPossibleInSector(ExerelinSetupData.getInstance().getAvailableFactions(sector));
        sectorManager.setRespawnWaitDays(ExerelinSetupData.getInstance().respawnDelay);
        sectorManager.setBuildOmnifactory(ExerelinSetupData.getInstance().omniFacPresent);
        sectorManager.setMaxSystemSize(ExerelinSetupData.getInstance().maxSystemSize);
        sectorManager.setPlayerStartShipVariant(ExerelinSetupData.getInstance().getPlayerStartingShipVariant());
        sectorManager.setSectorPrePopulated(ExerelinSetupData.getInstance().isSectorPopulated);
        sectorManager.setSectorPartiallyPopulated(ExerelinSetupData.getInstance().isSectorPartiallyPopulated);

        // Setup the sector manager
        sectorManager.setupSectorManager(sector);

        // Build a message manager object and add to persistent storage
        ExerelinMessageManager exerelinMessageManager = new ExerelinMessageManager();
        Global.getSector().getPersistentData().put("ExerelinMessageManager", exerelinMessageManager);

        sectorManager.setupFactionDirectors();

        // Build and add a time mangager
        TimeManager timeManger = new TimeManager();
        Global.getSector().addScript(timeManger);

        // Add a EveryFrameScript command queue to handle synchronous-only events
        CommandQueue commandQueue = new CommandQueue();
        Global.getSector().addScript(commandQueue);
        sectorManager.setCommandQueue(commandQueue);

		// Set abandoned as enemy of every faction
        ExerelinConfig.loadSettings();
		this.initFactionRelationships(sector);

		// Build off map initial station attack exerelin.fleets in random systems
        if(sectorManager.isSectorPrePopulated())
            this.populateSector(Global.getSector(), sectorManager);
        else
		    this.initStationAttackFleets(sector);

		// Add trader spawns
		this.initTraderSpawns(sector);

        // Remove any data stored in ExerelinSetupData
        ExerelinSetupData.resetInstance();

        System.out.println("Finished sector setup...");
	}

	private void initStationAttackFleets(SectorAPI sector)
	{
		String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
		ExerelinUtils.shuffleStringArray(factions); // Randomise order

		int numFactionsInitialStart = Math.min(factions.length - 1, ExerelinSetupData.getInstance().numStartFactions);
		for(int i = 0; i < numFactionsInitialStart; i = i + 1)
		{
			String factionId = factions[i];
			if(!SectorManager.getCurrentSectorManager().getPlayerFactionId().equalsIgnoreCase("player") && factionId.equalsIgnoreCase(ExerelinSetupData.getInstance().getPlayerFaction()))
			{
				numFactionsInitialStart = numFactionsInitialStart + 1;
				continue;
			}

			int systemChosen = ExerelinUtils.getRandomInRange(0, SectorManager.getCurrentSectorManager().getSystemManagers().length - 1);
			StarSystemAPI systemAPI = SectorManager.getCurrentSectorManager().getSystemManagers()[systemChosen].getStarSystemAPI();
			OutSystemStationAttackFleet offMapSpawn = new OutSystemStationAttackFleet(sector, systemAPI, factionId, false);
            FactionDirector.getFactionDirectorForFactionId(factionId).setHomeSystem(systemAPI);
			offMapSpawn.spawnFleet(null, null);
		}
	}

	private void initFactionRelationships(SectorAPI sector)
	{
		String[] factions = ExerelinSetupData.getInstance().getAvailableFactions(sector);
		for(int i = 0; i < factions.length; i = i + 1)
		{
			sector.getFaction(factions[i]).setRelationship("abandoned", -1);
            sector.getFaction(factions[i]).setRelationship("rebel", -1);
            sector.getFaction(factions[i]).setRelationship("independent", 0);

            String customRebelFactionId = ExerelinConfig.getExerelinFactionConfig(factions[i]).customRebelFaction;
            if(!customRebelFactionId.equalsIgnoreCase(""))
            {
                for(int j = 0; j < factions.length; j = j + 1)
                {
                        sector.getFaction(factions[j]).setRelationship(customRebelFactionId, -1);
                }
            }
		}

		// Set independent and rebels to hate each other
		FactionAPI rebel = sector.getFaction("rebel");
		FactionAPI independent = sector.getFaction("independent");
		rebel.setRelationship(independent.getId(), -1);
        independent.setRelationship(rebel.getId(), -1);
	}

	private void initTraderSpawns(SectorAPI sector)
	{
		for(int j = 0; j < SectorManager.getCurrentSectorManager().getSystemManagers().length; j++)
		{
			StarSystemAPI systemAPI = SectorManager.getCurrentSectorManager().getSystemManagers()[j].getStarSystemAPI();
			for(int i = 0; i < Math.max(1, systemAPI.getOrbitalStations().size()/5); i++)
			{
				IndependantTraderSpawnPoint tgtsp = new IndependantTraderSpawnPoint(sector,  systemAPI,  ExerelinUtils.getRandomInRange(8,12), 1, systemAPI.createToken(0,0));
				systemAPI.addSpawnPoint(tgtsp);
			}
		}
	}

    private void populateSector(SectorAPI sector, SectorManager sectorManager)
    {
        boolean finishedPopulating = false;

        // Popuate a single station for each starting faction
        String[] factions = sectorManager.getFactionsPossibleInSector();
        int numFactionsInitialStart = Math.min(factions.length - 1, ExerelinSetupData.getInstance().numStartFactions);
        for(int i = 0; i < numFactionsInitialStart; i++)
        {
            String factionId = factions[i];
            if(factionId.equalsIgnoreCase(sectorManager.getPlayerFactionId()))
            {
                numFactionsInitialStart = numFactionsInitialStart + 1;
                continue;
            }

            // Find an available station
            List systems = sector.getStarSystems();
            Collections.shuffle(systems);
            systemLoop: for(int j = 0; j < systems.size(); j++)
            {
                StarSystemAPI systemAPI = (StarSystemAPI)systems.get(j);
                List stations = systemAPI.getOrbitalStations();
                for(int k = 0; k < stations.size(); k++)
                {
                    SectorEntityToken station = (SectorEntityToken)stations.get(k);
                    if(station.getFaction().getId().equalsIgnoreCase("abandoned"))
                    {
                        SystemStationManager systemStationManager = SystemManager.getSystemManagerForAPI(systemAPI).getSystemStationManager();
                        StationRecord stationRecord = systemStationManager.getStationRecordForToken(station);
                        stationRecord.setOwner(factionId, false, false);
                        System.out.println("Setting start station in " + systemAPI.getName() + " for: " + factionId);
                        FactionDirector.getFactionDirectorForFactionId(factionId).setHomeSystem(systemAPI);
                        break systemLoop;
                    }
                }
            }
        }

        // Add players start station
        if(!sectorManager.getPlayerFactionId().equalsIgnoreCase("player"))
        {
            List systems = sector.getStarSystems();
            Collections.shuffle(systems);
            systemLoop: for(int j = 0; j < systems.size(); j++)
            {
                StarSystemAPI systemAPI = (StarSystemAPI)systems.get(j);
                List stations = systemAPI.getOrbitalStations();
                for(int k = 0; k < stations.size(); k++)
                {
                    SectorEntityToken station = (SectorEntityToken)stations.get(k);
                    if(station.getFaction().getId().equalsIgnoreCase("abandoned"))
                    {
                        SystemStationManager systemStationManager = SystemManager.getSystemManagerForAPI(systemAPI).getSystemStationManager();
                        StationRecord stationRecord = systemStationManager.getStationRecordForToken(station);
                        stationRecord.setOwner(sectorManager.getPlayerFactionId(), false, false);
                        System.out.println("Setting start station in " + systemAPI.getName() + " for: " + sectorManager.getPlayerFactionId());
                        FactionDirector.getFactionDirectorForFactionId(sectorManager.getPlayerFactionId()).setHomeSystem(systemAPI);
                        break systemLoop;
                    }
                }
            }
        }

        // Populate rest of sector half or full
        int populated = 1;
        String[] factionsInSector = sectorManager.getFactionsInSector();

        while(!finishedPopulating)
        {
            for(int i = 0; i < factionsInSector.length; i++)
            {
                String factionId = factionsInSector[i];

                // Check home system for available stations
                StarSystemAPI homeSystem = FactionDirector.getFactionDirectorForFactionId(factionId).getHomeSystem();
                StarSystemAPI system = homeSystem;
                SectorEntityToken station = ExerelinUtils.getClosestEnemyStation(factionId, homeSystem, ExerelinUtils.getRandomStationInSystemForFaction(factionId, homeSystem));

                if(station == null)
                {
                    // Couldn't find station in home system so find closest available system
                    system = ExerelinUtils.getClosestSystemForFaction(homeSystem, factionId, -1f, -0.0001f);
                    if(system != null)
                        station = ExerelinUtils.getClosestEntityToSystemEntrance(system, factionId, -1f, -0.0001f);
                }

                if(station == null)
                    continue; // Move to next faction

                SystemStationManager systemStationManager = SystemManager.getSystemManagerForAPI(system).getSystemStationManager();
                StationRecord stationRecord = systemStationManager.getStationRecordForToken(station);
                stationRecord.setOwner(factionId, false, false);
                System.out.println("Setting station in " + system.getName() + " for: " + factionId);
            }

            populated++;

            if((sectorManager.isSectorPartiallyPopulated() && populated >= ((StarSystemAPI)Global.getSector().getStarSystems().get(1)).getOrbitalStations().size()/2)
                    || populated >= ((StarSystemAPI)Global.getSector().getStarSystems().get(1)).getOrbitalStations().size())
                finishedPopulating = true;
        }
    }
}
