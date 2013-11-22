package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

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

    public static List<String> getFactionsAtWarWithFaction(String factionId, Boolean excludeNeutralAndRebels)
    {
        List<String> enemies = new ArrayList<String>();

        List<FactionAPI> factions = Global.getSector().getAllFactions();

        for(int i = 0; i < factions.size(); i++)
        {
            FactionAPI potentialWarCandidate = factions.get(i);
            if(factions.get(i).getRelationship(factionId) <= -1)
            {
                if(excludeNeutralAndRebels)
                {
                    if(potentialWarCandidate.getId().equalsIgnoreCase("rebel")
                            || potentialWarCandidate.getId().equalsIgnoreCase("abandoned")
                            || ExerelinUtilsHelper.doesStringArrayContainValue(potentialWarCandidate.getId(), ExerelinConfig.neutralFactions, false)
                            || ExerelinUtilsHelper.doesStringArrayContainValue(potentialWarCandidate.getId(), ExerelinConfig.getAllCustomFactionRebels().toArray(new String[]{}), false))
                    {
                        continue;
                    }
                }
                enemies.add(factions.get(i).getId());
            }
        }

        return enemies;
    }

    public static boolean doesFactionOwnSystem(String factionId, StarSystemAPI system)
    {
        for(SectorEntityToken station : system.getOrbitalStations())
        {
            if(!station.getFaction().getId().equalsIgnoreCase(factionId)
                    && !station.getFaction().getId().equalsIgnoreCase("neutral")
                    && !station.getFaction().getId().equalsIgnoreCase("independent"))
            {
                return false;
            }
        }

        return true;
    }

    public static boolean isFactionAtWar(String factionId, Boolean excludeNeutralAndRebels)
    {
        if(ExerelinUtilsFaction.getFactionsAtWarWithFaction(factionId, excludeNeutralAndRebels).size() > 0)
            return true;
        else
            return false;
    }
}
