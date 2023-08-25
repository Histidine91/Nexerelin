package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsGUI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FactionBonusPlugin extends BaseGroundBattlePlugin {
	
	protected transient Map<String, FactionBonusSubplugin> subplugins = new HashMap<>();
	
	protected Object readResolve() {
		subplugins = new HashMap<>();
		return this;
	}
	
	public FactionBonusSubplugin getSubpluginForFaction(String factionId) {
		if (subplugins.containsKey(factionId)) {
			return subplugins.get(factionId);
		}
		
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		if (conf.groundBattleSettings == null)
			return null;
		
		String pluginClass = "exerelin.campaign.intel.groundbattle.plugins.FactionBonusSubplugin";
		if (conf.groundBattleSettings.containsKey("plugin")) {
			pluginClass = (String)conf.groundBattleSettings.get("plugin");
		}
		
		FactionBonusSubplugin sub = FactionBonusSubplugin.loadPlugin(intel, factionId, pluginClass);
		subplugins.put(factionId, sub);
		return sub;
	}
	
	@Override
	public MutableStat modifyAttackStat(GroundUnit unit, MutableStat stat) {
		FactionBonusSubplugin sub = getSubpluginForFaction(unit.getFaction().getId());
		if (sub == null) return stat;
		return sub.modifyDamageDealt(unit, stat);
	}
	
	@Override
	public MutableStat modifyDamageReceived(GroundUnit unit, MutableStat dmg) {
		FactionBonusSubplugin sub = getSubpluginForFaction(unit.getFaction().getId());
		if (sub == null) return dmg;
		return sub.modifyDamageReceived(unit, dmg);
	}
	
	@Override
	public float modifyMoraleDamageReceived(GroundUnit unit, float dmg) {
		FactionBonusSubplugin sub = getSubpluginForFaction(unit.getFaction().getId());
		if (sub == null) return dmg;
		return sub.modifyMoraleDamageReceived(unit, dmg);
	}
	
	@Override
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {
		if (isAttacker == null) return;
		
		List<FactionBonusSubplugin> subs = new ArrayList<>();
		for (GroundUnit unit : intel.getSide(isAttacker).getUnits()) {
			String factionId = unit.getFaction().getId();
			FactionBonusSubplugin sub = getSubpluginForFaction(factionId);
			if (sub != null && !subs.contains(sub)) subs.add(sub);
		}
		
		if (subs.isEmpty()) return;
		FactionAPI faction = Global.getSector().getFaction(subs.get(0).factionId);
		
		NexUtilsGUI.CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, GroundBattleIntel.getString("modifierFaction"), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				faction.getCrest(), GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				faction.getBaseUIColor(), true, getModifierTooltip(isAttacker));
		
		info.addCustom(gen.panel, pad);
	}
	
	public TooltipMakerAPI.TooltipCreator getModifierTooltip(final boolean isAttacker) {
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
					processTooltip(tooltip, expanded, tooltipParam, isAttacker);
				}
		};
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam, boolean isAttacker) {
		float opad = 10;
		tooltip.addPara(GroundBattleIntel.getString("modifierFactionDescPre"), 0);
		
		List<FactionBonusSubplugin> subs = new ArrayList<>();
		for (GroundUnit unit : intel.getSide(isAttacker).getUnits()) {
			String factionId = unit.getFaction().getId();
			FactionBonusSubplugin sub = getSubpluginForFaction(factionId);
			if (sub != null && !subs.contains(sub)) subs.add(sub);
		}
		
		if (subs.isEmpty()) return;
		
		for (FactionBonusSubplugin sub : subs) {
			//Global.getLogger(this.getClass()).info("Testing subplugin for faction " + sub.factionId);
			sub.processTooltip(tooltip, expanded, tooltipParam);
		}
		
		tooltip.addPara(GroundBattleIntel.getString("modifierFactionDescPost"), opad);
	}

	@Override
	public float getSortOrder() {
		return -900;
	}
}
