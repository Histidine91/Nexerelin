package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

public class ResetClosestMarket implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        // do me!
        SectorAPI sector = Global.getSector();
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        List<MarketAPI> markets = Misc.getMarketsInLocation(playerFleet.getContainingLocation());
        
        Vector2f playerPos = playerFleet.getLocation();
        MarketAPI market = null;
        float closestDist = 9999999;
        
        for (MarketAPI tryMarket : markets) {
            float distance = Misc.getDistance(playerPos, tryMarket.getPrimaryEntity().getLocation());
            if (distance < closestDist)
            {
                closestDist = distance;
                market = tryMarket;
            }
        }
        
        if (market == null)
        {
            Console.showMessage("Unable to find target");
                return CommandResult.ERROR;
        }
        
        String ownerId = market.getFactionId();
        /*
        market.updatePriceMult();
        market.resetSmugglingValue();
        
        List<CommodityOnMarketAPI> allCommodities = market.getAllCommodities();
        for (CommodityOnMarketAPI commodity : allCommodities)
        {
            commodity.setAverageStockpile(0);
            commodity.setAverageStockpileAfterDemand(0);
            commodity.setStockpile(0);
        }
        */
        List<String> conds = new ArrayList<>();
        for (MarketConditionAPI cond : market.getConditions())
        {
            conds.add(cond.getId());
        }
        for (String cond : conds)
        {
            market.removeCondition(cond);
        }
        market.reapplyConditions();
        
        
        for (SubmarketAPI submarket : market.getSubmarketsCopy())
        {
            submarket.getCargo().clear();
            submarket.getPlugin().updateCargoPrePlayerInteraction();
        }
        /*
        if (ownerId.equals("templars"))
        {
            market.removeSubmarket("tem_templarmarket");
            market.addSubmarket("tem_templarmarket");
        }
        else
        {
            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
            market.removeSubmarket(Submarkets.SUBMARKET_BLACK);
            market.removeSubmarket(Submarkets.GENERIC_MILITARY);
            
            //market.addSubmarket(Submarkets.SUBMARKET_OPEN);
            //market.addSubmarket(Submarkets.SUBMARKET_BLACK);
            
            //market.addSubmarket(Submarkets.GENERIC_MILITARY);
            SubmarketPlugin plugin = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
            StoragePlugin storage = (StoragePlugin)plugin;
            storage.setPlayerPaidToUnlock(true);
        }
        */
        
        Console.showMessage("Resetting market " + market.getName());
        
        return CommandResult.SUCCESS;
    }
}
