package data.console.commands;

import exerelin.utilities.ExerelinUtilsReputation;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class SyncFactionRelationships implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
        Console.showMessage("Faction relationships synced.");
        
        return CommandResult.SUCCESS;
    }
}
