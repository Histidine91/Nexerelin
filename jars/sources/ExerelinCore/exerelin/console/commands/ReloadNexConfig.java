package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import exerelin.campaign.ui.ReinitScreenScript;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class ReloadNexConfig implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (!context.isInCampaign()) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		
		NexConfig.loadSettings();
		for (NexFactionConfig conf : NexConfig.getAllFactionConfigsCopy()) {
			conf.updateBaseAlignmentsInMemory();
		}
		Global.getSector().addTransientScript(new ReinitScreenScript());
				
		Console.showMessage(StringHelper.getString("nex_console", "msg_configReload"));
		
		return CommandResult.SUCCESS;
	}
}
