package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexTravelStage;
import java.util.List;

public class RemnantRaidTravelStage extends NexTravelStage {

	public RemnantRaidTravelStage(OffensiveFleetIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
		super(raid, from, to, requireNearTarget);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
	}
}
