package exerelin.utilities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;

public class ExerelinUtilsMarket {
	
	public static float getCommodityDemand(MarketAPI market, String commodity)
	{
		return market.getCommodityData(commodity).getDemand().getDemand().modified;
	}
	
	public static float getCommoditySupply(MarketAPI market, String commodity)
	{
		return market.getCommodityData(commodity).getSupply().modified;
	}
	
	public static float getCommoditySupplyMult(MarketAPI market, String commodity)
	{
		return market.getCommodityData(commodity).getSupply().computeMultMod();
	}
	
	public static float getCommodityDemandFractionMet(MarketAPI market, String commodity, boolean clamp)
	{
		if (clamp) return market.getCommodityData(commodity).getDemand().getClampedAverageFractionMet();
		else return market.getCommodityData(commodity).getDemand().getAverageFractionMet();
	}
	
	public static int countMarketConditions(MarketAPI market, String marketCondition)
	{
		int count = 0;
		List<MarketConditionAPI> conditions = market.getConditions();
		for (MarketConditionAPI condition : conditions)
		{
			if (condition.getId().equals(marketCondition)) count++;
		}
		return count;
	}
	
	public static int getPopulation(MarketAPI market)
	{
		return getPopulation(market.getSize());
	}
	
	public static int getPopulation(int size)
	{
		return (int)(Math.pow(10, size));
	}
	
	public static float getHyperspaceDistance(MarketAPI market1, MarketAPI market2)
	{
		SectorEntityToken primary1 = market1.getPrimaryEntity();
		SectorEntityToken primary2 = market2.getPrimaryEntity();
		if (primary1.getContainingLocation() == primary2.getContainingLocation())
			return 0;
		
		return Misc.getDistance(primary1.getLocationInHyperspace(), primary2.getLocationInHyperspace());
	}
}
