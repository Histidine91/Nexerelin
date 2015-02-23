package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import exerelin.SectorManager;

import java.awt.*;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class ExerelinCheck
{
    public static Boolean isToreUpPlentyInstalled()
    {
        try
        {
            Global.getSettings().getScriptClassLoader().loadClass("data.scripts.TUPModPlugin");
            System.out.println("EXERELIN: tore up plenty installed");
            return true;
        }
        catch (ClassNotFoundException ex)
        {
            System.out.println("EXERELIN: tore up plenty not installed");
            return false;
        }
    }

    public static void checkModCompatability()
    {
        System.out.println("Checking installed factions for missing ships");

        List<String> factions = new ArrayList<String>();

        factions.addAll(Arrays.asList(SectorManager.getCurrentSectorManager().getFactionsPossibleInSector()));

        if(ExerelinCheck.isToreUpPlentyInstalled())
            factions.add("scavengers");

        List<String> usedHullIds = new ArrayList<String>();

        for(String faction : factions)
        {
            ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(faction);
            Boolean error = false;

            System.out.println("Checking " + factionConfig.factionId);

            error = attemptToCreateFleetMember(factionConfig.freighterVariants, false, usedHullIds) || error;
            error = attemptToCreateFleetMember(factionConfig.tankerVariants, false, usedHullIds) || error;
            error = attemptToCreateFleetMember(factionConfig.superFreighterVariants, false, usedHullIds) || error;
            error = attemptToCreateFleetMember(factionConfig.troopTransportVariants, false, usedHullIds) || error;

            if(error)
            {
                ExerelinUtilsMessaging.addMessage("ERROR: Exerelin mod and " + factionConfig.factionId + " are out of sync. See log for details.", Color.ORANGE);
            }

            System.out.println("");
        }

        System.out.println("Checking for unused hulls");
        List<String> hullsToCheck = Global.getSector().getAllEmptyVariantIds();

        for(String hull : hullsToCheck)
        {
            if(!usedHullIds.contains(hull))
                System.out.println(" - " + hull);
        }

        System.out.println("");
    }

    private static Boolean attemptToCreateFleetMember(List<String> variants, boolean fighters, List<String> usedHullIds)
    {
        Boolean error = false;

        for(String variant : variants)
        {
            try {
                FleetMemberAPI newMember;
                if (fighters)
                    newMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, variant);
                else {
                    newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                    usedHullIds.add(newMember.getHullId() + "_Hull");
                }
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
                error = true;
            }
        }

        return error;
    }
}
