package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import exerelin.campaign.intel.agents.AgentIntel;
import java.util.Locale;
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
		if (tmp.length == 0 || tmp[0].isEmpty())
			return CommandResult.BAD_SYNTAX;
		
		MarketAPI market = CommandUtils.findBestMarketMatch(tmp[0]);
		if (market == null) {
			Console.showMessage("Market \"" + tmp[0] + "\" not found!");
			return CommandResult.ERROR;
		}
		
		int level = 1;
		if (tmp.length > 1) {
			try {
				level = Integer.parseInt(tmp[1]);
				if (level <= 0 || level > AgentIntel.MAX_LEVEL) {
					Console.showMessage("Agent level must be between 1 and " + AgentIntel.MAX_LEVEL + "!");
					return CommandResult.ERROR;
				}
			} catch (NumberFormatException nfex) {
				return CommandResult.BAD_SYNTAX;
			}
		}
		
        PersonAPI agent = Global.getSector().getPlayerFaction().createRandomPerson();
		agent.setRankId(Ranks.AGENT);
		agent.setPostId(Ranks.POST_AGENT);
		AgentIntel intel = new AgentIntel(agent, Global.getSector().getPlayerFaction(), level);
		
		if (tmp.length > 2) {
			AgentIntel.Specialization spec = AgentIntel.Specialization.valueOf(tmp[2].toUpperCase(Locale.ENGLISH));
			intel.addSpecialization(spec);
		}
		
		intel.setMarket(market);
		intel.init();
        Console.showMessage("Added level " + level + " agent " + agent.getNameString() + " to market " + market.getName());
        
        return CommandResult.SUCCESS;
    }
}
