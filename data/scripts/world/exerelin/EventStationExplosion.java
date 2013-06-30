package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import data.scripts.world.exerelin.ExerelinData;
import data.scripts.world.exerelin.StationRecord;

import java.awt.*;
import java.util.List;

public class EventStationExplosion
{
	public EventStationExplosion()
	{

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
			if(efficiency == 0.1)
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

				CargoAPI stationCargo = station.getStationToken().getCargo();
				ExerelinUtils.decreaseCargo(stationCargo, "fuel", (int)(stationCargo.getFuel()*0.9));
				ExerelinUtils.decreaseCargo(stationCargo, "supplies", (int)(stationCargo.getSupplies()*0.9));
				ExerelinUtils.decreaseCargo(stationCargo, "marines", (int)(stationCargo.getMarines()*0.9));
				ExerelinUtils.decreaseCargo(stationCargo, "crewRegular", (int)(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR)*0.9));

				List ships = stationCargo.getMothballedShips().getMembersListCopy();
				int i = 0;
				while(i < ships.size())
				{
					if(ExerelinUtils.getRandomInRange(0,9) > 0)
						ships.remove(i);

					i++;
				}

				List weapons =  stationCargo.getWeapons();
				while(i < weapons.size())
				{
					if(ExerelinUtils.getRandomInRange(0,9) > 0)
						weapons.remove(i);

					i++;
				}
			}
			else
			{
				station.setOwner(null, false, false);
				station.getStationToken().setFaction("abandoned");
				station.clearCargo();
				station.setEfficiency(0.1f);

				if(station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					Global.getSector().addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and has been abandoned!", Color.magenta);
				else
					Global.getSector().addMessage(station.getStationToken().getFullName() + " has suffered a " + accidentType + " accident and has been abandoned.");
				System.out.println("EVENT : " + accidentType + " station accident at " + station.getStationToken().getFullName());

			}
		}
	}
}
