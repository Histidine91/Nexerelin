package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import static com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage.STRAGGLER;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.List;

public class ColonyReturnStage extends ReturnStage {

	public ColonyReturnStage(RaidIntel raid) {
		super(raid);
	}
	
	// TBD
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		// blank
	}
	
	// go "home" to new colony instead of origin market if appropriate
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) {
		for (RouteManager.RouteData route : stragglers) {
			SectorEntityToken from = Global.getSector().getHyperspace().createToken(route.getInterpolatedHyperLocation());
			
			route.setCustom(STRAGGLER);
			resetRoute(route);
			
			SectorEntityToken dest = route.getMarket().getPrimaryEntity();
			ColonyExpeditionIntel colIntel = (ColonyExpeditionIntel)intel;
			if (colIntel.getTarget().getFaction() == colIntel.getFaction())
				dest = colIntel.getTarget().getPrimaryEntity();

			float travelDays = RouteLocationCalculator.getTravelDays(from, dest);
			if (DebugFlags.RAID_DEBUG || DebugFlags.FAST_RAIDS) {
				travelDays *= 0.1f;
			}
			
			float orbitDays = 1f + 1f * (float) Math.random();
			route.addSegment(new RouteManager.RouteSegment(travelDays, from, dest));
			route.addSegment(new RouteManager.RouteSegment(orbitDays, dest));
			
			//route.addSegment(new RouteSegment(2f + (float) Math.random() * 1f, route.getMarket().getPrimaryEntity()));
		}
	}
}