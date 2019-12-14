package exerelin.console.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.intel.fleets.VengeanceFleetIntel;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class VengeanceEvent implements BaseCommand {

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

		if (tmp.length < 1)
		{
			return CommandResult.BAD_SYNTAX;
		}

		final FactionAPI faction = CommandUtils.findBestFactionMatch(tmp[0]);
		if (faction == null)
		{
			Console.showMessage("No such faction '" + tmp[0] + "'!");
			return CommandResult.ERROR;
		}
		
		RevengeanceManager veng = RevengeanceManager.getManager();
		int level;
		if (tmp.length >= 2) level = Integer.parseInt(tmp[1]);
		else level = veng.getCurrentVengeanceStage(faction.getId());
			
		MarketAPI market = veng.pickMarketForFactionVengeance(faction.getId());
		if (market == null)
		{
			Console.showMessage("Unable to find market for vengeance fleet");
			return CommandResult.ERROR;
		}
		
		VengeanceFleetIntel vengeance = new VengeanceFleetIntel(faction.getId(), market, level);
		vengeance.startEvent();
		Console.showMessage("Spawning vengeance fleet level " + level + " for faction " + faction.getDisplayName() + " from " + market.getName());	
		return CommandResult.SUCCESS;
	}
}
