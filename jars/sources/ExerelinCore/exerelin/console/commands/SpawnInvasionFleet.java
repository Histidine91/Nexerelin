package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.invasion.InvasionIntel;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.CollectionUtils;

public class SpawnInvasionFleet implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (!context.isInCampaign()) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		String[] tmp = args.split(" ");
		
		if (tmp.length < 2) {
			return CommandResult.BAD_SYNTAX;
		}
		
		MarketAPI source = getMarket(tmp[0]);
		MarketAPI target = getMarket(tmp[1]);
		
		if (source == null) {
			Console.showMessage("Invalid source market");
			return CommandResult.ERROR;
		}
		if (target == null) {
			Console.showMessage("Invalid target market");
			return CommandResult.ERROR;
		}
		
		// spawn fleet
		float fp = InvasionFleetManager.getWantedFleetSize(source.getFaction(), target, 0.2f, false);
		fp *= InvasionFleetManager.getInvasionSizeMult(source.getFactionId());
		//fp *= MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		InvasionIntel intel = new InvasionIntel(source.getFaction(), source, target, fp, 1);	
		if (intel == null) {
			Console.showMessage("Unable to spawn fleet");
			return CommandResult.ERROR;
		}
		intel.init();
		Console.showMessage("Spawning invasion from " + source.getName());
		Console.showMessage("Oscar Mike to " + target.getName() + " (" + target.getFaction().getDisplayName()
				+ ") in " + target.getContainingLocation().getName());
		return CommandResult.SUCCESS;
	}
	
	/**
	 * Gets best faction match; prints error message if not found.
	 * @param factionId
	 * @return 
	 */
	public static FactionAPI getFaction(String factionId)
	{
		if (factionId == null)
		{
			return null;
		}
		
		FactionAPI faction = CommandUtils.findBestFactionMatch(factionId);
		if (faction == null)
		{
			final List<String> ids = new ArrayList<>();
			for (FactionAPI existsFaction : Global.getSector().getAllFactions())
			{
				ids.add(existsFaction.getId());
			}
			
			Console.showMessage("Error: no such faction '" + factionId
					+ "'! Valid factions: " + CollectionUtils.implode(ids) + ".");
			return null;
		}
		return faction;
	}
	
	public static MarketAPI getMarket(String marketId) {
		return CommandUtils.findBestMarketMatch(marketId);
	}
}
