package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import java.util.List;

public class RemnantRaidReturnStage extends NexRaidReturnStage {
	
	public RemnantRaidReturnStage(RaidIntel raid) {
		super(raid);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
	}
}
