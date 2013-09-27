package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.List;

public class ExerelinUtilsFleet
{
    public static void fleetOrderReset(CampaignFleetAPI fleet)
    {
        // A temporary list to store re-ordered members in
        List<FleetMemberAPI> tempMemberList = new ArrayList<FleetMemberAPI>();

        // local reference
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Put a reference to fleet members into the temp list in the order we want them
        for(int i = 0; i < 5; i++)
        {
            for(int j = 0; j < initialFleetMembers.size(); j++)
            {
                if(i == 0 && initialFleetMembers.get(j).isCapital())
                    tempMemberList.add(initialFleetMembers.get(j));

                if(i == 1 && initialFleetMembers.get(j).isCruiser())
                    tempMemberList.add(initialFleetMembers.get(j));

                if(i == 2 && initialFleetMembers.get(j).isDestroyer())
                    tempMemberList.add(initialFleetMembers.get(j));

                if(i == 3 && initialFleetMembers.get(j).isFrigate())
                    tempMemberList.add(initialFleetMembers.get(j));

                if(i == 4 && initialFleetMembers.get(j).isFighterWing())
                    tempMemberList.add(initialFleetMembers.get(j));
            }
        }

        // Remove all members from fleet
        for(int i = 0; i < initialFleetMembers.size(); i++)
            fleet.getFleetData().removeFleetMember(initialFleetMembers.get(i));

        // Re-add members to fleet from temp list
        for(int i = 0; i < tempMemberList.size(); i++)
            fleet.getFleetData().addFleetMember(tempMemberList.get(i));
    }
}
