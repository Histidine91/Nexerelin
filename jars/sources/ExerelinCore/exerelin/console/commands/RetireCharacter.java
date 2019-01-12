package exerelin.console.commands;

import exerelin.campaign.SectorManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class RetireCharacter implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        SectorManager.retire();
        Console.showMessage("You have retired your character. Unpause the game to continue.");
        
        return CommandResult.SUCCESS;
    }
}
