package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;
import java.util.List;

public class EventStationExplosion extends EventBase
{
	public EventStationExplosion()
	{
		setType(this.getClass().getName());
	}

	public void causeExplosion(StarSystemAPI starSystemAPI)
	{
        SystemStationManager manager = SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getSystemStationManager();
		StationRecord[] stations = manager.getStationRecords();
        StationRecord station = null;
		int attempts = 0;

		while(station == null && attempts < 20)
		{
			station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];
			if(station.getOwner() == null || station.getOwner().getFactionId().equalsIgnoreCase(manager.getFactionLoser()))
				station = null;
            attempts++;
		}

		if(station != null)
		{
			float efficiency = (float)ExerelinUtils.getRandomInRange(1,9)/10f;
			String accidentType = "";
			if(efficiency < 0.2)
				accidentType = "catastrophic";
			else if(efficiency < 0.5)
				accidentType = "major";
			else
				accidentType = "minor";

            // Don't allow major or catastrophic accidents on a faction's last station
            if (manager.getNumStationsOwnedByFaction(station.getOwner().getFactionId()) == 1)
                accidentType = "minor";

			if(!accidentType.equalsIgnoreCase("catastrophic"))
			{
                if(ExerelinUtils.isPlayerInSystem(starSystemAPI))
                {
                    if(station.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
                        ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and is operating at reduced efficiency!", Color.magenta);
                    else
                        ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and is operating at reduced efficiency.");
                }

				station.setEfficiency(efficiency);

				// Clear the majority of resources/weapons/ships
				CargoAPI stationCargo = station.getStationToken().getCargo();
				ExerelinUtils.decreaseCargo(stationCargo, "fuel", (int)(stationCargo.getFuel()*0.9));
				ExerelinUtils.decreaseCargo(stationCargo, "supplies", (int)(stationCargo.getSupplies()*0.9));
				ExerelinUtils.decreaseCargo(stationCargo, "marines", (int)(stationCargo.getMarines()*0.9));
				ExerelinUtils.decreaseCargo(stationCargo, "crewRegular", (int)(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR)*0.9));

				List ships = stationCargo.getMothballedShips().getMembersListCopy();
				ExerelinUtils.removeRandomShipsFromCargo(stationCargo,  (int)(ships.size()*0.9));

				List weapons =  stationCargo.getWeapons();
				ExerelinUtils.removeRandomWeaponStacksFromCargo(stationCargo,  (int)(weapons.size()*0.9));
			}
			else
			{
                if(ExerelinUtils.isPlayerInSystem(starSystemAPI))
                {
                    if(station.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
                        ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and has been abandoned!", Color.magenta);
                    else
                        ExerelinUtilsMessaging.addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and has been abandoned.");
                }

				station.setOwner(null, false, false);
				station.getStationToken().setFaction("abandoned");
				station.clearCargo();
				station.setEfficiency(efficiency);
			}
		}
	}
}
