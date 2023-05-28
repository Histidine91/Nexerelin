package exerelin.campaign.intel.defensefleet;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.utilities.StringHelper;
import java.util.List;

public class DefenseActionStage extends PirateRaidActionStage {
	
	protected float startingDwellTime = 150;
	protected float dwellTime = 150;
	protected DefenseFleetIntel defIntel;
	
	public DefenseActionStage(DefenseFleetIntel intel, MarketAPI market) {
		super(intel, market.getStarSystem());
		defIntel = intel;
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		// do nothing
	}
	
	@Override
	protected void updateStatus() {
		abortIfNeededBasedOnFP(true);
		if (status != RaidIntel.RaidStageStatus.ONGOING) return;
		
		if (dwellTime < 0) {
			status = RaidIntel.RaidStageStatus.SUCCESS;
			defIntel.reportOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
		}
	}
	
	@Override
	public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
		if (fleet == null) return false;
		
		return !market.getFaction().isHostileTo(fleet.getFaction());
	}	
	
	@Override
	protected void updateRoutes() {
		resetRoutes();
		
		((OffensiveFleetIntel)intel).sendEnteredSystemUpdate();
		
		MarketAPI target = ((OffensiveFleetIntel)intel).getTarget();
		
		List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
		for (RouteManager.RouteData route : routes) {
			if (target.getStarSystem() != null) { // so that fleet may spawn NOT at the target
				route.addSegment(new RouteManager.RouteSegment(5, target.getStarSystem().getCenter()));
			}
			route.addSegment(new RouteManager.RouteSegment(1000f, target.getPrimaryEntity()));
		}
	}
	
	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("defending", market.getName());
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("movingToDefend", market.getName());
	}
	
	@Override
	public String getRaidDefaultText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("patrollingNoTarget", null);
	}
	
	@Override
	public String getRaidInSystemText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("patrollingNoTarget", null);
	}
	
	@Override
	public String getRaidPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
		return StringHelper.getFleetAssignmentString("orbiting", from.getName());
	}
	
	@Override
	public void advance(float amount) {
		super.advance(amount);
		
		float days = Misc.getDays(amount);
		dwellTime -= days;
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		float opad = 10f;
		
		if (curr < index) return;
		
		if (status == RaidIntel.RaidStageStatus.ONGOING && curr == index) {
			info.addPara(StringHelper.getString("nex_defenseFleet", "intelStageAction"), 
					opad, Misc.getHighlightColor(), (int)dwellTime + "");
			return;
		}
		
		if (status == RaidIntel.RaidStageStatus.SUCCESS) {			
			info.addPara("The expeditionary force has succeeded.", opad); // shouldn't happen?
		} else {
			info.addPara("The expeditionary force has failed.", opad); // shouldn't happen?
		}
	}
}
