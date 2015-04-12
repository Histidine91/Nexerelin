package exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;
import java.util.List;

@Deprecated
public class EventAddObjectToStorage extends EventBase
{

	public EventAddObjectToStorage()
	{
		setType(this.getClass().getName());
	}

	/*public void addAgentToStorageFacility()
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
                    ExerelinUtilsMessaging.addMessage("An agent is available at your storage facility.", Color.green);
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
                    ExerelinUtilsMessaging.addMessage("A prisoner of war has been jailed at your storage facility.", Color.green);
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
                    ExerelinUtilsMessaging.addMessage("A sabateur is available at your storage facility.", Color.green);
                }
            }
        }
    }

    public void addEliteShipToStorage()
    {
        for(int j = 0; j < Global.getSector().getStarSystems().size(); j++)
        {
            List stations = ((StarSystemAPI)Global.getSector().getStarSystems().get(j)).getOrbitalStations();

            for(int i = 0; i < stations.size(); i++)
            {
                SectorEntityToken station = (SectorEntityToken)stations.get(i);
                if(station.getFullName().contains("Storage"))
                {
                    CampaignFleetAPI eliteFleet = Global.getSector().createFleet(Global.getSector().getPlayerFleet().getFaction().getId(), "exerelinEliteFleet");
                    station.getCargo().getMothballedShips().addFleetMember(eliteFleet.getFleetData().getMembersListCopy().get(0));
                    ExerelinUtilsMessaging.addMessage("A powerful ship has been gifted to you and is available at your storage facility.", Color.green);
                }
            }
        }
    }*/
}
