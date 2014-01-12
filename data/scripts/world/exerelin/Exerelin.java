package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import exerelin.commandQueue.CommandQueue;
import exerelin.*;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinMessageManager;

@SuppressWarnings("unchecked")
public class Exerelin //implements SectorGeneratorPlugin
{
	public void generate(SectorAPI sector)
	{
        System.out.println("Starting setup...");

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

        // Build the sector manager
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
		this.initStationAttackFleets(sector);

		// Add trader spawns
		this.initTraderSpawns(sector);

        // Remove any data stored in ExerelinSetupData
        ExerelinSetupData.resetInstance();

        System.out.println("Finished generation and setup...");
	}

	private void initStationAttackFleets(SectorAPI sector)
	{
		String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
		ExerelinUtils.shuffleStringArray(factions); // Randomise order

		int numFactionsInitialStart = Math.min(factions.length - 1, ExerelinSetupData.getInstance().numStartFactions);
		for(int i = 0; i < numFactionsInitialStart; i = i + 1)
		{
			String factionId = factions[i];
			if(factionId.equalsIgnoreCase(ExerelinSetupData.getInstance().getPlayerFaction()))
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
}
