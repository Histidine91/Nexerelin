package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Events;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.missions.FactionCommissionMission;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.events.ExerelinFactionCommissionMissionEvent;

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
    
    public static int getFactionPopulation(String factionId)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        int pop = 0;
        for (MarketAPI market : allMarkets)
        {
            if (market.getFactionId().equals(factionId))
                pop += market.getSize();
        }
        return pop;
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
    
    public static boolean isFactionHostileToAll(String factionId)
    {
        if (isPirateFaction(factionId)) return true;
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (config == null) return false;
        return config.hostileToAll > 0;
    }
    
    public static boolean isExiInCorvus(String factionId)
    {
        if (factionId.equals("exigency") && SectorManager.getCorvusMode()) 
        {
            //List<String> factions = Arrays.asList(ExerelinSetupData.getInstance().getPossibleFactions());
            //return (factions.contains("exigency") && SectorManager.getCorvusMode());
            SectorEntityToken tasserus = Global.getSector().getEntityById("exigency_tasserus");
            return tasserus != null;
        }
        return false;
    }
    
    public static boolean isCorvusCompatible(String factionId, boolean noConfigFallback)
    {
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (config == null) return noConfigFallback;
        return config.corvusCompatible;
    }
    
    public static void grantCommission(SectorEntityToken entity)
    {
        FactionAPI faction = entity.getFaction();
        if (!faction.getCustomBoolean(Factions.CUSTOM_OFFERS_COMMISSIONS))
            return;
        if (faction.getId().equals(Misc.getCommissionFaction()))
            return;    // already have commission
        
        revokeCommission();
        FactionCommissionMission mission = new FactionCommissionMission(faction.getId());
        mission.playerAccept(entity);
    }
    
    public static void revokeCommission()
    {
        SectorAPI sector = Global.getSector();
        // find event
        CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, Events.FACTION_COMMISSION);
        if (eventSuper == null) return;
        
        ExerelinFactionCommissionMissionEvent event = (ExerelinFactionCommissionMissionEvent)eventSuper;
        event.endEvent();
        //sector.reportEventStage(event, "annul", event.findMessageSender(), MessagePriority.ENSURE_DELIVERY);    // TODO comment out after debugging
    }
}
