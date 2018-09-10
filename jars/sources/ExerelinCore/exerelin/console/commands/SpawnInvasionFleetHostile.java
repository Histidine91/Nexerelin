package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.campaign.fleets.InvasionFleetManager;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

public class SpawnInvasionFleetHostile implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        SectorAPI sector = Global.getSector();
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        List<MarketAPI> targets = ExerelinUtilsFaction.getFactionMarkets(playerAlignedFactionId);
        List<MarketAPI> sources = Global.getSector().getEconomy().getMarketsCopy();
        
        Vector2f playerPos = playerFleet.getLocationInHyperspace();
        MarketAPI closestTargetMarket = null;
        float closestTargetDist = 9999999;
        MarketAPI closestOriginMarket = null;
        float closestOriginDist = 9999999;
        
        // pick target market
        for (MarketAPI market : targets) {
            float distance = Misc.getDistance(playerPos, market.getPrimaryEntity().getLocationInHyperspace());            
            if (distance < closestTargetDist)
            {
                closestTargetDist = distance;
                closestTargetMarket = market;
            }
        }
        
        // pick source market
        for (MarketAPI market : sources) {
            if (!market.getFaction().isHostileTo(playerAlignedFactionId)) continue;
            float distance = Misc.getDistance(playerPos, market.getPrimaryEntity().getLocationInHyperspace());
            if (distance < closestOriginDist)
            {
                closestOriginDist = distance;
                closestOriginMarket = market;
            }
        }
        
        if (closestTargetMarket == null || closestOriginMarket == null)
        {
            Console.showMessage("Unable to find origin and/or target");
            return CommandResult.ERROR;
        }
        
        // spawn fleet
        InvasionFleetManager.InvasionFleetData data = InvasionFleetManager.spawnInvasionFleet(closestOriginMarket.getFaction(), closestOriginMarket, closestTargetMarket, 1.1f, true);
        if (data == null) {
            Console.showMessage("Unable to spawn fleet");
            return CommandResult.ERROR;
        }
        if (closestOriginMarket.getContainingLocation() != playerFleet.getContainingLocation())
        {
            closestOriginMarket.getContainingLocation().removeEntity(data.fleet);
            playerFleet.getContainingLocation().addEntity(data.fleet);
        }
        Console.showMessage("Spawning " + data.fleet.getName() + " from " + closestOriginMarket.getName());
        Console.showMessage("Oscar Mike to " + closestTargetMarket.getName());
        data.fleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        return CommandResult.SUCCESS;
    }
}
