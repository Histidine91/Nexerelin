package exerelin.console.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
import exerelin.utilities.NexConfig;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class SpawnRespawnFleet implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (!context.isInCampaign()) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		String[] tmp = args.split(" ");
		
		// get faction to respawn
		String factionId;
		if (tmp.length == 0 || tmp[0].isEmpty())
		{
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
			List<String> factions = NexConfig.getFactions(true, false);
			for (String faction : factions)
			{
				if (faction.equals(Factions.PLAYER)) continue;
				if (SectorManager.isFactionAlive(faction)) continue;
				picker.add(faction);
			}
			factionId = picker.pick();
		}
		else
			factionId = tmp[0];
		
		if (factionId == null)
		{
			Console.showMessage("Error: No available faction to respawn");
			return CommandResult.ERROR;
		}
		
		FactionAPI faction = SpawnInvasionFleet.getFaction(factionId);
		if (faction == null) return CommandResult.ERROR;
		
		Console.showMessage("Attempting to respawn faction " + faction.getDisplayName());
			
		// spawn fleet
		RespawnInvasionIntel intel = SectorManager.spawnRespawnFleet(faction, null, true, true);
		if (intel == null) {
			Console.showMessage("Unable to spawn fleet");
			return CommandResult.ERROR;
		}
		intel.init();
		MarketAPI target = intel.getTarget();
		Console.showMessage("Spawning respawn fleet(s) from " + intel.getMarketFrom().getName());
		Console.showMessage("Oscar Mike to " + target.getName() + " (" + target.getFaction().getDisplayName()
				+ ") in " + target.getContainingLocation().getName());
		return CommandResult.SUCCESS;
	}
}
