package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import static com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage.STRAGGLER;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.intel.RemnantRaidIntel;
import java.util.List;

public class RemnantRaidActionStage extends NexRaidActionStage {

	public RemnantRaidActionStage(RaidIntel raid, StarSystemAPI system) {
		super(raid, system);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) {
		SectorEntityToken base = ((RemnantRaidIntel)intel).getBase();
		for (RouteManager.RouteData route : stragglers) {
			SectorEntityToken from = Global.getSector().getHyperspace().createToken(route.getInterpolatedHyperLocation());
			
			route.setCustom(STRAGGLER);
			resetRoute(route);

			float travelDays = RouteLocationCalculator.getTravelDays(from, base);
			if (DebugFlags.RAID_DEBUG) {
				travelDays *= 0.1f;
			}
			
			float orbitDays = 1f + 1f * (float) Math.random();
			route.addSegment(new RouteManager.RouteSegment(travelDays, from, base));
			route.addSegment(new RouteManager.RouteSegment(orbitDays, base));
			
			//route.addSegment(new RouteSegment(2f + (float) Math.random() * 1f, base));
		}
	}
}
