package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.InvasionIntel.InvasionOutcome;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class InvTravelStage extends TravelStage {
	
	public InvTravelStage(InvasionIntel invasion, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
		super(invasion, from, to, requireNearTarget);
	}
	
	protected void abortIfOutOfMarines(boolean giveReturnOrders) {
		float marines = 0;
		List<RouteManager.RouteData> routes = getRoutes();
		List<RouteManager.RouteData> stragglers = new ArrayList<>();	//getStragglers(routes, to, 1000);
		for (CampaignFleetAPI fleet : ((InvasionIntel)intel).getFleetsThatMadeIt(routes, stragglers))
		{
			marines += fleet.getCargo().getMarines();
		}
		float startingMarines = ((InvasionIntel)intel).getStartingMarines();
		if (marines/startingMarines < 0.4f)
		{
			Global.getLogger(this.getClass()).info("Invasion: Insufficient marines, aborting (" + marines + "/" + startingMarines + ")");
			status = RaidStageStatus.FAILURE;
			((InvasionIntel)intel).setOutcome(InvasionOutcome.TASK_FORCE_DEFEATED);
			if (giveReturnOrders) {
				giveReturnOrdersToStragglers(routes);
			}
		}
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
		
		if (status == RaidStageStatus.FAILURE) {
			info.addPara("The invasion force has failed to successfully reach the " +
					intel.getSystem().getNameWithLowercaseType() + ".", opad);
		} else if (curr == index) {
			info.addPara("The invasion force is currently travelling to the " + 
					intel.getSystem().getNameWithLowercaseType() + ".", opad);
		}
	}
}
