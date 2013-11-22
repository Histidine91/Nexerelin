package exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFleet;

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
        String fleetId = "exerelinGenericFleet";

		java.util.List fleets = starSystemAPI.getFleets();

        // Get cont of current rebel exerelin.fleets in system
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

        if(!ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFaction.equalsIgnoreCase(""))
            rebelFAPI = Global.getSector().getFaction(ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFaction);

        if(!ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFleetId.equalsIgnoreCase(""))
            fleetId = ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFleetId;

        SectorEntityToken planet = (SectorEntityToken)starSystemAPI.getPlanets().get(ExerelinUtils.getRandomInRange(1, starSystemAPI.getPlanets().size() - 1));

        CampaignFleetAPI newRebelFleet;
        if(ExerelinConfig.getExerelinFactionConfig(factionLeaderId).customRebelFaction.equalsIgnoreCase(""))
            newRebelFleet = Global.getSector().createFleet(factionLeaderId, fleetId);
        else
            newRebelFleet = Global.getSector().createFleet(rebelFAPI.getId(), fleetId);

        ExerelinUtilsFleet.sortByFleetCost(newRebelFleet);

        // Reduce size of rebel fleet to contain ships of close to the same fleet points as the player fleet
        int playerFleetPoints = Global.getSector().getPlayerFleet().getFleetPoints();
        int targetFleetPoints = ExerelinUtils.getRandomInRange(playerFleetPoints, playerFleetPoints * 3);
        int rebelFleetPoints = newRebelFleet.getFleetPoints();

        // Remove best ships until we're closer to player fleet size
        while(rebelFleetPoints > (playerFleetPoints * 5) && newRebelFleet.getFleetData().getMembersListCopy().size() > 1){
            FleetMemberAPI member = (FleetMemberAPI)newRebelFleet.getFleetData().getMembersListCopy().get(0);
            newRebelFleet.getFleetData().removeFleetMember(member);
            rebelFleetPoints -= member.getFleetPointCost();
        }

        // Remove random ships until we're within target fleet size
        // This should provide a fairly decent variety of fleet compositions
        while (rebelFleetPoints > targetFleetPoints && newRebelFleet.getFleetData().getMembersListCopy().size() > 1){
            List rebelFleetMembers = newRebelFleet.getFleetData().getMembersListCopy();
            FleetMemberAPI member = (FleetMemberAPI)rebelFleetMembers.get(ExerelinUtils.getRandomInRange(0, rebelFleetMembers.size() - 1));
            newRebelFleet.getFleetData().removeFleetMember(member);
            rebelFleetPoints -= member.getFleetPointCost();
        }

        // Cap rabel fleet size at 4 as we don't want rebels to take over the system
        while(newRebelFleet.getFleetData().getMembersListCopy().size() > 4)
        {
            List rebelFleetMembers = newRebelFleet.getFleetData().getMembersListCopy();
            FleetMemberAPI member = (FleetMemberAPI)rebelFleetMembers.get(ExerelinUtils.getRandomInRange(0, rebelFleetMembers.size() - 1));
            newRebelFleet.getFleetData().removeFleetMember(member);
        }

        // Add cargo ships if this fleet has capital or cruiser class ships, or 33% of the time for smaller exerelin.fleets
        //if (newRebelFleet.getNumCapitals() > 0 || newRebelFleet.getNumCruisers() > 0 || (ExerelinUtils.getRandomInRange(0, 2) == 0 ))
            ExerelinUtils.addFreightersToFleet(newRebelFleet); // Needs freighters due to resupply issues

        ExerelinUtils.resetFleetCargoToDefaults(newRebelFleet, 0.3f, 0.1f, CargoAPI.CrewXPLevel.REGULAR);
        ExerelinUtilsFleet.sortByHullSize(newRebelFleet);

        newRebelFleet.setFaction(rebelFAPI.getId());
        newRebelFleet.setName(ExerelinConfig.getExerelinFactionConfig(factionLeaderId).rebelFleetSuffix);

        // Make rebel exerelin.fleets more willing to engage in combat
        if(((FleetMemberAPI)newRebelFleet.getFleetData().getMembersListCopy().get(0)).getHullSpec().getHullSize().compareTo(ShipAPI.HullSize.DESTROYER) >= 0)
            newRebelFleet.getCommander().setPersonality("aggressive");
        else
            newRebelFleet.getCommander().setPersonality("fearless");

        newRebelFleet.addAssignment(FleetAssignment.ATTACK_LOCATION, ExerelinUtils.getRandomStationInSystemForFaction(factionLeaderId, starSystemAPI), 90);
        newRebelFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, planet, 60);

        newRebelFleet.setPreferredResupplyLocation(planet);

        //starSystemAPI.spawnFleet(planet, 0, 0, newRebelFleet);
        SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(planet, 0, 0, newRebelFleet));
        //System.out.println("EVENT: Spawned rebel fleet in " + starSystemAPI.getName());
	}
}






