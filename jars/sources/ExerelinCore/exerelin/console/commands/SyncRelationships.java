package exerelin.console.commands;

import exerelin.utilities.NexUtilsReputation;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class SyncRelationships implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        NexUtilsReputation.syncFactionRelationshipsToPlayer();
        Console.showMessage("Faction relationships synced.");
        
        return CommandResult.SUCCESS;
    }
}
