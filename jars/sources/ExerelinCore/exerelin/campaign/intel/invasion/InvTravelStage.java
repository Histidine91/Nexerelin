package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.InvasionIntel;
import java.awt.Color;

public class InvTravelStage extends TravelStage {
	
	public InvTravelStage(InvasionIntel invasion, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
		super(invasion, from, to, requireNearTarget);
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
