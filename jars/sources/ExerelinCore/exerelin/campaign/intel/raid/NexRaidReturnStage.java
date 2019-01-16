package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class NexRaidReturnStage extends ReturnStage 
{
	public NexRaidReturnStage(RaidIntel raid) {
		super(raid);
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		// blank
	}
}
