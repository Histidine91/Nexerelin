package exerelin.utilities;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;


public class ExerelinUtilsCargo
{
    public static void addCommodityStockpile(MarketAPI market, String commodityID, float amountToAdd)
    {
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
        
        commodity.addToStockpile(amountToAdd);
        
        if (market.getFactionId().equals("templars"))
        {
            CargoAPI cargoTemplars = market.getSubmarket("tem_templarmarket").getCargo();
            cargoTemplars.addCommodity(commodityID, amountToAdd * 0.2f);
            return;
        }
        if (market.getSubmarket(Submarkets.SUBMARKET_OPEN) == null)    // some weirdo mod
        {
            return;
        }
        
        CargoAPI cargoOpen = market.getSubmarket(Submarkets.SUBMARKET_OPEN).getCargo();
        CargoAPI cargoBlack = cargoOpen;
        if (market.hasSubmarket(Submarkets.SUBMARKET_BLACK))
            cargoBlack = market.getSubmarket(Submarkets.SUBMARKET_BLACK).getCargo();
        CargoAPI cargoMilitary = null;
        if (market.hasSubmarket(Submarkets.GENERIC_MILITARY))
            cargoMilitary = market.getSubmarket(Submarkets.GENERIC_MILITARY).getCargo();
        
        if(!market.isIllegal(commodity))
            cargoOpen.addCommodity(commodityID, amountToAdd * 0.15f);
        else if (commodityID.equals("hand_weapons") && cargoMilitary != null)
        {
            cargoMilitary.addCommodity(commodityID, amountToAdd * 0.1f);
            cargoBlack.addCommodity(commodityID, amountToAdd * 0.05f);
        }
        else
            cargoBlack.addCommodity(commodityID, amountToAdd * 0.1f);
        //log.info("Adding " + amount + " " + commodityID + " to " + market.getName());
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
