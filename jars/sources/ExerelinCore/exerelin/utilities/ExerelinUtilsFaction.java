package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;

import java.util.ArrayList;
import java.util.List;

public class ExerelinUtilsFaction {

    

    public static boolean doesFactionOwnSystem(String factionId, StarSystemAPI system)
    {
        for(MarketAPI market : Misc.getMarketsInLocation(system))
        {
            if(!market.getFaction().getId().equalsIgnoreCase(factionId)
                    && !market.getFaction().getId().equalsIgnoreCase("neutral")
                    && !market.getFaction().getId().equalsIgnoreCase("independent"))
            {
                return false;
            }
        }

        return true;
    }
    
    public static List<MarketAPI> getFactionMarkets(String factionId)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        List<MarketAPI> ret = new ArrayList<>();
        for (MarketAPI market : allMarkets)
        {
            if (market.getFactionId().equals(factionId))
                ret.add(market);
        }
        return ret;
    }
    
    public static boolean isPirateFaction(String factionId)
    {
        List<String> pirates = DiplomacyManager.getPirateFactionsCopy();
        return pirates.contains(factionId);
    }
    
    public static boolean isPirateOrTemplarFaction(String factionId)
    {
        if (factionId.equals("templars")) return true;
        return isPirateFaction(factionId);
    }

    public static boolean isExiInCorvus(String factionId)
    {
        return factionId.equals("exigency") && SectorManager.getCorvusMode();
    }
    
}
