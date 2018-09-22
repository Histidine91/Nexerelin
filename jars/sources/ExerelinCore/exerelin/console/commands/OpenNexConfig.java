package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import exerelin.campaign.ReinitScreenScript;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class OpenNexConfig implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (context != CommandContext.CAMPAIGN_MAP) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		
		Global.getSector().addTransientScript(new ReinitScreenScript());
		
		Console.showMessage("The configuration screen will appear when you close the console.");
		
		return CommandResult.SUCCESS;
	}
}
