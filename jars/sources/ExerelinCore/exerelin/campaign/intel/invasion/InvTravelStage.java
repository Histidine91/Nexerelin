package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexTravelStage;

@Deprecated
public class InvTravelStage extends NexTravelStage {
	
	public InvTravelStage(OffensiveFleetIntel invasion, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
		super(invasion, from, to, requireNearTarget);
	}
}
