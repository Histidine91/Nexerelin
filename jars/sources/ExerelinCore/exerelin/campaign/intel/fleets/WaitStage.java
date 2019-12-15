package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI.FleetActionDelegate;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.List;

public class WaitStage extends BaseRaidStage implements FleetActionDelegate {
	
	protected OffensiveFleetIntel offFltIntel;
	protected SectorEntityToken token;
	protected float time;
	protected boolean aggressive;

	public WaitStage(OffensiveFleetIntel intel, SectorEntityToken token, float time, boolean aggressive) 
	{
		super(intel);
		offFltIntel = intel;
		this.token = token;
		this.time = time;
		this.aggressive = aggressive;
	}
	
	@Override
	public void notifyStarted() {
		updateRoutes();
	}
	
	public boolean isAggressive() {
		return aggressive;
	}


	protected void updateRoutes() {
		resetRoutes();
		
		if (offFltIntel.getOutcome() != OffensiveOutcome.SUCCESS) {
			giveReturnOrdersToStragglers(getRoutes());
			return;
		}
		
		maxDays = time;
		List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
		for (RouteManager.RouteData route : routes) {
			route.addSegment(new RouteManager.RouteSegment(0.1f, token, 
					aggressive ? null : AssembleStage.WAIT_STAGE));
			route.addSegment(new RouteManager.RouteSegment(time, token, 
					aggressive ? null : AssembleStage.WAIT_STAGE));
			
			// update fleet delegate
			CampaignFleetAPI fleet = route.getActiveFleet();
			if (fleet != null) {
				RouteFleetAssignmentAI ai = ExerelinUtilsFleet.getRouteAssignmentAI(fleet);
				ai.setDelegate(this);
			}
		}
	}
	
	@Override
	protected void updateStatus() {
		abortIfNeededBasedOnFP(true);
		if (elapsed > maxDays) {
			status = RaidIntel.RaidStageStatus.SUCCESS;
		}
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		float opad = 10f;
		
		if (curr == index) {
			info.addPara(StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
					"intelStageWait", "$market", token.getName()), opad);
		}
	}

	@Override
	public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
		return false;
	}
	
	@Override
	public String getRaidPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
		return StringHelper.getFleetAssignmentString("orbiting", from.getName());
	}
	
	@Override
	public String getRaidInSystemText(CampaignFleetAPI fleet) {
		return getCommonActionText();
	}
	
	@Override
	public String getRaidDefaultText(CampaignFleetAPI fleet) {
		return getCommonActionText();
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return "<this should not appear>";
	}

	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return "<this should not appear>";
	}

	@Override
	public void performRaid(CampaignFleetAPI arg0, MarketAPI market) {
	}
	
	protected String getCommonActionText() {
		if (aggressive) return StringHelper.getFleetAssignmentString("defending", token.getName());
		return StringHelper.getFleetAssignmentString("rendezvous", null);
	}
}
