package exerelin.console.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class LeaveAlliance implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        if (args.isEmpty())
        {
            return CommandResult.BAD_SYNTAX;
        }

        String[] tmp = args.split(" ");

        if (tmp.length != 1)
        {
            return CommandResult.BAD_SYNTAX;
        }

        String faction = tmp[0];

        FactionAPI fac = CommandUtils.findBestFactionMatch(faction);

        if (fac == null)
        {
            Console.showMessage("Error: no such faction '" + faction + "'!");
            return CommandResult.ERROR;
        }

        Alliance alliance = AllianceManager.getFactionAlliance(faction);
		if (alliance == null) {
			Console.showMessage(CommandUtils.getFactionName(fac) + " is not in an alliance");
            return CommandResult.ERROR;
		}
		AllianceManager.getManager().leaveAlliance(faction, alliance, false, true);
		
        Console.showMessage(CommandUtils.getFactionName(fac) + " has left their alliance.");
        return CommandResult.SUCCESS;
    }
}
