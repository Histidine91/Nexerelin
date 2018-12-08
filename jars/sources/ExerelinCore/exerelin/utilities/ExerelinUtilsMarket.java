package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

// TODO: Clean up this crap
public class ExerelinUtilsMarket {
	
	// use the memory key instead of this wherever possible
	public static final List<String> NO_INVADE_MARKETS = Arrays.asList(new String[]{"SCY_prismFreeport", "prismFreeport", "prismFreeport_market"});
	
	public static float getPopulation(MarketAPI market)
	{
		return getPopulation(market.getSize());
	}
	
	public static float getPopulation(int size)
	{
		//return (int)(Math.pow(10, size));
		if (size <= 1) return 0.125f;
		if (size == 2) return 0.25f;
		if (size == 3) return 0.5f;
		
		return (float) Math.pow(2, size - 4);
	}
		
	public static float getHyperspaceDistance(MarketAPI market1, MarketAPI market2)
	{
		SectorEntityToken primary1 = market1.getPrimaryEntity();
		SectorEntityToken primary2 = market2.getPrimaryEntity();
		if (primary1.getContainingLocation() == primary2.getContainingLocation())
			return 0;
		
		return Misc.getDistance(primary1.getLocationInHyperspace(), primary2.getLocationInHyperspace());
	}
		
	public static void setTariffs(MarketAPI market)
	{
		String factionId = market.getFactionId();
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		market.getTariff().modifyMult("nexerelinMult", ExerelinConfig.baseTariffMult);
		market.getTariff().modifyMult("nexerelinFactionMult", conf.tariffMult);
		if (market.hasCondition(Conditions.FREE_PORT))
		{
			market.getTariff().modifyMult("nexerelin_freeMarket", ExerelinConfig.freeMarketTariffMult);
		}
		else
		{
			market.getTariff().unmodify("nexerelin_freeMarket");
		}
	}
	
	public static boolean isMarketBeingInvaded(MarketAPI market)
	{
		return market.getMemoryWithoutUpdate().getBoolean("$beingInvaded")	// NPC fleet
				|| market.getId().equals(Global.getSector().getCharacterData().getMemoryWithoutUpdate().getString("$invasionTarget"));	// player
	}
	
	/**
	 * Did this market originally belong to the specified faction?
	 * @param market
	 * @param factionId
	 * @return
	 */
	public static boolean wasOriginalOwner(MarketAPI market, String factionId)
	{
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains("$startingFactionId"))
			return mem.getString("$startingFactionId").equals(factionId);
		
		return false;
	}
	
	/**
	 * Is this market still owned by its original owner?
	 * @param market
	 * @return
	 */
	public static boolean isWithOriginalOwner(MarketAPI market)
	{
		return wasOriginalOwner(market, market.getFactionId());
	}
	
	/**
	 * Can factions launch invasion fleets at <code>market</code>?
	 * Player may still be able to invade even if this returns false.
	 * @param market
	 * @param minSize Minimum market size to consider for invasions
	 * @return
	 */
	public static boolean shouldTargetForInvasions(MarketAPI market, int minSize)
	{
		if (market.getSize() < minSize) return false;
		FactionAPI marketFaction = market.getFaction();
		String factionId = marketFaction.getId();
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		boolean isIndie = factionId.equals(Factions.INDEPENDENT);
		
		if (config != null && !config.playableFaction && !isIndie)
			return false;
		
		boolean allowPirates = ExerelinConfig.allowPirateInvasions;
		if (!allowPirates && (ExerelinUtilsFaction.isPirateFaction(factionId) || isIndie))
		{
			// this is the only circumstance when pirate markets can be invaded while allowPirateInvasions is off
			if (!ExerelinConfig.retakePirateMarkets)
				return false;
			if (isWithOriginalOwner(market))	// was a pirate market all along, can't invade
				return false;
		}
		
		return canBeInvaded(market, false);
	}
	
	/**
	 * Can this market be invaded, by player or by NPCs?
	 * @param market
	 * @param isPlayer Is the would-be invader the player?
	 * @return
	 */
	public static boolean canBeInvaded(MarketAPI market, boolean isPlayer)
	{
		if (market.hasCondition(Conditions.ABANDONED_STATION)) return false;		
		if (market.getPrimaryEntity() instanceof CampaignFleetAPI) return false;
		
		FactionAPI marketFaction = market.getFaction();
		if (isPlayer)
		{
			if (marketFaction == PlayerFactionStore.getPlayerFaction() || marketFaction.isPlayerFaction())
				return false;
		}
		if (marketFaction.isNeutralFaction()) return false;
		if (!market.isInEconomy()) return false;
		
		if (market.getPrimaryEntity().hasTag(ExerelinConstants.TAG_UNINVADABLE))
			return false;
		if (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_UNINVADABLE))
			return false;
		
		return true;
	}
	
	public static boolean canTradeWithMarket(MarketAPI market)
	{
		FactionAPI faction = market.getFaction();
		if (faction.isAtWorst(Factions.PLAYER, RepLevel.SUSPICIOUS))
			return true;
		if (market.hasCondition(Conditions.FREE_PORT))
			return true;
		if (faction.getCustomBoolean(Factions.CUSTOM_ALLOWS_TRANSPONDER_OFF_TRADE))
			return true;
		
		return false;
	}
}
