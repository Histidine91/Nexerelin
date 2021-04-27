package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;

public class SpaceportPlugin extends IndustryForBattlePlugin {
	
	public static float DROP_COST_MULT = 0.5f;
	public static float DROP_COST_MULT_MEGAPORT = 0.25f;
	
	@Override
	public void apply() {
		super.apply();
		
		if (indForBattle.isIndustryTrueDisrupted()) return;
		
		GroundBattleSide ourSide = indForBattle.getHoldingSide();
		ourSide.getDropCostMod().modifyMult(defId, getDropCostMult(), indForBattle.getIndustry().getCurrentName());
		
	}
	
	@Override
	public void unapply() {
		super.unapply();
		GroundBattleSide ourSide = indForBattle.getHoldingSide();
		ourSide.getDropCostMod().unmodify(defId);
	}
	
	public float getDropCostMult() {
		if (indForBattle.getIndustry().getId().equals(Industries.MEGAPORT))
			return DROP_COST_MULT_MEGAPORT;
		return DROP_COST_MULT;
	}
	
	@Override
	public boolean hasTooltip() {
		return true;
	}
	
	@Override
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		super.processTooltip(tooltip, expanded, tooltipParam);
		tooltip.addPara("- %s drop costs", 0, Misc.getHighlightColor(), String.format("%.1f×", getDropCostMult()));
	}
}
