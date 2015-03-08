package data.scripts.world.exerelin;

import java.util.List;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.FactionDirector;
import exerelin.SectorManager;
import exerelin.StationRecord;
import exerelin.SystemManager;
import exerelin.SystemStationManager;
import exerelin.utilities.ExerelinUtils;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;

import java.util.Collections;
import org.lwjgl.util.vector.Vector2f;

@SuppressWarnings("unchecked")
public class Exerelin //implements SectorGeneratorPlugin
{
	public static Logger log = Global.getLogger(Exerelin.class);
	
	public void generate(SectorAPI sector)
	{
		System.out.println("Starting sector setup...");
	
		ExerelinSetupData.getInstance().resetAvailableFactions();
	
		// Set sector manager reference in persistent storage
		/*
		SectorManager sectorManager = new SectorManager();
		Global.getSector().getPersistentData().put("SectorManager", sectorManager);
	
		// Set starting conditions needed later for saving into the save file
		sectorManager.setPlayerFreeTransfer(ExerelinSetupData.getInstance().playerOwnedStationFreeTransfer);
		sectorManager.setRespawnFactions(ExerelinSetupData.getInstance().respawnFactions);
		sectorManager.setMaxFactions(ExerelinSetupData.getInstance().maxFactionsInExerelinAtOnce);
		//sectorManager.setPlayerFactionId(ExerelinSetupData.getInstance().getPlayerFaction());
		sectorManager.setFactionsPossibleInSector(ExerelinSetupData.getInstance().getAvailableFactions(sector));
		sectorManager.setRespawnWaitDays(ExerelinSetupData.getInstance().respawnDelay);
		sectorManager.setBuildOmnifactory(ExerelinSetupData.getInstance().omniFacPresent);
		sectorManager.setMaxSystemSize(ExerelinSetupData.getInstance().maxSystemSize);
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
		*/
		
		ExerelinConfig.loadSettings();
		
		String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
		PlayerFactionStore.setPlayerFactionId(selectedFactionId);
		
		// Populate sector
		//this.populateSector(Global.getSector(), sectorManager);
	
		// Add trader spawns
		//this.initTraderSpawns(sector);
		
		// set player start location at the largest faction market with a military base
		//setPlayerSpawnLocation();
	
		System.out.println("Finished sector setup...");
	}
		
	private void setPlayerSpawnLocation()
	{
		SectorAPI sector = Global.getSector();
		String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		int size = 0;
		MarketAPI targetMarket = null;
		for (MarketAPI market : markets)
		{
			if (!market.getFactionId().equals(selectedFactionId))
				continue;
			if (targetMarket == null)
				targetMarket = market;
			else if (market.hasCondition("military_base"))
			{
				if (size < market.getSize()) {
					size = market.getSize();
					targetMarket = market;
				}
			}
		}
		if (targetMarket != null)
		{
			LocationAPI loc = targetMarket.getContainingLocation();
			final MarketAPI market = targetMarket;
			Vector2f marketPos = market.getPrimaryEntity().getLocation();
			/*
			EveryFrameScript script = new EveryFrameScript() {
				private boolean done = false;

				@Override
				public boolean runWhilePaused() {
					return false;
				}
				@Override
				public boolean isDone() {
					return done;
				}
				@Override
				public void advance(float amount) {
					CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
					if (playerFleet != null)
					{
						playerFleet.setCircularOrbit(market.getPrimaryEntity(), 0, 200, 60);
						done = true;
					}
				}
			};
			Global.getSector().addScript(script);
			*/
			sector.setRespawnLocation(loc);
			sector.getRespawnCoordinates().set(marketPos.x, marketPos.y);		
		}
	}

	private void populateSector(SectorAPI sector, SectorManager sectorManager)
	{
		boolean finishedPopulating = false;
		int populated = 0;
	
		// Popuate a single station for each starting faction
		String[] factions = sectorManager.getFactionsPossibleInSector();
		int numFactionsInitialStart = Math.min(factions.length - 1, ExerelinSetupData.getInstance().numStartFactions);
	
		// If player is starting unaligned add one more starting faction
		if(SectorManager.getCurrentSectorManager().isPlayerInPlayerFaction())
		numFactionsInitialStart++;
	
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
						populated++;
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
						populated++;
						break systemLoop;
					}
				}
			}
		}
	
		// Populate rest of sector half or full
	
		String[] factionsInSector = sectorManager.getFactionsInSector();
	
		// If empty sector, only leave one station
		if(!ExerelinSetupData.getInstance().isSectorPopulated)
			finishedPopulating = true;
	
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
				populated++;

				if((sectorManager.isSectorPartiallyPopulated() && populated > (((StarSystemAPI)Global.getSector().getStarSystems().get(0)).getOrbitalStations().size()*Global.getSector().getStarSystems().size())/2)
					|| populated >= ((StarSystemAPI)Global.getSector().getStarSystems().get(0)).getOrbitalStations().size()*Global.getSector().getStarSystems().size())
				{
					finishedPopulating = true;
					break;
				}
			}
		}
	}
}
