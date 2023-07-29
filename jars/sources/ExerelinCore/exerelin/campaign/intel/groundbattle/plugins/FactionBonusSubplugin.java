package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;

import java.awt.Color;

/**
 * Generates damage dealt/received and morale damage received modifiers for
 * a specific faction. Transient, created on demand.
 * @author Histidine
 */
public class FactionBonusSubplugin {
	
	public String factionId;
	public GroundBattleIntel intel;	
	protected NexFactionConfig conf;
	
	public void init(GroundBattleIntel intel, String factionId) {
		this.intel = intel;
		this.factionId = factionId;
		conf = NexConfig.getFactionConfig(factionId);
	}
	
	protected FactionAPI getFaction() {
		return Global.getSector().getFaction(factionId);
	}

	public Float getSettingsFloat(String key) {
		Object val = conf.groundBattleSettings.get(key);
		if (val == null) return null;

		if (val instanceof Float) return (Float)val;
		else if (val instanceof Double) return ((Double)val).floatValue();
		else if (val instanceof Integer) return ((Integer)val).floatValue();
		else if (val instanceof String) return Float.parseFloat((String)val);

		return (float)val;	// if there's a ClassCastException just throw it
	}
	
	public MutableStat modifyDamageDealt(GroundUnit unit, MutableStat dmg) {
		Float mult = getSettingsFloat("attackMult");
		if (mult != null)
			dmg.modifyMult("bonus_" + factionId, mult, getFaction().getDisplayName());
		return dmg;
	}
	
	public MutableStat modifyDamageReceived(GroundUnit unit, MutableStat dmg) {
		Float mult = getSettingsFloat("damageTakenMult");
		if (mult != null)
			dmg.modifyMult("bonus_" + factionId, mult, getFaction().getDisplayName());
		return dmg;
	}
	
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg) {
		Float mult = getSettingsFloat("moraleDamageTakenMult");
		if (mult != null)
			dmg *= mult;
		return dmg;
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) 
	{
		FactionAPI faction = getFaction();
		tooltip.addPara(faction.getDisplayName(), faction.getBaseUIColor(), 3);
		tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
		Color good = Misc.getPositiveHighlightColor(), bad = Misc.getNegativeHighlightColor();
		Float atkMult = getSettingsFloat("attackMult");
		if (atkMult != null && atkMult != 1) {
			Color col = atkMult >= 1 ? good : bad;
			tooltip.addPara(GroundBattleIntel.getString("modifierFactionAttackMult"), 
					0, col, String.format("%.1f×", atkMult));
		}
		Float defMult = getSettingsFloat("damageTakenMult");
		if (defMult != null && defMult != 1) {
			Color col = defMult <= 1 ? good : bad;
			tooltip.addPara(GroundBattleIntel.getString("modifierFactionDamageTakenMult"), 
					0, col, String.format("%.1f×", defMult));
		}
		Float moraleMult = getSettingsFloat("moraleDamageTakenMult");
		if (moraleMult != null && moraleMult != 1) {
			Color col = moraleMult <= 1 ? good : bad;
			tooltip.addPara(GroundBattleIntel.getString("modifierFactionMoraleDamageTakenMult"), 
					0, col, String.format("%.1f×", moraleMult));
		}
		tooltip.setBulletedListMode(null);
	}
	
	public static FactionBonusSubplugin loadPlugin(GroundBattleIntel intel, String factionId, String className) 
	{		
		FactionBonusSubplugin plugin = (FactionBonusSubplugin)NexUtils.instantiateClassByName(className);
		if (plugin != null) plugin.init(intel, factionId);
		
		return plugin;
	}
}
