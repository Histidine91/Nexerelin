package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import java.awt.Color;
import java.util.List;

public class WaitStage extends BaseRaidStage {
	
	protected OffensiveFleetIntel offFltIntel;
	protected SectorEntityToken token;
	protected float time;

	public WaitStage(OffensiveFleetIntel intel, SectorEntityToken token, float time) {
		super(intel);
		offFltIntel = intel;
		this.token = token;
		this.time = time;
	}
	
	@Override
	public void notifyStarted() {
		updateRoutes();
	}


	protected void updateRoutes() {
		resetRoutes();
		
		if (offFltIntel.getOutcome() != OffensiveOutcome.SUCCESS) {
			giveReturnOrdersToStragglers(getRoutes());
			return;
		}
		
		List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
		for (RouteManager.RouteData route : routes) {
			route.addSegment(new RouteManager.RouteSegment(time, token, AssembleStage.WAIT_STAGE));
			maxDays = time;
		}
	}
	
	protected void updateStatus() {
		abortIfNeededBasedOnFP(true);
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		if (status == RaidIntel.RaidStageStatus.FAILURE) {
			info.addPara("The raiding forces have failed to successfully reach the " +
					intel.getSystem().getNameWithLowercaseType() + ". The raid is now over.", opad);
		} else if (curr == index) {
			info.addPara("The raiding forces are currently waiting in the " + 
					intel.getSystem().getNameWithLowercaseType() + ".", opad);
		}
	}
}
