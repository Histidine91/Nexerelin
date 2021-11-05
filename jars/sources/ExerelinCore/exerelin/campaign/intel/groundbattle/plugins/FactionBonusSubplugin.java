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
import exerelin.utilities.StringHelper;
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
	
	// fucking awful hax for a ClassCastException a couple of people are having
	// how is this possible, the value is never stored as a double in the map!
	public Float getSettingsFloat(String key) {
		try {
			return (Float)conf.groundBattleSettings.get(key);
		} catch (ClassCastException ex) {
			Double value = (Double)conf.groundBattleSettings.get(key);
			if (value == null) return null;
			return (float)(double)value;
		}
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
		FactionBonusSubplugin plugin = null;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			plugin = (FactionBonusSubplugin)clazz.newInstance();
			plugin.init(intel, factionId);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryForBattlePlugin.class).error("Failed to load faction subplugin " + className, ex);
		}
		
		return plugin;
	}
}
