package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import data.scripts.world.exerelin.ExerelinData;
import data.scripts.world.exerelin.StationRecord;

import java.awt.*;
import java.util.List;

public class EventStationExplosion extends EventBase
{
	public EventStationExplosion()
	{
		setType(this.getClass().getName());
	}

	public void causeExplosion()
	{
		StationRecord[] stations = ExerelinData.getInstance().systemManager.stationManager.getStationRecords();
		int attempts = 0;
		StationRecord station = null;
		while(station == null & attempts < 20)
		{
			attempts = attempts + 1;
			station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];
			if(station.getOwner() == null || station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().systemManager.stationManager.getFactionLoser()))
				station = null;
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
			if(!accidentType.equalsIgnoreCase("catastrophic"))
			{
				if(station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					Global.getSector().addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and is operating at reduced efficiency!", Color.magenta);
				else
					Global.getSector().addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and is operating at reduced efficiency.");
				System.out.println("EVENT : " + accidentType + " station accident at " + station.getStationToken().getFullName());

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
				if(station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					Global.getSector().addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and has been abandoned!", Color.magenta);
				else
					Global.getSector().addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and has been abandoned.");
				System.out.println("EVENT : " + accidentType + " station accident at " + station.getStationToken().getFullName());

				station.setOwner(null, false, false);
				station.getStationToken().setFaction("abandoned");
				station.clearCargo();
				station.setEfficiency(efficiency);
			}
		}
	}
}
