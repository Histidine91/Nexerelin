package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class NexTravelStage extends TravelStage {
	
	protected OffensiveFleetIntel offFltIntel;

	public NexTravelStage(OffensiveFleetIntel intel, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
		super(intel, from, to, requireNearTarget);
		offFltIntel = intel;
	}
	
	protected Object readResolve() {
		if (offFltIntel == null)
			offFltIntel = (OffensiveFleetIntel)intel;
		
		return this;
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		float opad = 10f;
		
		String key = "stageTravel";
		String loc = intel.getSystem().getNameWithLowercaseType();
		if (isFailed(curr, index)) {
			key = "stageTravelFailed";
		} else if (curr == index) {
		} else {
			return;
		}
		String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel", key, "$location", loc);
		str = StringHelper.substituteToken(str, "$theForceType", offFltIntel.getForceTypeWithArticle(), true);
		str = StringHelper.substituteToken(str, "$isOrAre", offFltIntel.getForceTypeIsOrAre());
		str = StringHelper.substituteToken(str, "$hasOrHave", offFltIntel.getForceTypeHasOrHave());
		info.addPara(str, opad);
	}
	
	protected boolean isFailed(int curr, int index) {
		if (status == RaidIntel.RaidStageStatus.FAILURE)
			return true;
		if (curr == index && offFltIntel.getOutcome() == OffensiveFleetIntel.OffensiveOutcome.FAIL)
			return true;
		
		return false;
	}
}
