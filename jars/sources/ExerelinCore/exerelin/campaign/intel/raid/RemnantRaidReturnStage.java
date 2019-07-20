package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexReturnStage;
import java.util.List;

public class RemnantRaidReturnStage extends NexReturnStage {
	
	public RemnantRaidReturnStage(OffensiveFleetIntel raid) {
		super(raid);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
	}
}
