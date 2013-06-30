package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.awt.*;

public class EventStationSeccession
{
	SectorAPI sectorAPI;
	StarSystemAPI starSystemAPI;

	public EventStationSeccession(SectorAPI sector, StarSystemAPI system)
	{
		sectorAPI = sector;
		starSystemAPI = system;
	}

	public void makeStationSecedeToOutSystemFaction()
	{
		String[] factions = ExerelinData.getInstance().getAvailableFactions(sectorAPI);
		String[] factionsInSystem = ExerelinUtils.getFactionsInSystem(starSystemAPI);
		int attempts = 0;
		String factionId = "";
		while((factionId.equalsIgnoreCase("")) && attempts < 20)
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
				factionId = "";
		}

		if(factionId.equalsIgnoreCase(""))
			return; // No faction has 0 stations in system

		StationRecord[] stations = ExerelinData.getInstance().systemManager.stationManager.getStationRecords();
		attempts = 0;
		StationRecord station = null;
		while(station == null & attempts < 20)
		{
			attempts = attempts + 1;
			station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];
			if(station.getOwner() == null
					|| !station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().systemManager.stationManager.getFactionLeader())
					|| ExerelinData.getInstance().systemManager.stationManager.getNumStationsOwnedByFaction(ExerelinData.getInstance().systemManager.stationManager.getFactionLeader()) <= 1)
				station = null;
		}

		if(station != null)
		{
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				sectorAPI.addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!", Color.MAGENTA);
			else
				sectorAPI.addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!");

			System.out.println("EVENT : Station secession at " + station.getStationToken().getFullName() + " to " + factionId + "(out system)");

			ExerelinData.getInstance().systemManager.diplomacyManager.declarePeaceWithAllFactions(factionId);
			ExerelinData.getInstance().systemManager.diplomacyManager.createWarIfNoneExists(factionId);
			station.setOwner(factionId,  false, false);

			station.getStationToken().getCargo().clear();
			station.getStationToken().getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 1600);
			station.getStationToken().getCargo().addMarines(800);
			station.getStationToken().getCargo().addFuel(1600);
			station.getStationToken().getCargo().addSupplies(3200);

			station.setEfficiency(3);
		}
	}
}
