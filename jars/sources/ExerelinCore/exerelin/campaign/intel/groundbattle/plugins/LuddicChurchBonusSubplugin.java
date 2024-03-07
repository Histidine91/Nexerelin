package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;

import java.awt.*;

public class LuddicChurchBonusSubplugin extends FactionBonusSubplugin {
		
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg) {
		String origOwner = NexUtilsMarket.getOriginalOwner(intel.getMarket());
		if (origOwner == null) 
			return super.modifyMoraleDamageReceived(unit, dmg);
		
		if (NexUtilsFaction.isLuddicFaction(origOwner)) {
			Float mult = getSettingsFloat("luddef_moraleDamageTakenMult");
			if (mult != null) {
				dmg *= mult;
				if (intel.getMarket().hasCondition(Conditions.LUDDIC_MAJORITY))
					dmg *= mult;
			}
		}
		
		return super.modifyMoraleDamageReceived(unit, dmg);
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) 
	{
		super.processTooltip(tooltip, expanded, tooltipParam);
		Color good = Misc.getPositiveHighlightColor(), bad = Misc.getNegativeHighlightColor();
		Float moraleMult = (Float)conf.groundBattleSettings.get("luddef_moraleDamageTakenMult");
		tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
		if (moraleMult != null && moraleMult != 1) {
			Color col = moraleMult <= 1 ? good : bad;
			tooltip.addPara(GroundBattleIntel.getString("modifierLuddicMoraleDamageTakenMult"), 
					0, col, String.format("%.1f×", moraleMult));
		}
		tooltip.setBulletedListMode(null);
	}
}
