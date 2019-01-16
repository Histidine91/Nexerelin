package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import java.util.List;

public class RemnantRaidTravelStage extends TravelStage {

	public RemnantRaidTravelStage(RaidIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
		super(raid, from, to, requireNearTarget);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
	}
}
