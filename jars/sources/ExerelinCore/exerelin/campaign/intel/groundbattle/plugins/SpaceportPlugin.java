package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;

public class SpaceportPlugin extends IndustryForBattlePlugin {
	
	public static float DROP_COST_MULT = 0.6f;
	public static float DROP_COST_MULT_MEGAPORT = 0.4f;
	//public static int MOVE_POINTS = 2;
	//public static int MOVE_POINTS_MEGAPORT = 3;
	
	@Override
	public void apply() {
		super.apply();
		
		if (indForBattle.isIndustryTrueDisrupted()) return;
		
		GroundBattleSide ourSide = indForBattle.getHoldingSide();
		String name = indForBattle.getName();
		ourSide.getDropCostMod().modifyMult(defId, getDropCostMult(), name);		
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
		tooltip.addPara("- " + GroundBattleIntel.getString("modifierDropCost"), 
				0, Misc.getHighlightColor(), String.format("%.1f×", getDropCostMult()));
	}
}
