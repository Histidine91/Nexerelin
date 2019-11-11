package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.utilities.StringHelper;

public class ColonyOrganizeStage extends NexOrganizeStage {
	
	public ColonyOrganizeStage(ColonyExpeditionIntel col, MarketAPI market, float durDays) {
		super(col, market, durDays);
	}
		
	@Override
	protected String getRaidString() {
		return StringHelper.getString("nex_colonyFleet", "colonyFleet");
	}
	
	@Override
	protected void updateStatus() {
		super.updateStatus();
		if (offFltIntel.getTarget().isInEconomy()) {
			status = RaidIntel.RaidStageStatus.FAILURE;
			((ColonyExpeditionIntel)intel).notifyQueueJumpedEarly();
		}
	}
}