package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import exerelin.campaign.intel.agents.AgentIntel;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class AddAgent implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
		String[] tmp = args.split(" ");
		int level = 1;
		if (tmp.length > 0) {
			try {
				level = Integer.parseInt(tmp[0]);
			} catch (NumberFormatException nfex) {
				// not a number, do nothing
			}
		}
		MarketAPI market;
		if (tmp.length > 1) {
			market = CommandUtils.findBestMarketMatch(tmp[1]);
		} else
			market = Global.getSector().getEconomy().getMarketsCopy().get(0);
		
        PersonAPI agent = Global.getSector().getPlayerFaction().createRandomPerson();
		agent.setRankId(Ranks.AGENT);
		agent.setPostId(Ranks.POST_AGENT);
		AgentIntel intel = new AgentIntel(agent, Global.getSector().getPlayerFaction(), level);
		intel.setMarket(market);
		intel.init();
        Console.showMessage("Added level " + level + " agent " + agent.getNameString() + " to market " + market.getName());
        
        return CommandResult.SUCCESS;
    }
}
