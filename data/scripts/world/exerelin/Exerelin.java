package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import data.scripts.world.exerelin.commandQueue.CommandQueue;

@SuppressWarnings("unchecked")
public class Exerelin //implements SectorGeneratorPlugin
{
	public void generate(SectorAPI sector)
	{
        System.out.println("Starting setup...");

        /*for(int i = 0; i < sector.getStarSystems().size(); i++)
        {
            System.out.println(((StarSystemAPI) sector.getStarSystems().get(i)).getName());
            StarSystemAPI system = ((StarSystemAPI) sector.getStarSystems().get(i));

            for(int j = 0; j < system.getSpawnPoints().size(); j++)
            {
                SpawnPointPlugin spawnPointPlugin = (SpawnPointPlugin)system.getSpawnPoints().get(j);
                System.out.println("Removing spawnpoint");
                system.removeSpawnPoint(spawnPointPlugin);
            }

            for(int k = 0; k < system.getEntities(BaseSpawnPoint.class).size(); k++)
            {
                System.out.println("Found baseSpawnPoint");
            }

            for(int l = 0; l < system.getOrbitalStations().size(); l++)
            {
                SectorEntityToken station = (SectorEntityToken)system.getOrbitalStations().get(l);
                station.setFaction("abandoned");
            }
        }*/

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

        // Add sector manager to cache
        ExerelinData.getInstance().setSectorManager(sectorManager);

        sectorManager.setupFactionDirectors();

        // Build and add a time mangager
        TimeManager timeManger = new TimeManager();
        timeManger.sectorManagerRef = sectorManager;
        ((StarSystemAPI)sector.getStarSystems().get(0)).addSpawnPoint(timeManger);

        // Add a EveryFrameScript command queue to handle synchronous-only events
        CommandQueue commandQueue = new CommandQueue();
        Global.getSector().addScript(commandQueue);
        sectorManager.setCommandQueue(commandQueue);

		// Check that player picked faction is available
		this.checkPlayerFactionPick(sector);

		// Set abandoned as enemy of every faction
		this.initFactionRelationships(sector);

		// Build off map initial station attack fleets in random systems
		this.initStationAttackFleets(sector);

		// Add trader spawns
		this.initTraderSpawns(sector);

        System.out.println("Finished generation and setup...");
	}

	private void checkPlayerFactionPick(SectorAPI sector)
	{
		String[] availableFactions = ExerelinData.getInstance().getAvailableFactions(sector);
		Boolean pickOK = false;
		for(int i = 0; i < availableFactions.length; i = i + 1)
		{
			if(ExerelinData.getInstance().getPlayerFaction().equalsIgnoreCase(availableFactions[i]))
			pickOK = true;
		}

		if(!pickOK)
			ExerelinData.getInstance().resetPlayerFaction();
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

		// Set indpendant and rebels to hate each other
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
