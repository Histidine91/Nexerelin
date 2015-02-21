package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinUtils;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.world.InvasionFleetManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

public class FlipClosestMarket implements BaseCommand {

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
        List<String> factions = SectorManager.getLiveFactionIdsCopy();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String factionId : factions)
        {
            if (!factionId.equals(defenderFactionId))
                picker.add(factionId);
        }
        String attackerFactionId = picker.pick();
        FactionAPI attackerFaction = Global.getSector().getFaction(attackerFactionId);
        
        List<SectorEntityToken> linkedEntities = market.getConnectedEntities();
        for (SectorEntityToken entity : linkedEntities)
        {
                entity.setFaction(attackerFactionId);
        }
        market.setFactionId(attackerFactionId);
        List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
        for (SubmarketAPI submarket : submarkets)
        {
                if(submarket.getFaction() == defenderFaction)
                submarket.setFaction(attackerFaction);
        }
        market.reapplyConditions();
        
        Map<String, Object> params = new HashMap<>();
        params.put("newOwner", attackerFaction);
        params.put("oldOwner", defenderFaction);
        params.put("playerInvolved", false);
        params.put("factionsToNotify", new ArrayList<>());
        params.put("repChangeStrength", 0f);
        sector.getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_market_captured", params);
        SectorManager.notifyMarketCaptured(market, attackerFaction, defenderFaction);
        Console.showMessage("Transferred market " + market.getName() + " from " + defenderFaction.getDisplayName() + " to " + attackerFaction.getDisplayName());
        
        return CommandResult.SUCCESS;
    }
}
