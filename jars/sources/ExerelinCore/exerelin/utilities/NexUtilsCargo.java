package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;

import java.awt.*;


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

    public static void makePaymentWithDebtIfNeeded(int payment, InteractionDialogAPI dialog) {
        int cashPayment = payment;
        MutableValue creds = Global.getSector().getPlayerFleet().getCargo().getCredits();
        int debt = 0;

        if (creds.get() < cashPayment) {
            debt = -(int)(creds.get() - cashPayment);
            cashPayment = (int)creds.get();
            MonthlyReport report = SharedData.getData().getPreviousReport();
            report.setDebt(report.getDebt() + debt);
        }
        creds.subtract(cashPayment);
        if (dialog != null) AddRemoveCommodity.addCreditsLossText(cashPayment, dialog.getTextPanel());
        if (debt > 0) {
            String debtStr = Misc.getDGSCredits(debt);
            String str = StringHelper.getString("exerelin_misc", "dialog_debtIncurred", true);
            if (dialog != null) {
                dialog.getTextPanel().setFontSmallInsignia();
                dialog.getTextPanel().addPara(str, Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), debtStr);
                dialog.getTextPanel().setFontInsignia();
            }
            else {
                Global.getSector().getCampaignUI().addMessage(str, Misc.getNegativeHighlightColor(), debtStr, "", Misc.getHighlightColor(), Color.white);
            }
        }
    }
}
