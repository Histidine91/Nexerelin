package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import data.scripts.world.exerelin.commandQueue.CommandQueue;
import data.scripts.world.exerelin.utilities.ExerelinConfig;
import data.scripts.world.exerelin.utilities.ExerelinMessageManager;

@SuppressWarnings("unchecked")
public class Exerelin //implements SectorGeneratorPlugin
{
	public void generate(SectorAPI sector)
	{
        System.out.println("Starting setup...");

        ExerelinData.getInstance().resetAvailableFactions();

        // Build a sector manager to run things
        SectorManager sectorManager = new SectorManager(sector);

        // Set starting conditions needed later for saving into the save file
        sectorManager.setPlayerFreeTransfer(ExerelinData.getInstance().playerOwnedStationFreeTransfer);
        sectorManager.setRespawnFactions(ExerelinData.getInstance().respawnFactions);
        sectorManager.setMaxFactions(ExerelinData.getInstance().maxFactionsInExerelinAtOnce);
        sectorManager.setPlayerFactionId(ExerelinData.getInstance().getPlayerFaction());
        sectorManager.setFactionsPossibleInSector(ExerelinData.getInstance().getAvailableFactions(sector));
        sectorManager.setRespawnWaitDays(ExerelinData.getInstance().respawnDelay);
        sectorManager.setBuildOmnifactory(ExerelinData.getInstance().omniFacPresent);
        sectorManager.setMaxSystemSize(ExerelinData.getInstance().maxSystemSize);
        sectorManager.setPlayerStartShipVariant(ExerelinData.getInstance().getPlayerStartingShipVariant());

        // Set sector manager reference in cache and persistent storage
        ExerelinData.getInstance().setSectorManager(sectorManager);
        Global.getSector().getPersistentData().put("SectorManager", sectorManager);

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
		this.initFactionRelationships(sector);

		// Build off map initial station attack fleets in random systems
        ExerelinConfig.loadSettings(); // Needed for attack fleets
		this.initStationAttackFleets(sector);

		// Add trader spawns
		this.initTraderSpawns(sector);

        System.out.println("Finished generation and setup...");
	}

	private void initStationAttackFleets(SectorAPI sector)
	{
		String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
		ExerelinUtils.shuffleStringArray(factions); // Randomise order

		int numFactionsInitialStart = Math.min(factions.length - 1, ExerelinData.getInstance().numStartFactions);
		for(int i = 0; i < numFactionsInitialStart; i = i + 1)
		{
			String factionId = factions[i];
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
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
		String[] factions = ExerelinData.getInstance().getAvailableFactions(sector);
		for(int i = 0; i < factions.length; i = i + 1)
		{
			sector.getFaction(factions[i]).setRelationship("abandoned", -1);
            sector.getFaction(factions[i]).setRelationship("rebel", -1);
            sector.getFaction(factions[i]).setRelationship("independent", 0);
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
