package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import static com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage.PREP_STAGE;
import static com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage.WAIT_STAGE;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.RemnantRaidIntel;

public class RemnantRaidAssembleStage extends NexRaidAssembleStage {
	
	public RemnantRaidAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint) {
		super(raid, gatheringPoint);
	}
	
	@Override
	protected void addRoutesAsNeeded(float amount) {
		if (spawnFP <= 0) return;
		
		float days = Misc.getDays(amount);
		
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
		
		RemnantRaidIntel raid = (RemnantRaidIntel)intel;
		CampaignFleetAPI base = raid.getBase();
		if (base == null || base.isAlive()) {
			status = RaidIntel.RaidStageStatus.FAILURE;
			return;
		}
		
		MarketAPI market = null;
		
		RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);
		
		String sid = raid.getRouteSourceId();
		RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, raid, null);
		
		extra.fleetType = pickNextType();
		float fp = getFP(extra.fleetType);
		
		//extra.fp = Misc.getAdjustedFP(fp, market);
		extra.fp = fp;
		extra.strength = Misc.getAdjustedStrength(fp, market);
		
		float prepDays = 3f + 3f * (float) Math.random();
		float travelDays = RouteLocationCalculator.getTravelDays(base, gatheringPoint);
				
		route.addSegment(new RouteManager.RouteSegment(prepDays, base, PREP_STAGE));
		route.addSegment(new RouteManager.RouteSegment(travelDays, base, gatheringPoint));
		route.addSegment(new RouteManager.RouteSegment(1000f, gatheringPoint, WAIT_STAGE));
		
		maxDays = Math.max(maxDays, prepDays + travelDays);
		//maxDays = 6f;
		
	}
	
	@Override
	public boolean isSourceKnown() {
		RemnantRaidIntel rri = (RemnantRaidIntel)intel;
		boolean known = rri.getBase().isVisibleToPlayerFleet();
		return known;
	}
}
