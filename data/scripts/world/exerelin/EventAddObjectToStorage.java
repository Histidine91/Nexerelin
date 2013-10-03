package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.awt.*;
import java.util.List;

public class EventAddObjectToStorage extends EventBase
{

	public EventAddObjectToStorage()
	{
		setType(this.getClass().getName());
	}

	public void addAgentToStorageFacility()
	{
        for(int j = 0; j < Global.getSector().getStarSystems().size(); j++)
        {
            List stations = ((StarSystemAPI)Global.getSector().getStarSystems().get(j)).getOrbitalStations();

            for(int i = 0; i < stations.size(); i++)
            {
                SectorEntityToken station = (SectorEntityToken)stations.get(i);
                if(station.getFullName().contains("Storage"))
                {
                    station.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, "agent", 1);
                    System.out.println("EVENT: An agent is available at your storage facility.");
                    Global.getSector().addMessage("An agent is available at your storage facility.", Color.green);
                }
            }
        }
	}

	public void addPrisonerToStorageFacility()
	{
        for(int j = 0; j < Global.getSector().getStarSystems().size(); j++)
        {
            List stations = ((StarSystemAPI)Global.getSector().getStarSystems().get(j)).getOrbitalStations();

            for(int i = 0; i < stations.size(); i++)
            {
                SectorEntityToken station = (SectorEntityToken)stations.get(i);
                if(station.getFullName().contains("Storage"))
                {
                    station.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, "prisoner", 1);
                    System.out.println("EVENT: A prisoner of war has been jailed at your storage facility.");
                    Global.getSector().addMessage("A prisoner of war has been jailed at your storage facility.", Color.green);
                }
            }
        }
	}

    public void addSabateurToStorageFacility()
    {
        for(int j = 0; j < Global.getSector().getStarSystems().size(); j++)
        {
            List stations = ((StarSystemAPI)Global.getSector().getStarSystems().get(j)).getOrbitalStations();

            for(int i = 0; i < stations.size(); i++)
            {
                SectorEntityToken station = (SectorEntityToken)stations.get(i);
                if(station.getFullName().contains("Storage"))
                {
                    station.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, "saboteur", 1);
                    System.out.println("EVENT: A sabateur is available at your storage facility.");
                    Global.getSector().addMessage("A sabateur is available at your storage facility.", Color.green);
                }
            }
        }
    }
}
