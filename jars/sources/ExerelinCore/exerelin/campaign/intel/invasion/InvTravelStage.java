package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import org.histidine.foundation.scripts.StringHelper;

public class InvTravelStage extends TravelStage {
	
	public InvTravelStage(RaidIntel invasion, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
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
		
		String key = "intelStageTravel";
		String loc = intel.getSystem().getNameWithLowercaseType();
		if (status == RaidStageStatus.FAILURE) {
			key = "intelStageTravelFailed";
			info.addPara(StringHelper.getStringAndSubstituteToken("exerelin_invasion", key, "$location", loc), opad);
		} else if (curr == index) {
			info.addPara(StringHelper.getStringAndSubstituteToken("exerelin_invasion", key, "$location", loc), opad);
		}
	}
}
