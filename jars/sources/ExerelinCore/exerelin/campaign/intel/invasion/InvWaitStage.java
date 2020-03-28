package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.WaitStage;

/**
 * Go home if planet is captured by someone else
 */
public class InvWaitStage extends WaitStage {
	
	public InvWaitStage(OffensiveFleetIntel intel, SectorEntityToken token, float time, boolean aggressive) 
	{
		super(intel, token, time, aggressive);
	}
	
	@Override
	protected void updateStatus() {
		if (offFltIntel.getTarget().getFaction() != offFltIntel.getFaction()) {
			status = RaidIntel.RaidStageStatus.SUCCESS;
			giveReturnOrdersToStragglers(getRoutes());
			return;
		}
		super.updateStatus();
	}
}
