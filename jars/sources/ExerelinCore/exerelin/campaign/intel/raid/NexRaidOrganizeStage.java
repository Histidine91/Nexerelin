package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

public class NexRaidOrganizeStage extends OrganizeStage {
	
	public NexRaidOrganizeStage(RaidIntel raid, MarketAPI market, float durDays) {
		super(raid, market, durDays);
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
		
		int days = Math.round(maxDays - elapsed);
		String strDays = RaidIntel.getDaysString(days);
		
		String timing = getForcesString() + " should begin assembling in %s " + strDays + ".";
		if (days < 2) {
			timing = getForcesString() + " should begin assembling shortly.";
		}
		
		String raid = getRaidString();
		if (status == RaidIntel.RaidStageStatus.FAILURE) {
			info.addPara("The " + raid + " has been disrupted in the planning stages and will not happen.", opad);
		} else if (curr == index) {
			boolean known = !market.isHidden() || !market.getPrimaryEntity().isDiscoverable();
			if (known) {
				info.addPara("The " + raid + " is currently being planned " + 
						market.getOnOrAt() + " " + market.getName() + ". " + timing,
						opad, h, "" + days);
			} else {
				info.addPara("The " + raid + " is currently in the planning stages. " + timing,
						opad, h, "" + days);
			}
		}
	}
	
}
