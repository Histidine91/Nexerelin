package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;

public class PlanetHazardPlugin extends BaseGroundBattlePlugin {
	
	public static float MORALE_PER_25_HAZARD = 0.0125f;
	public static float MIN_MORALE = 0.4f;
	public static float MAX_MORALE = 0.7f;
	
	protected transient String marketCondition;
		
	@Override
	public void afterTurnResolve(int turn) {
		damageUnits(intel.getAllUnits());
		super.afterTurnResolve(turn);
	}
	
	public void damageUnits(List<GroundUnit> units) 
	{
		float moraleDelta = getMoraleImpact();
		if (moraleDelta == 0) return;
		for (GroundUnit unit : units) {
			if (!unit.isDeployed()) continue;
			
			if (moraleDelta < 0 && unit.getLocation().heldByAttacker == unit.isAttacker())
				continue;
			unit.modifyMorale(moraleDelta, MIN_MORALE, MAX_MORALE);
			//Global.getLogger(this.getClass()).info(String.format("Unit %s morale delta from hazard", moraleDelta));
		}
	}
	
	public float getMoraleImpact() {
		float netHazard = intel.getMarket().getHazardValue() - 1;
		float morale = -(netHazard/0.25f) * MORALE_PER_25_HAZARD;
		return morale;
	}
	
	public String pickMarketCondition() {
		String best = null;
		float largestHazard = 0;
		
		for (MarketConditionAPI cond : intel.getMarket().getConditions()) {
			if (cond.getGenSpec() == null) continue;
			Float hazard = cond.getGenSpec().getHazard();
			if (hazard == null || hazard == 0) continue;
			if (hazard < 0) hazard += 0.01f;	// to prefer negative over positive conditions
			float absHaz = Math.abs(hazard);
			if (absHaz > largestHazard) {
				best = cond.getId();
				largestHazard = absHaz;
			}
		}
		
		return best;
	}
	
	public boolean hasTooltip() {
		return intel.getMarket().getHazardValue() != 1;
	}
	
	@Override
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {
		
		if (!hasTooltip()) return;
		if (isAttacker != null) return;
		
		if (marketCondition == null || !intel.getMarket().hasCondition(marketCondition))
		{
			marketCondition = pickMarketCondition();
		}
		String icon = marketCondition != null ? Global.getSettings().getMarketConditionSpec(marketCondition).getIcon()
				: "graphics/icons/industry/local_production2.png";
		
		float hazard = intel.getMarket().getHazardValue();
		
		NexUtilsGUI.CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, GroundBattleIntel.getString("modifierHazard"), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				icon, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				hazard <= 1 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), 
				true, getModifierTooltip());
		
		info.addCustom(gen.panel, pad);
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		float morale = getMoraleImpact();
		Color h = morale >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		String name = intel.getMarket().getName();
		String hazard = StringHelper.toPercent(intel.getMarket().getHazardValue());
		
		String str = GroundBattleIntel.getString("modifierHazardDesc1");
		str = String.format(str, name, hazard);
		LabelAPI label = tooltip.addPara(str, 0);
		label.setHighlight(hazard);
		
		//Global.getLogger(this.getClass()).info(String.format("wtf? %s, %s", MIN_MORALE, MAX_MORALE));
		
		str = GroundBattleIntel.getString(morale >= 0 ? "modifierHazardDescPos" : "modifierHazardDescNeg");
		String moraleStr = StringHelper.toPercent(morale);
		String limitStr = StringHelper.toPercent(morale >= 0 ? MAX_MORALE : MIN_MORALE);
		label = tooltip.addPara(str, 3, h, moraleStr, limitStr);
	}

	@Override
	public float getSortOrder() {
		return -800;
	}
}
