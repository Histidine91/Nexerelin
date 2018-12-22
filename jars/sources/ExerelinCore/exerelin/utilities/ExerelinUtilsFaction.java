package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;

import java.util.ArrayList;
import java.util.List;

public class ExerelinUtilsFaction {
    
    @Deprecated
    public static boolean doesFactionOwnSystem(String factionId, StarSystemAPI system)
    {
        for(MarketAPI market : Misc.getMarketsInLocation(system))
        {
            if(!market.getFaction().getId().equalsIgnoreCase(factionId)
                    && !market.getFaction().getId().equalsIgnoreCase(Factions.NEUTRAL)
                    && !market.getFaction().getId().equalsIgnoreCase(Factions.INDEPENDENT))
            {
                return false;
            }
        }

        return true;
    }
    
    public static boolean doesFactionExist(String factionId)
    {
        return Global.getSector().getFaction(factionId) != null;
    }
    
    public static List<MarketAPI> getFactionMarkets(String factionId)
    {
        return getFactionMarkets(factionId, false);
    }
    
    public static boolean hasAnyMarkets(String factionId)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        for (MarketAPI market : allMarkets)
        {
            if (market.getFactionId().equals(factionId))
                return true;
        }
        return false;
    }
    
    public static List<MarketAPI> getFactionMarkets(String factionId, boolean onlyInvadable)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        List<MarketAPI> ret = new ArrayList<>();
        for (MarketAPI market : allMarkets)
        {
            if (onlyInvadable && market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_UNINVADABLE))
                continue;
            if (onlyInvadable && market.getPrimaryEntity().getTags().contains(ExerelinConstants.TAG_UNINVADABLE))
                continue;
            if (market.getFactionId().equals(factionId))
                ret.add(market);
        }
        return ret;
    }
    
    /**
     * Returns the sum of the sizes of the faction's markets
     * @param factionId
     * @return
     */
    public static int getFactionMarketSizeSum(String factionId)
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
    
    public static String getFactionShortName(String factionId)
    {
        return getFactionShortName(Global.getSector().getFaction(factionId));
    }
    
    public static String getFactionShortName(FactionAPI faction)
    {
        String name = faction.getEntityNamePrefix();
        if (name == null || name.isEmpty())
            name = faction.getDisplayName();
        return name;
    }
    
    public static boolean isPirateFaction(String factionId)
    {
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (config == null) return false;
        return config.pirateFaction;
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
    
    public static boolean isLuddicFaction(String factionId)
    {
        return (factionId.equals(Factions.LUDDIC_CHURCH) 
                || factionId.equals(Factions.LUDDIC_PATH)
                || factionId.equals(Factions.KOL));
    }
    
    /**
     * Is this faction ExigencyCorp and are we in Corvus mode?
     * @param factionId
     * @return
     */
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
    
    /**
     * Is ExigencyCorp present in the Sector and are we in Corvus mode?
     * @return
     */
    public static boolean isExiInCorvus()
    {
        return isExiInCorvus("exigency");
    }
    
    public static boolean isCorvusCompatible(String factionId, boolean noConfigFallback)
    {
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (config == null) return noConfigFallback;
        return config.corvusCompatible;
    }
    
    public static void grantCommission(String factionId, SectorEntityToken entity)
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (!ExerelinConfig.getExerelinFactionConfig(faction.getId()).playableFaction)
            return;
        if (faction.getId().equals(getCommissionFactionId()))
            return;    // already have commission
        
        revokeCommission();
        FactionCommissionIntel intel = new FactionCommissionIntel(faction);
        intel.missionAccepted();
        intel.makeRepChanges(null);
    }
    
    public static void revokeCommission()
    {
        FactionCommissionIntel intel = Misc.getCommissionIntel();
		if (intel == null) return;
		BaseMissionIntel.MissionResult result = intel.createResignedCommissionResult(true, true, null);
		intel.setMissionResult(result);
		intel.setMissionState(BaseMissionIntel.MissionState.ABANDONED);
		intel.endMission(null);
    }
    
    public static String getCommissionFactionId()
    {
        FactionAPI commissionFaction = Misc.getCommissionFaction();
        if (commissionFaction == null) return null;
        return commissionFaction.getId();
    }
}
