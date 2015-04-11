package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

public class SetClosestMarketOwner implements BaseCommand {

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
        FactionAPI defenderFaction = market.getFaction();
        String defenderFactionId = defenderFaction.getId();
        String attackerFactionId = "player_npc";
        
        //Console.showMessage("Arguments: " + args);
        
        if (args.isEmpty())
        {
            List<String> factions = SectorManager.getLiveFactionIdsCopy();
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            for (String factionId : factions)
            {
                if (!factionId.equals(defenderFactionId))
                    picker.add(factionId);
            }
            attackerFactionId = picker.pick();
        }
        else
        {
            String[] tmp = args.split(" ");

            if (tmp.length > 1)
            {
                return CommandResult.BAD_SYNTAX;
            }

            String factionStr = tmp[0];
            FactionAPI fac = CommandUtils.findBestFactionMatch(factionStr);

            if (fac == null)
            {
                Console.showMessage("Error: no such faction '" + factionStr + "'!");
                return CommandResult.ERROR;
            }
            attackerFactionId = fac.getId();
        }
        
        FactionAPI attackerFaction = Global.getSector().getFaction(attackerFactionId);
        
        SectorManager.captureMarket(market, attackerFaction, defenderFaction, false, null, 0);
        Console.showMessage("Transferred market " + market.getName() + " from " + defenderFaction.getDisplayName() + " to " + attackerFaction.getDisplayName());
        
        return CommandResult.SUCCESS;
    }
}
