package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager.InvasionFleetData;
import exerelin.utilities.ExerelinConfig;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class SpawnRespawnFleet implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (context != CommandContext.CAMPAIGN_MAP) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		String[] tmp = args.split(" ");
		
		// get faction to respawn
		String factionId;
		if (tmp.length == 0 || tmp[0].isEmpty())
		{
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
			List<String> factions = ExerelinConfig.getFactions(true, false);
			for (String faction : factions)
			{
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
			
		// spawn fleet
		InvasionFleetData data = SectorManager.spawnRespawnFleet(faction, null, true);
		if (data == null) {
			Console.showMessage("Unable to spawn fleet");
			return CommandResult.ERROR;
		}
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		Console.showMessage("Spawning " + data.fleet.getNameWithFaction() + " from " + data.sourceMarket.getName());
		Console.showMessage("Oscar Mike to " + data.targetMarket.getName() + " (" + data.target.getFaction().getDisplayName() 
				+ ") in " + data.target.getContainingLocation().getName());
		data.fleet.getContainingLocation().removeEntity(data.fleet);
		playerFleet.getContainingLocation().addEntity(data.fleet);
		data.fleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
		data.fleet.addAssignmentAtStart(FleetAssignment.STANDING_DOWN, data.fleet, 0.5f, null);	// so it doesn't instantly attack player
		return CommandResult.SUCCESS;
	}
}
