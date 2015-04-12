package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;
import java.util.List;

@SuppressWarnings("unchecked")
@Deprecated
public class EventRebelInsurrection extends EventBase
{

	public EventRebelInsurrection()
	{
		setType(this.getClass().getName());
	}

	public void causeRebellionAgainstLeadingFaction(StarSystemAPI starSystemAPI)
	{
		String rebelAgainseFaction = "";

        // Get a target faction
        rebelAgainseFaction = SystemManager.getSystemManagerForAPI(starSystemAPI).getSystemStationManager().getFactionLeader();

        if(rebelAgainseFaction == null)
            return;

        if(SystemManager.getSystemManagerForAPI(starSystemAPI).getSystemOwnership(rebelAgainseFaction) != 1f)
            return; // If not total ownership, then don't rebel

		// Warn player
        if(ExerelinUtils.isPlayerInSystem(starSystemAPI))
        {
            if(rebelAgainseFaction.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
                ExerelinUtilsMessaging.addMessage("A number of " + Global.getSector().getFaction(rebelAgainseFaction).getDisplayName() + " fleets are attempting an insurrection!", Color.magenta);
            else
                ExerelinUtilsMessaging.addMessage("A number of " + Global.getSector().getFaction(rebelAgainseFaction).getDisplayName() + " fleets are attempting an insurrection!");
        }

		List fleets = starSystemAPI.getFleets();

		for(int i = 0; i < fleets.size(); i++)
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);

			// Faction exerelin.fleets have a chance to rebel
			if(fleet.getFaction().getId().equalsIgnoreCase(rebelAgainseFaction) && ExerelinUtils.getRandomInRange(0, 1) == 0)
			{
				if(fleet.getFullName().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFullName()))
					continue; // Skip the players fleet

				fleet.setFaction("rebel");

				String fleetName = "Insurrection Fleet";

				fleet.setName(fleetName);

				fleet.clearAssignments();
				SectorEntityToken station = ExerelinUtils.getRandomStationInSystemForFaction(rebelAgainseFaction, starSystemAPI);
				fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, station, 30);
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, ExerelinUtils.getRandomOffMapPoint(starSystemAPI), 30);
			}
		}
	}
}






