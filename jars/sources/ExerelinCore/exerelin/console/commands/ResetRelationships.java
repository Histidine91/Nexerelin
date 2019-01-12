package exerelin.console.commands;

import exerelin.campaign.DiplomacyManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class ResetRelationships implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        DiplomacyManager.initFactionRelationships(true);
        Console.showMessage("Faction relationships reset.");
        
        return CommandResult.SUCCESS;
    }
}
