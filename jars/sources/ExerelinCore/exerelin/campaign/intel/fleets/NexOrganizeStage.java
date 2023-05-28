package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class NexOrganizeStage extends OrganizeStage {
	
	protected OffensiveFleetIntel offFltIntel;

	public NexOrganizeStage(OffensiveFleetIntel intel, MarketAPI market, float durDays) {
		super(intel, market, durDays);
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
		String key = "stageOrganize";
		boolean haveTiming = true;
		boolean printSource = false;
		if (isFailed(curr, index)) {
			key = "stageOrganizeDisrupted";
			haveTiming = false;
		} else if (curr == index) {
			boolean known = !market.isHidden() || !market.getPrimaryEntity().isDiscoverable();
			if (known) {
				printSource = true;
			} else {
				key = "stageOrganizeUnknown";
			}
		} else {
			return;
		}
		String str = StringHelper.getString("nex_fleetIntel", key);
		str = StringHelper.substituteToken(str, "$theAction", raid, true);
		if (printSource) {
			str = StringHelper.substituteToken(str, "$onOrAt", market.getOnOrAt());
			str = StringHelper.substituteToken(str, "$market", market.getName());
		}
		if (haveTiming)
			str += " " + timing;
		info.addPara(str, opad, h, "" + days);
	}
	
	protected boolean isFailed(int curr, int index) {
		if (status == RaidStageStatus.FAILURE)
			return true;
		if (curr == index && offFltIntel.getOutcome() == OffensiveOutcome.FAIL)
			return true;
		
		return false;
	}
	
	@Override
	protected String getForcesString() {
		return offFltIntel.getForceTypeWithArticle();
	}
	
	@Override
	protected String getRaidString() {
		return offFltIntel.getActionName();
	}
	
	@Override
	protected void updateStatus() {
		super.updateStatus();
		boolean fail = false;
		// fail if market is no longer in economy
		if (offFltIntel.hasMarket() && !market.isInEconomy()) {
			fail = true;
		}
		// fail if spaceport + any present military base are disrupted
		else if (offFltIntel.hasMarket() && offFltIntel.requiresSpaceportOrBase) {
			if (!NexUtilsMarket.hasWorkingSpaceport(market) && !market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY))
				fail = true;
		}
		if (fail) {
			status = RaidStageStatus.FAILURE;
			offFltIntel.reportOutcome(OffensiveOutcome.FAIL);
		}
	}
	
	// from BaseRaidStage, skips the version in OrganizeStage (which cancels raid if source has no military base)
	@Override
	public void advance(float amount) {
		float days = Misc.getDays(amount);
		
		elapsed += days;
		
		statusInterval.advance(days);
		if (statusInterval.intervalElapsed()) {
			updateStatus();
		}
	}
}
