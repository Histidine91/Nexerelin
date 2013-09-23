package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.awt.*;
import java.util.List;

public class EventRebelFleetSpawn extends EventBase
{

	public EventRebelFleetSpawn()
	{
		setType(this.getClass().getName());
	}

	public void spawnRebelFleet(StarSystemAPI starSystemAPI)
	{
		// DEFAULTS
        FactionAPI rebelFAPI = Global.getSector().getFaction("rebel");

		System.out.println("EVENT: Spawning rebel fleet in " + starSystemAPI.getName());

		java.util.List fleets = starSystemAPI.getFleets();

        // Get cont of current rebel fleets in system
        int rebelFleetCount = 0;
		for(int i = 0; i < fleets.size(); i++)
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);
            if(fleet.getFaction().getId().equalsIgnoreCase(rebelFAPI.getId()))
                rebelFleetCount++;
		}

        if(rebelFleetCount > fleets.size() / 4)
            return;

        String factionLeaderId = SectorManager.getCurrentSectorManager().getSystemManager(starSystemAPI).getLeadingFactionId();
        if(factionLeaderId == null || factionLeaderId.equalsIgnoreCase(""))
            return;

        SectorEntityToken planet = (SectorEntityToken)starSystemAPI.getPlanets().get(ExerelinUtils.getRandomInRange(1, starSystemAPI.getPlanets().size() - 1));

        CampaignFleetAPI newRebelFleet = Global.getSector().createFleet(factionLeaderId, "exerelinGenericFleet");

        // Reduce size of rebel fleet
        List members = newRebelFleet.getFleetData().getMembersListCopy();
        int numToRemove = ExerelinUtils.getRandomInRange((members.size()/3)*2, members.size() - 2);
        for(int i = 0; i < numToRemove; i++)
        {
            List membersUpdated = newRebelFleet.getFleetData().getMembersListCopy();
            FleetMemberAPI toRemove = (FleetMemberAPI)membersUpdated.get(ExerelinUtils.getRandomInRange(0, membersUpdated.size() - 1));
            newRebelFleet.getFleetData().removeFleetMember(toRemove);
        }

        ExerelinUtils.addFreightersToFleet(newRebelFleet);
        ExerelinUtils.resetFleetCargoToDefaults(newRebelFleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);
        newRebelFleet.setFaction(rebelFAPI.getId());
        newRebelFleet.setName("Dissenter Fleet");

        starSystemAPI.spawnFleet(planet, 0, 0, newRebelFleet);

        newRebelFleet.addAssignment(FleetAssignment.ATTACK_LOCATION, (SectorEntityToken)starSystemAPI.getOrbitalStations().get(ExerelinUtils.getRandomInRange(0, starSystemAPI.getOrbitalStations().size() - 1)), 90);
        newRebelFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, planet, 60);
	}
}






