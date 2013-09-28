package data.scripts.world.exerelin.commandQueue;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Created with IntelliJ IDEA.
 * User: Kirk
 * Date: 28/09/13
 * Time: 7:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class CommandSpawnPrebuiltFleet implements  BaseCommand {

    private SectorEntityToken spawnLocation;
    private int xOffset;
    private int yOffset;
    private CampaignFleetAPI fleet;

    public CommandSpawnPrebuiltFleet(SectorEntityToken spawnLocation, int xOffset, int yOffset, CampaignFleetAPI fleet)
    {
        this.spawnLocation = spawnLocation;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.fleet = fleet;
    }

    @Override
    public void executeCommand()
    {
        spawnLocation.getContainingLocation().spawnFleet(spawnLocation, xOffset, yOffset, fleet);
    }
}
