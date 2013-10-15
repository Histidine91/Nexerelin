package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import data.scripts.world.exerelin.utilities.ExerelinConfig;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFleet;

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
        ExerelinUtilsFleet.sortByFleetCost(newRebelFleet);

        // Reduce size of rebel fleet to contain ships of close to the same fleet points as the player fleet
        int playerFleetPoints = Global.getSector().getPlayerFleet().getFleetPoints();
        int targetFleetPoints = ExerelinUtils.getRandomInRange(playerFleetPoints, playerFleetPoints * 3);
        int rebelFleetPoints = newRebelFleet.getFleetPoints();

        // Remove best ships until we're closer to player fleet size
        while(rebelFleetPoints > (playerFleetPoints * 5)){
            FleetMemberAPI member = (FleetMemberAPI)newRebelFleet.getFleetData().getMembersListCopy().get(0);
            newRebelFleet.getFleetData().removeFleetMember(member);
            rebelFleetPoints -= member.getFleetPointCost();
        }

        // Remove random ships until we're within target fleet size
        // This should provide a fairly decent variety of fleet compositions
        while (rebelFleetPoints > targetFleetPoints){
            List rebelFleetMembers = newRebelFleet.getFleetData().getMembersListCopy();
            FleetMemberAPI member = (FleetMemberAPI)rebelFleetMembers.get(ExerelinUtils.getRandomInRange(0, rebelFleetMembers.size() - 1));
            newRebelFleet.getFleetData().removeFleetMember(member);
            rebelFleetPoints -= member.getFleetPointCost();
        }

        // Cap rabel fleet size at 6 as we don't want rebels to take over the system
        if(newRebelFleet.getFleetData().getMembersListCopy().size() > 6)
        {
            for(int i = 0; i < newRebelFleet.getFleetData().getMembersListCopy().size() - 6; i++)
            {
                List rebelFleetMembers = newRebelFleet.getFleetData().getMembersListCopy();
                FleetMemberAPI member = (FleetMemberAPI)rebelFleetMembers.get(ExerelinUtils.getRandomInRange(0, rebelFleetMembers.size() - 1));
                newRebelFleet.getFleetData().removeFleetMember(member);
            }
        }

        // Add cargo ships if this fleet has capital or cruiser class ships, or 33% of the time for smaller fleets
        if (newRebelFleet.getNumCapitals() > 0 || newRebelFleet.getNumCruisers() > 0 || (ExerelinUtils.getRandomInRange(0, 2) == 0 ))
            ExerelinUtils.addFreightersToFleet(newRebelFleet);

        ExerelinUtils.resetFleetCargoToDefaults(newRebelFleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);
        ExerelinUtilsFleet.sortByHullSize(newRebelFleet);

        newRebelFleet.setFaction(rebelFAPI.getId());
        newRebelFleet.setName(ExerelinConfig.getExerelinFactionConfig(factionLeaderId).rebelFleetSuffix);

        // Make rebel fleets more willing to engage in combat
        if(((FleetMemberAPI)newRebelFleet.getFleetData().getMembersListCopy().get(0)).getHullSpec().getHullSize().compareTo(ShipAPI.HullSize.DESTROYER) >= 0)
            newRebelFleet.getCommander().setPersonality("aggressive");
        else
            newRebelFleet.getCommander().setPersonality("fearless");

        newRebelFleet.addAssignment(FleetAssignment.ATTACK_LOCATION, ExerelinUtils.getRandomStationInSystemForFaction(factionLeaderId, starSystemAPI), 90);
        newRebelFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, planet, 60);
        //starSystemAPI.spawnFleet(planet, 0, 0, newRebelFleet);
        SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(planet, 0, 0, newRebelFleet));
        //System.out.println("EVENT: Spawned rebel fleet in " + starSystemAPI.getName());
	}
}






