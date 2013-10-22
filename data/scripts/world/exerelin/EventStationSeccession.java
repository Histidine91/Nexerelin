package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import data.scripts.world.exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;

public class EventStationSeccession extends EventBase
{

	public EventStationSeccession()
	{
		setType(this.getClass().getName());
	}

	public void makeStationSecedeToOutSystemFaction(StarSystemAPI starSystemAPI)
	{
		if(SystemManager.getSystemManagerForAPI(starSystemAPI).getSystemStationManager().getNumFactionsInSystem() >= SectorManager.getCurrentSectorManager().getMaxFactions())
		{
			System.out.println(SystemManager.getSystemManagerForAPI(starSystemAPI).getSystemStationManager().getNumFactionsInSystem() + " of " + SectorManager.getCurrentSectorManager().getMaxFactions() + " already in system.");
			return;
		}

		String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
		String[] factionsInSystem = SystemManager.getSystemManagerForAPI(starSystemAPI).getFactionsInSystem();
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

		StationRecord[] stations = SystemManager.getSystemManagerForAPI(starSystemAPI) .getSystemStationManager().getStationRecords();
		attempts = 0;
		StationRecord station = null;
		while(station == null & attempts < 20)
		{
			attempts = attempts + 1;
			station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];
			if(station.getOwner() == null
					|| !station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getFactionLeader())
					|| ExerelinData.getInstance().getSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getNumStationsOwnedByFaction(ExerelinData.getInstance().getSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getFactionLeader()) <= 1)
				station = null;
		}

		if(station != null)
		{
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!", Color.MAGENTA);
			else
				ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!");

			ExerelinData.getInstance().getSectorManager().getDiplomacyManager().declarePeaceWithAllFactions(factionId);
			ExerelinData.getInstance().getSectorManager().getDiplomacyManager().createWarIfNoneExists(factionId);
			station.setOwner(factionId,  false, false);

			station.getStationToken().getCargo().clear();
			station.getStationToken().getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 1600);
			station.getStationToken().getCargo().addMarines(800);
			station.getStationToken().getCargo().addFuel(1600);
			station.getStationToken().getCargo().addSupplies(6400);

			station.setEfficiency(3);

            SectorManager.getCurrentSectorManager().setLastFactionSpawnTime(Global.getSector().getClock().getTimestamp());
		}
	}
}
