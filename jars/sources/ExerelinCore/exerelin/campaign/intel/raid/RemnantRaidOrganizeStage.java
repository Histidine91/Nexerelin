package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;

public class RemnantRaidOrganizeStage extends NexOrganizeStage {

	public RemnantRaidOrganizeStage(RemnantRaidIntel raid, MarketAPI market, float durDays) {
		super(raid, market, durDays);
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		int days = Math.round(maxDays - elapsed);
		String strDays = RaidIntel.getDaysString(days);
		
		String timing;
		if (days >= 2) {
			timing = StringHelper.getString("nex_fleetIntel", "stageOrganizeTiming");
			timing = StringHelper.substituteToken(timing, "$theForceType", getForcesString(), true);
			timing = StringHelper.substituteToken(timing, "$strDays", strDays);
		} else {
			timing = StringHelper.getString("nex_fleetIntel", "stageOrganizeTimingSoon");
			timing = StringHelper.substituteToken(timing, "$theForceType", getForcesString(), true);
		}
		
		String raid = offFltIntel.getActionNameWithArticle();
		String cat = "nex_fleetIntel";
		String key;
		boolean haveTiming = true;
		boolean printSource = false;
		RemnantRaidIntel rri = (RemnantRaidIntel)intel;
		
		if (isFailed(curr, index)) {
			key = "stageOrganizeDisrupted";
			haveTiming = false;
		} else if (curr == index) {
			boolean known = rri.isSourceKnown();
			if (known) {
				cat = "exerelin_raid";
				key = "stageOrganizeRemnant";
				printSource = true;
			} else {
				key = "stageOrganizeUnknown";
			}
		} else {
			return;
		}
		
		String str = StringHelper.getString(cat, key);
		str = StringHelper.substituteToken(str, "$theAction", raid, true);
		if (printSource) {
			str = StringHelper.substituteToken(str, "$location", rri.getBase()
					.getContainingLocation().getNameWithLowercaseType());
		}
		if (haveTiming)
			str += " " + timing;
		
		info.addPara(str, opad, h, "" + days);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
	}
}
