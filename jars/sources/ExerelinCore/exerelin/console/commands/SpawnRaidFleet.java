package exerelin.console.commands;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.raid.NexRaidIntel;
import static exerelin.console.commands.SpawnInvasionFleet.getMarket;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.MathUtils;

public class SpawnRaidFleet extends SpawnInvasionFleet 
{
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
		fp *= InvasionFleetManager.RAID_SIZE_MULT;
		fp *= InvasionFleetManager.getInvasionSizeMult(source.getFactionId());
		fp *= MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		NexRaidIntel intel = new NexRaidIntel(source.getFaction(), source, target, fp, 1);	
		if (intel == null) {
			Console.showMessage("Unable to spawn fleet");
			return CommandResult.ERROR;
		}
		intel.init();
		Console.showMessage("Spawning raid from " + source.getName());
		Console.showMessage("Oscar Mike to " + target.getName() + " (" + target.getFaction().getDisplayName()
				+ ") in " + target.getContainingLocation().getName());
		return CommandResult.SUCCESS;
	}
}
