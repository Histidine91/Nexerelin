package exerelin.campaign.econ.faction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.FactionConditionPlugin;
import exerelin.utilities.StringHelper;

public class OutlawFCSubplugin extends BaseFactionConditionSubplugin {
	
	public static float ACCESSIBILITY_BONUS = 0.1f; 
	
	@Override
	public void apply(String id) {
		plugin.getMarket().getAccessibilityMod().modifyFlat(id, ACCESSIBILITY_BONUS, plugin.getName());
	}
	
	@Override
	public void unapply(String id) {
		plugin.getMarket().getAccessibilityMod().unmodify(id);
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		float opad = 10;
		String str = FactionConditionPlugin.getString("condDesc_outlaw");
		str = StringHelper.substituteFactionTokens(str, factionId);
		String access = String.format("%.0f", ACCESSIBILITY_BONUS * 100);
		str = StringHelper.substituteToken(str, "$accessibility", access);
		
		tooltip.addPara(str, opad, Misc.getHighlightColor(), access);
	}
	
	@Override
	public String getName() {
		String fname = Misc.ucFirst(Global.getSector().getFaction(factionId).getDisplayName());
		return String.format(FactionConditionPlugin.getString("condName_outlaw"), fname);
	}
}
