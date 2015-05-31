package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.world.InvasionFleetManager;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

public class SpawnRespawnFleet implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        // do me!
        SectorAPI sector = Global.getSector();
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        List<MarketAPI> markets = Misc.getMarketsInLocation(playerFleet.getContainingLocation());
        
        Vector2f playerPos = playerFleet.getLocation();
        MarketAPI closestTargetMarket = null;
        float closestTargetDist = 9999999;
        
        for (MarketAPI market : markets) {
            float distance = Misc.getDistance(playerPos, market.getPrimaryEntity().getLocation());
            if (distance < closestTargetDist)
            {
                closestTargetDist = distance;
                closestTargetMarket = market;
            }
        }
        
        if (closestTargetMarket == null)
        {
            Console.showMessage("Unable to find target");
                return CommandResult.ERROR;
        }
        
        InvasionFleetManager.InvasionFleetData data = InvasionFleetManager.spawnRespawnFleet(playerAlignedFaction, closestTargetMarket, closestTargetMarket);
        Console.showMessage("Spawning " + data.fleet.getName() + ", sending to " + closestTargetMarket.getName());
        data.fleet.setLocation(playerPos.x, playerPos.y);
        return CommandResult.SUCCESS;
    }
}
