package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import java.awt.Color;

public class LuddicChurchBonusSubplugin extends FactionBonusSubplugin {
		
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg) {
		String origOwner = NexUtilsMarket.getOriginalOwner(intel.getMarket());
		if (origOwner == null) 
			return super.modifyMoraleDamageReceived(unit, dmg);
		
		if (NexUtilsFaction.isLuddicFaction(origOwner)) {
			Float mult = getSettingsFloat("luddef_moraleDamageTakenMult");
			if (mult != null) {
				dmg *= mult;
				if (unit.getType() == ForceType.MILITIA)
					dmg *= mult;
			}
		}
		
		return super.modifyMoraleDamageReceived(unit, dmg);
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) 
	{
		super.processTooltip(tooltip, expanded, tooltipParam);
		FactionAPI faction = getFaction();
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
	
	public static LuddicChurchBonusSubplugin loadPlugin(GroundBattleIntel intel, String factionId, String className) 
	{		
		LuddicChurchBonusSubplugin plugin = null;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			plugin = (LuddicChurchBonusSubplugin)clazz.newInstance();
			plugin.init(intel, factionId);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryForBattlePlugin.class).error("Failed to load faction subplugin " + className, ex);
		}
		
		return plugin;
	}
}
