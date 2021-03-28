package exerelin.utilities;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;


public class NexUtilsCargo
{
    public static void addCommodityStockpile(MarketAPI market, String commodityID, float amountToAdd)
    {
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
        commodity.addTradeModPlus("commodity_add_" + Misc.genUID(), amountToAdd, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);
    }
    
    public static void addCommodityStockpile(MarketAPI market, String commodityID, float minMult, float maxMult)
    {
        float multDiff = maxMult - minMult;
        float mult = minMult + (float)(Math.random()) * multDiff;
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
        float demand = commodity.getDemand().getDemandValue();
        float amountToAdd = demand*mult;
        addCommodityStockpile(market, commodityID, amountToAdd);
    }
}
