package exerelin.campaign.intel.invasion;

import java.awt.Color;

import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class InvReturnStage extends ReturnStage {

	public InvReturnStage(RaidIntel raid) {
		super(raid);
	}
	
	// TBD
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		if (curr >= index) {
			//info.addPara("The inspection is over, and the task force is returning to its home base.", opad);
		}
	}
}