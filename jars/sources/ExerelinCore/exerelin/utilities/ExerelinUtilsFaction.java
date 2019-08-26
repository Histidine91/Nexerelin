package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static com.fs.starfarer.api.util.Misc.isMilitary;
import exerelin.campaign.SectorManager;
import java.awt.Color;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class ExerelinUtilsFaction {
	
	// same as Misc.getClaimingFaction except doesn't exclude player faction
	public static FactionAPI getSystemOwner(LocationAPI loc)
	{
		int max = 0;
		MarketAPI result = null;
		for (MarketAPI curr : Global.getSector().getEconomy().getMarkets(loc)) {
			if (curr.isHidden()) continue;
			
			int score = curr.getSize();
			if (isMilitary(curr)) score += 10;
			if (score > max) {
				JSONObject json = curr.getFaction().getCustom().optJSONObject(Factions.CUSTOM_PUNITIVE_EXPEDITION_DATA);
				if (json == null) continue;
				boolean territorial = json.optBoolean("territorial");
				if (!territorial) continue;
				
				max = score;
				result = curr;
			}
		}
		if (result == null) return null;
		
		return result.getFaction();
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
            if (onlyInvadable && !ExerelinUtilsMarket.canBeInvaded(market, false))
                continue;
            if (market.getFactionId().equals(factionId))
                ret.add(market);
        }
        return ret;
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
                if (onlyInvadable && !ExerelinUtilsMarket.canBeInvaded(market, false))
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
	
	public static void addFactionNamePara(TooltipMakerAPI info, float pad, Color color, FactionAPI faction) {
		String name = Misc.ucFirst(faction.getDisplayName());
		info.addPara(name, pad, color, faction.getBaseUIColor(), name);
	}
}
