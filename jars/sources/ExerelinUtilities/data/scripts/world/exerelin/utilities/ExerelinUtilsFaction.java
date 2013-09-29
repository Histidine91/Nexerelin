package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;

import java.util.ArrayList;
import java.util.List;

public class ExerelinUtilsFaction {

    public static List<String> getFactionsAlliedWithFaction(String factionId)
    {
        List<String> allies = new ArrayList<String>();

        List<FactionAPI> factions = Global.getSector().getAllFactions();

        for(int i = 0; i < factions.size(); i++)
        {
            if(factions.get(i).getRelationship(factionId) >= 1)
                allies.add(factions.get(i).getId());
        }

        return allies;
    }

    public static List<String> getFactionsAtWarWithFaction(String factionId)
    {
        List<String> allies = new ArrayList<String>();

        List<FactionAPI> factions = Global.getSector().getAllFactions();

        for(int i = 0; i < factions.size(); i++)
        {
            if(factions.get(i).getRelationship(factionId) <= -1)
                allies.add(factions.get(i).getId());
        }

        return allies;
    }

}
