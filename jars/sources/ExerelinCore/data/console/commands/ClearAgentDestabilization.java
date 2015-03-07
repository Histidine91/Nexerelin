package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import exerelin.campaign.events.AgentDestabilizeMarketEventForCondition;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class ClearAgentDestabilization implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        SectorAPI sector = Global.getSector();
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        
        for (MarketAPI market: markets)
        {
            CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market_for_condition");
            if (eventSuper == null) continue;
            AgentDestabilizeMarketEventForCondition event = (AgentDestabilizeMarketEventForCondition)eventSuper;
            event.setStabilityPenalty(0);
            market.reapplyConditions();
        }
        Console.showMessage("Cleared agent destabilization events");
        
        return CommandResult.SUCCESS;
    }
}
