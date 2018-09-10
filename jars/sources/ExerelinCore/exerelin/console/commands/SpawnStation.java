package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.fleets.DefenceStationManager;
import exerelin.utilities.ExerelinUtils;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class SpawnStation implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) 
	{
        if (!context.isInCampaign())
        {
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
		
        try
        {
			MarketAPI market = ExerelinUtils.getClosestMarket(faction.getId());
			if (market == null)
				market = Global.getSector().getEconomy().getMarketsCopy().get(0);
			
            final CampaignFleetAPI toSpawn = DefenceStationManager.getManager().createFleet(market);
            DefenceStationManager.getManager().addStationToFleet(toSpawn, market);

            // Spawn fleet around player
            final Vector2f offset = MathUtils.getRandomPointOnCircumference(null,
                    Global.getSector().getPlayerFleet().getRadius()
                    + toSpawn.getRadius() + 150f);
            Global.getSector().getCurrentLocation().spawnFleet(
                    Global.getSector().getPlayerFleet(), offset.x, offset.y, toSpawn);
            Global.getSector().addPing(toSpawn, "danger");

            Console.showMessage("Spawned a station, aligned with faction " + faction.getId() 
					+ ", using market " + market.getName());
            return CommandResult.SUCCESS;
        }
        catch (Exception ex)
        {
            Console.showMessage("Unable to spawn station for faction '"
                    + faction.getId() + "'!");
            return CommandResult.ERROR;
        }
    }
}
