package exerelin.utilities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exerelin.ExerelinUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ExerelinUtilsFleet
{
    public static void sortByFleetCost(CampaignFleetAPI fleet)
    {
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

    public static String getRandomVariantIdForFactionByHullsize(String factionId, ShipAPI.HullSize hullSize)
    {
        ExerelinFactionConfig exerelinFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        if(exerelinFactionConfig == null)
        {
            System.out.println("EXERELIN ERROR: Couldn't get random variant for: " + factionId);
            return "";
        }

        switch (hullSize) {
            case FIGHTER:
                return exerelinFactionConfig.fighterWings.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.fighterWings.size() - 1));
            case FRIGATE:
                return exerelinFactionConfig.frigateVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.frigateVariants.size() - 1));
            case DESTROYER:
                return exerelinFactionConfig.destroyerVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.destroyerVariants.size() - 1));
            case CRUISER:
                return exerelinFactionConfig.cruiserVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.cruiserVariants.size() - 1));
            case CAPITAL_SHIP:
                return exerelinFactionConfig.capitalVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.capitalVariants.size() - 1));
            default:
                return exerelinFactionConfig.frigateVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.fighterWings.size() - 1));
        }
    }

    public static String getRandomVariantIdForFaction(String factionId)
    {
        int rand = ExerelinUtils.getRandomInRange(1, 5);
        ShipAPI.HullSize hullSize = ShipAPI.HullSize.FIGHTER;

        switch(rand){
            case 1:
                hullSize = ShipAPI.HullSize.FIGHTER;
            case 2:
                hullSize = ShipAPI.HullSize.FRIGATE;
            case 3:
                hullSize = ShipAPI.HullSize.DESTROYER;
            case 4:
                hullSize = ShipAPI.HullSize.CRUISER;
            case 5:
                hullSize = ShipAPI.HullSize.CAPITAL_SHIP;
        }

        return ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, hullSize);
    }

    public static Boolean doesFactionHaveVariantOfHullsize(String factionId, ShipAPI.HullSize hullSize)
    {
        ExerelinFactionConfig exerelinFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        if(exerelinFactionConfig == null)
        {
            System.out.println("EXERELIN ERROR: Failed hullsize check for: " + factionId);
            return false;
        }

        switch (hullSize) {
            case FIGHTER:
                return exerelinFactionConfig.fighterWings.size() > 0;
            case FRIGATE:
                return exerelinFactionConfig.frigateVariants.size() > 0;
            case DESTROYER:
                return exerelinFactionConfig.destroyerVariants.size() > 0;
            case CRUISER:
                return exerelinFactionConfig.cruiserVariants.size() > 0;
            case CAPITAL_SHIP:
                return exerelinFactionConfig.capitalVariants.size() > 0;
            default:
                return exerelinFactionConfig.frigateVariants.size() > 0;
        }

    }
}
