package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import exerelin.campaign.RevengeanceManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class VengeanceEvent implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (context != CommandContext.CAMPAIGN_MAP) {
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
		if (veng == null)
		{
			Console.showMessage("Vengeance event not running");
			return CommandResult.ERROR;
		}
		
		MarketAPI market = veng.pickMarketForFactionVengeance(faction.getId());
		if (market == null)
		{
			Console.showMessage("Unable to find market for vengeance fleet");
			return CommandResult.ERROR;
		}
		
		Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_faction_vengeance", null);
		Console.showMessage("Spawning vengeance fleet for faction " + faction.getDisplayName() + " from " + market.getName());		
		return CommandResult.SUCCESS;
	}
}
