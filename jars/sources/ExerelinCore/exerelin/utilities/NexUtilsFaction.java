package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.Nex_FactionCommissionIntel;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.fs.starfarer.api.util.Misc.isMilitary;

public class NexUtilsFaction {
	
	public static MarketAPI getSystemOwningMarket(LocationAPI loc) {
		int max = 0;
		MarketAPI result = null;
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarkets(loc);
		for (MarketAPI curr : markets) {
			if (curr.isHidden()) continue;
			
			int score = curr.getSize();
			for (MarketAPI other : markets) {
				if (other != curr && other.getFaction() == curr.getFaction()) score++;
			}
			if (isMilitary(curr)) score += 10;
			if (score > max) {				
				max = score;
				result = curr;
			}
		}
		
		return result;
	}
	
	/**
	 * Same as Misc.getClaimingFaction except doesn't exclude player faction.
	 * @param loc
	 * @return
	 */
	public static FactionAPI getSystemOwner(LocationAPI loc)
	{
		MarketAPI result = getSystemOwningMarket(loc);
		if (result == null) return null;
		
		return result.getFaction();
    }

    public static FactionAPI getClaimingFaction(SectorEntityToken entity) {
        if (entity.getContainingLocation() != null) {
            String claimedBy = entity.getContainingLocation().getMemoryWithoutUpdate().getString(MemFlags.CLAIMING_FACTION);
            if (claimedBy != null) {
                return Global.getSector().getFaction(claimedBy);
            }
            return getSystemOwner(entity.getContainingLocation());
        }
        return null;
    }
    
    public static boolean doesFactionExist(String factionId)
    {
        return Global.getSector().getFaction(factionId) != null;
    }
    
    public static List<MarketAPI> getFactionMarkets(String factionId)
    {
        return getFactionMarkets(factionId, false);
    }
    
    public static List<MarketAPI> getFactionMarkets(String factionId, boolean onlyInvadable)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        List<MarketAPI> ret = new ArrayList<>();
        for (MarketAPI market : allMarkets)
        {
            if (onlyInvadable && !NexUtilsMarket.canBeInvaded(market, false))
                continue;
            if (market.getFactionId().equals(factionId))
                ret.add(market);
        }
        return ret;
    }

    public static List<MarketAPI> getPlayerMarkets(boolean includeAutonomous, boolean includeHidden)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        List<MarketAPI> ret = new ArrayList<>();
        for (MarketAPI market : allMarkets)
        {
            if (!includeHidden && market.isHidden()) continue;
            if (!includeAutonomous && !market.isPlayerOwned()) continue;
            if (market.getFactionId().equals(Factions.PLAYER))
                ret.add(market);
        }
        return ret;
    }

    
    public static Set<LocationAPI> getLocationsWithFactionPresence(String factionId) {
        Set<LocationAPI> results = new HashSet<>();
        for (MarketAPI market : getFactionMarkets(factionId)) {
            results.add(market.getContainingLocation());
        }
        return results;
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
    
    public static int getFactionMarketSizeSum(String factionId)
    {
        return getFactionMarketSizeSum(factionId, false);
    }
    
    /**
     * Returns the sum of the sizes of the faction's markets
     * @param factionId
     * @param onlyInvadable
     * @return
     */
    public static int getFactionMarketSizeSum(String factionId, boolean onlyInvadable)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        int pop = 0;
        for (MarketAPI market : allMarkets)
        {
            if (market.getFactionId().equals(factionId))
            {
                if (onlyInvadable && !NexUtilsMarket.canBeInvaded(market, false))
                    continue;
                pop += market.getSize();
            }
        }
        return pop;
    }
    
    public static String getFactionShortName(String factionId)
    {
        return getFactionShortName(Global.getSector().getFaction(factionId));
    }
    
    public static String getFactionShortName(FactionAPI faction)
    {
		if (faction.isPlayerFaction() && !Misc.isPlayerFactionSetUp()) {
			return StringHelper.getString("player");
		}
		
        String name = faction.getEntityNamePrefix();
        if (name == null || name.isEmpty())
            name = faction.getDisplayName();
        return name;
    }
    
    public static boolean isPirateFaction(String factionId)
    {
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
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
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
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
        if (factionId.equals("exigency") && SectorManager.getManager().isCorvusMode()) 
        {
            //List<String> factions = Arrays.asList(ExerelinSetupData.getInstance().getPossibleFactions());
            //return (factions.contains("exigency") && SectorManager.getManager().isCorvusMode());
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
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
        if (config == null) return noConfigFallback;
        return config.corvusCompatible;
    }
	
	public static List<FactionAPI> factionIdsToFactions(List<String> factionIds) {
		List<FactionAPI> factions = new ArrayList<>();
		for (String factionId : factionIds) {
			factions.add(Global.getSector().getFaction(factionId));
		}
		return factions;
	}
    
    public static void grantCommission(String factionId)
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (!NexConfig.getFactionConfig(faction.getId()).playableFaction)
            return;
        if (faction.getId().equals(Misc.getCommissionFactionId()))
            return;    // already have commission
        
        revokeCommission();
        FactionCommissionIntel intel = new Nex_FactionCommissionIntel(faction);
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
	
	public static void addFactionNamePara(TooltipMakerAPI info, float pad, Color color, FactionAPI faction) {
		String name = Misc.ucFirst(faction.getDisplayName());
		info.addPara(name, pad, color, faction.getBaseUIColor(), name);
	}

	// redundant method; remains here because Roider Union uses it
	@Deprecated
	public static String getCommissionFactionId()
	{
		return Misc.getCommissionFactionId();
	}
}
