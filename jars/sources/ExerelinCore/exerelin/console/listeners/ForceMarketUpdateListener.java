package exerelin.console.listeners;

import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_BlueprintSwap;
import java.util.Locale;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;
import org.lazywizard.console.CommandListener;

public class ForceMarketUpdateListener implements CommandListener {

	@Override
	public boolean onPreExecute(String command, String args, CommandContext context, boolean alreadyIntercepted) {
		return false;	// do nothing
	}

	@Override
	public CommandResult execute(String command, String args, CommandContext context) {
		return CommandResult.SUCCESS;	// do nothing
	}

	@Override
	public void onPostExecute(String command, String args, CommandResult result, CommandContext context, CommandListener interceptedBy) {
		if (!command.toLowerCase(Locale.ROOT).equals("forcemarketupdate"))
			return;
		if (result != CommandResult.SUCCESS)
			return;
		Nex_BlueprintSwap.unsetBlueprintStocks();
	}
	
}
