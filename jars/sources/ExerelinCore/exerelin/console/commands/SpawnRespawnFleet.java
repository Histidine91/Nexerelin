package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinConfig;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;

public class SpawnRespawnFleet implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        
        SectorAPI sector = Global.getSector();
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        
        Vector2f playerPos = playerFleet.getLocationInHyperspace();
        MarketAPI closestTargetMarket = null;
        float closestTargetDist = 9999999;
        
        // pick faction
        List<String> factions = ExerelinConfig.getBuiltInFactionsList(true);
        factions.addAll(ExerelinConfig.getModdedFactionsList(true));
        WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>();
        for (String faction : factions)
        {
            if (SectorManager.isFactionAlive(faction)) continue;
			if (faction.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
            factionPicker.add(faction);
        }
        String factionId = factionPicker.pick();
        if (factionId == null) factionId = playerAlignedFactionId;
        
        // pick target market
        for (MarketAPI market : markets) {
            //if (market.getFaction() == playerAlignedFaction) continue;
            if (market.getContainingLocation() != playerFleet.getContainingLocation()) continue;
            float distance = Misc.getDistance(playerPos, market.getPrimaryEntity().getLocation());
            if (distance < closestTargetDist)
            {
                closestTargetDist = distance;
                closestTargetMarket = market;
            }
        }
        
        if (closestTargetMarket == null)
        {
            Console.showMessage("Unable to find target");
                return CommandResult.ERROR;
        }
        
        // spawn fleet
        InvasionFleetManager.InvasionFleetData data = InvasionFleetManager.spawnRespawnFleet(Global.getSector().getFaction(factionId), 
                closestTargetMarket, closestTargetMarket, false);
        if (data == null) {
            Console.showMessage("Unable to spawn fleet");
            return CommandResult.ERROR;
        }
        Console.showMessage("Spawning " + data.fleet.getName() + ", sending to " + closestTargetMarket.getName());
        data.fleet.getContainingLocation().removeEntity(data.fleet);
        playerFleet.getContainingLocation().addEntity(data.fleet);
        data.fleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        return CommandResult.SUCCESS;
    }
}
