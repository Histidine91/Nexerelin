package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;
import java.awt.Color;

public class SpaceportPlugin extends IndustryForBattlePlugin {
	
	public static float BASE_LIFT_CAP = 50;	// at size 1
	
	@Override
	public void apply() {
		super.apply();
		
		if (indForBattle.isIndustryTrueDisrupted()) return;
		
		GroundBattleSide ourSide = indForBattle.getHoldingSide();
		ourSide.getLiftCapacity().modifyFlat(defId, getLiftCapacity(), indForBattle.getIndustry().getCurrentName());
		
	}
	
	@Override
	public void unapply() {
		super.unapply();
		GroundBattleSide ourSide = indForBattle.getHoldingSide();
		ourSide.getLiftCapacity().unmodify(defId);
	}
	
	public float getLiftCapacity() {
		int size = indForBattle.getIndustry().getMarket().getSize();
		
		float cap = (float)(BASE_LIFT_CAP * Math.pow(2, size));
		if (indForBattle.getIndustry().getId().equals(Industries.MEGAPORT))
			cap *= 8/5;
		return cap;
	}
	
	@Override
	public TooltipMakerAPI.TooltipCreator getModifierTooltip() {
		final GBDataManager.IndustryDef def = getDef();
				
		final Color h = Misc.getHighlightColor();
		return new TooltipMakerAPI.TooltipCreator() {
				@Override
				public boolean isTooltipExpandable(Object tooltipParam) {
					return false;
				}

				@Override
				public float getTooltipWidth(Object tooltipParam) {
					return 360;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					tooltip.addPara("- +%s lift capacity", 0, h, String.format("%.0f", getLiftCapacity()));
				}
		};
	}
}
