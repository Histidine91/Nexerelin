package data.scripts.world.exerelin;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public interface FleetScript extends Script {

    public void run(CampaignFleetAPI fleet);

}
