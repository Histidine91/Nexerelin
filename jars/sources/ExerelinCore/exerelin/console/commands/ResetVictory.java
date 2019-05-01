package exerelin.console.commands;

import exerelin.campaign.SectorManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class ResetVictory implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        boolean didAnything = SectorManager.getManager().clearVictory();
		if (didAnything)
			Console.showMessage("Victory reset.");
		else
			Console.showMessage("Game was not in victory state, no action taken.");
        
        return CommandResult.SUCCESS;
    }
}
