package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.campaign.intel.agents.AgentIntel;
import org.lazywizard.console.BaseCommand;
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
		
        PersonAPI agent = Global.getSector().getPlayerFaction().createRandomPerson();
		AgentIntel intel = new AgentIntel(agent, Global.getSector().getPlayerFaction(), level);
		intel.setMarket(Global.getSector().getEconomy().getMarketsCopy().get(0));
		intel.init();
        Console.showMessage("Added level " + level + " agent " + agent.getNameString());
        
        return CommandResult.SUCCESS;
    }
}
