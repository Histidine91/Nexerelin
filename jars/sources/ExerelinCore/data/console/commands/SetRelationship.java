package data.console.commands;

import exerelin.utilities.ExerelinUtilsReputation;

public class SetRelationship extends org.lazywizard.console.commands.SetRelationship {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        CommandResult result = super.runCommand(args, context);
		if (result == CommandResult.SUCCESS)
		{
			ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
		}
		return result;
    }
}
