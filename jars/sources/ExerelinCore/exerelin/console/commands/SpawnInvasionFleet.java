package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.CollectionUtils;
import org.lwjgl.util.vector.Vector2f;

public class SpawnInvasionFleet implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (!context.isInCampaign()) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		String[] tmp = args.split(" ");
		
		// get source faction
		String factionId;
		if (tmp.length == 0 || tmp[0].isEmpty() || tmp[0].equalsIgnoreCase("any"))
		{
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
			List<String> factions = SectorManager.getLiveFactionIdsCopy();
			picker.addAll(factions);
			factionId = picker.pick();
		}
		else
			factionId = tmp[0];
		
		FactionAPI faction = getFaction(factionId);
		if (faction == null) {
			return CommandResult.ERROR;
		}
		
		// get target faction
		String targetFactionId;
		if (tmp.length <= 1 || tmp[1].isEmpty() || tmp[1].equalsIgnoreCase("any"))
		{
			// any hostile market is elgible
			targetFactionId = null;
		}
		else
			targetFactionId = tmp[1];
		
		FactionAPI targetFaction = getFaction(targetFactionId);
		//if (targetFaction == null) return CommandResult.ERROR;
		
		// get source and target markets
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		List<MarketAPI> sources = ExerelinUtilsFaction.getFactionMarkets(faction.getId());
		List<MarketAPI> targets;
		if (targetFaction != null) targets = ExerelinUtilsFaction.getFactionMarkets(targetFaction.getId());
		else targets = Global.getSector().getEconomy().getMarketsCopy();
		
		Vector2f playerPos = playerFleet.getLocationInHyperspace();
		MarketAPI closestTargetMarket = null;
		float closestTargetDist = 9999999;
		MarketAPI closestOriginMarket = null;
		float closestOriginDist = 9999999;
		
		// pick target market
		for (MarketAPI market : targets) {
			if (!ExerelinUtilsMarket.canBeInvaded(market, false))
				continue;
			if (!market.getFaction().isHostileTo(faction))
				continue;
			float distance = Misc.getDistance(playerPos, market.getPrimaryEntity().getLocationInHyperspace());			
			if (distance < closestTargetDist)
			{
				closestTargetDist = distance;
				closestTargetMarket = market;
			}
		}
		
		// pick source market
		for (MarketAPI market : sources) {
			float distance = Misc.getDistance(playerPos, market.getPrimaryEntity().getLocationInHyperspace());
			if (distance < closestOriginDist)
			{
				closestOriginDist = distance;
				closestOriginMarket = market;
			}
		}
		
		if (closestTargetMarket == null || closestOriginMarket == null)
		{
			Console.showMessage("Unable to find origin and/or target");
			return CommandResult.ERROR;
		}
		
		// spawn fleet
		float fp = InvasionFleetManager.getWantedFleetSize(closestTargetMarket);
		fp *= 1 + ExerelinConfig.getExerelinFactionConfig(factionId).invasionFleetSizeMod;
		InvasionIntel intel = new InvasionIntel(faction, closestOriginMarket, 
				closestTargetMarket, fp, 1);	
		if (intel == null) {
			Console.showMessage("Unable to spawn fleet");
			return CommandResult.ERROR;
		}
		Console.showMessage("Spawning invasion from " + closestOriginMarket.getName());
		Console.showMessage("Oscar Mike to " + closestTargetMarket.getName() + " (" + closestTargetMarket.getFaction().getDisplayName()
				+ ") in " + closestTargetMarket.getContainingLocation().getName());
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
}
