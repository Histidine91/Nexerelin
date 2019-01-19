package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

@Deprecated
public class InvReturnStage extends ReturnStage {

	public InvReturnStage(RaidIntel raid) {
		super(raid);
	}
	
	// TBD
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		// blank
	}
}