package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ExerelinUtilsFleet
{

    public static void sortByFleetCost(CampaignFleetAPI fleet){
        // local reference to be sorted
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Remove all members from the fleet
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        // Sort descending by fleet cost so that more expensive ships are first
        Collections.sort(initialFleetMembers, new Comparator<FleetMemberAPI>() {
            @Override
            public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
                return Float.compare(o2.getFleetPointCost(), o1.getFleetPointCost());
            }
        });

        // Re-add members to fleet from sorted list
        for (FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().addFleetMember(member);
    }

    public static void sortByHullSize(CampaignFleetAPI fleet)
    {
        // local reference to be sorted
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Remove all members from the fleet
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        // Sort descending by hull size so that larger hulls are first
        Collections.sort(initialFleetMembers, new Comparator<FleetMemberAPI>() {
            @Override
            public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
                return o2.getHullSpec().getHullSize().compareTo(o1.getHullSpec().getHullSize());
            }
        });

        // Re-add members to fleet from sorted list
        for (FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().addFleetMember(member);
    }
}
