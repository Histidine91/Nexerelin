package exerelin.utilities;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
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
}
