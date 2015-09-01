package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;

@Deprecated
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
			factionId = (String) ExerelinUtils.getRandomArrayElement(factions);

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
			station = (StationRecord) ExerelinUtils.getRandomArrayElement(stations);
			if(station.getOwner() == null
					|| !station.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getFactionLeader())
					|| SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getNumStationsOwnedByFaction(SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getFactionLeader()) <= 1)
				station = null;
		}

		if(station != null)
		{
			if(factionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
				ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!", Color.MAGENTA);
			else
				ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!");

			SectorManager.getCurrentSectorManager().getDiplomacyManager().declarePeaceWithAllFactions(factionId);
			SectorManager.getCurrentSectorManager().getDiplomacyManager().createWarIfNoneExists(factionId);
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
