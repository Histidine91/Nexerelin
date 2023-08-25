package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FleetSupportPlugin extends BaseGroundBattlePlugin {
	
	protected float atkBonus = 0;
	protected float defBonus = 0;
	protected transient Set<GroundUnit> atkUnits;	// created and filled before round resolve
	protected transient Set<GroundUnit> defUnits;	// created and filled before round resolve
	protected transient float atkStrSum = 0;		// incremented prior to round resolve
	protected transient float defStrSum = 0;		// incremented prior to round resolve
	
	// recomputed on unit move
	protected transient Float atkStrSumEst;
	protected transient Float defStrSumEst;
	protected transient Float atkBonusEst;
	protected transient Float defBonusEst;
	
	protected float getBonusFromFleets(List<CampaignFleetAPI> fleets) {
		float increment = 0;
		for (CampaignFleetAPI fleet : fleets) {
			 increment += Misc.getFleetwideTotalMod(fleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		}
		if (increment < 0) return 0;
		return increment;
	}
	
	@Override
	public void init(GroundBattleIntel intel) {
		super.init(intel);
		if (intel.getTurnNum() == 1) {
			String desc = GroundBattleIntel.getString("modifierMovementPointsFleetSupport");
			List<CampaignFleetAPI> atkFleets = intel.getSupportingFleets(true);
			float initDeployBonus = getBonusFromFleets(atkFleets) * GBConstants.FLEET_SUPPORT_MOVEMENT_MULT;
			intel.getSide(true).getMovementPointsPerTurn().modifyFlat("fleetSupport",
				initDeployBonus, desc);
		}
	}
		
	@Override
	public void advance(float days) {
		List<CampaignFleetAPI> atkFleets = intel.getSupportingFleets(true);
		List<CampaignFleetAPI> defFleets = intel.getSupportingFleets(false);
		
		float atkBonusIncrement = getBonusFromFleets(atkFleets);
		atkBonus += atkBonusIncrement * days;
		
		float defBonusIncrement = getBonusFromFleets(defFleets);
		defBonus += defBonusIncrement * days;
		
		Global.getSector().addPing(intel.getMarket().getPrimaryEntity(), "nex_invasion_support_range");
	}
	
	protected void loadEligibleUnits(Set<GroundUnit> collection, boolean attacker,
			Collection<IndustryForBattle> contestedLocations) 
	{
		for (GroundUnit unit : intel.getSide(attacker).getUnits()) {
			if (!unit.isDeployed()) continue;
			if (unit.isAttackPrevented()) continue;
			if (!contestedLocations.contains(unit.getLocation())) continue;
			
			collection.add(unit);
			if (attacker) atkStrSum += unit.getBaseStrength();
			else defStrSum += unit.getBaseStrength();
		}
	}
	
	protected void recomputeEstimates()
	{
		atkStrSumEst = 0f;
		defStrSumEst = 0f;
		
		if (atkBonusEst == null)
			atkBonusEst = getBonusFromFleets(intel.getSupportingFleets(true));
		if (defBonusEst == null)
			defBonusEst = getBonusFromFleets(intel.getSupportingFleets(false));
		
		Set<IndustryForBattle> contestedLocations = new HashSet<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			if (ifb.isContested()) contestedLocations.add(ifb);
		}
		
		for (GroundUnit unit : intel.getAllUnits()) {
			if (!unit.isDeployed()) continue;
			if (unit.isAttackPrevented()) continue;
			if (!contestedLocations.contains(unit.getLocation())) continue;
			
			if (unit.isAttacker()) atkStrSumEst += unit.getBaseStrength();
			else defStrSumEst += unit.getBaseStrength();
		}
	}
	
	protected float getUnitAttackBonus(GroundUnit unit) {
		if (!intel.isResolving() && Global.getSettings().getBoolean("nex_gbUseFleetSupportEstimate")) {
			return getUnitAttackBonusEst(unit);
		}
		
		if (atkUnits == null || defUnits == null) return 0;
		
		float bonus = 0;
		float shareMult = 1;
		
		if (unit.isAttacker()) {
			if (!atkUnits.contains(unit)) {
				return 0;
			}
			if (atkStrSum == 0) return 0;
			shareMult = unit.getBaseStrength()/atkStrSum;
			bonus = atkBonus * shareMult * 0.5f;
		} else {
			if (!defUnits.contains(unit)) {
				return 0;
			}
			if (defStrSum == 0) return 0;
			shareMult = unit.getBaseStrength()/defStrSum;
			bonus = defBonus * shareMult * 0.5f;
		}
		bonus = Math.min(bonus, unit.getBaseStrength());
		if (bonus != 0) {
			Global.getLogger(this.getClass()).info(String.format(
					"    Unit %s receiving %s bonus damage from ground support (share %s)", 
					unit.getName(), bonus, StringHelper.toPercent(shareMult)));
		}
		return bonus;
	}
	
	protected float getUnitAttackBonusEst(GroundUnit unit) {
		if (atkBonusEst == null || defBonusEst == null) recomputeEstimates();
		
		if (!unit.isDeployed() || unit.isAttackPrevented() || !unit.getLocation().isContested()) {
			return 0;
		}
		
		boolean attacker = unit.isAttacker();
		float divisor = attacker ? atkStrSumEst : defStrSumEst;
		if (divisor == 0) return 0;
		float shareMult = unit.getBaseStrength()/divisor;
		
		return shareMult * (attacker ? atkBonusEst : defBonusEst);
	}
	
	@Override
	public void reportUnitMoved(GroundUnit unit, IndustryForBattle lastLoc) {
		if (!intel.isResolving())
			recomputeEstimates();
	}
	
	@Override
	public void beforeCombatResolve(int turn, int numThisTurn) {
		Set<IndustryForBattle> contestedLocations = new HashSet<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			if (ifb.isContested()) contestedLocations.add(ifb);
		}
		
		atkUnits = new HashSet<>();
		defUnits = new HashSet<>();
		atkStrSum = 0;
		defStrSum = 0;
		
		if (atkBonus > 0) {
			loadEligibleUnits(atkUnits, true, contestedLocations);
		}
		if (defBonus > 0) {
			loadEligibleUnits(defUnits, false, contestedLocations);
		}
	}
	
	@Override
	public MutableStat modifyAttackStat(GroundUnit unit, MutableStat stat) {
		boolean estimate = !intel.isResolving();
		float bonus = getUnitAttackBonus(unit);
		String key = "modifierGroundSupport" + (estimate ? "Estimate" : "");
		if (bonus != 0)
			stat.modifyFlat("groundSupport", bonus,
					StringHelper.getString("nex_invasion2", key));
		return stat;
	}
	
	@Override
	public void afterTurnResolve(int turn) {
		String desc = GroundBattleIntel.getString("modifierMovementPointsFleetSupport");
		intel.getSide(false).getMovementPointsPerTurn().modifyFlat("fleetSupport",
				defBonus * GBConstants.FLEET_SUPPORT_MOVEMENT_MULT, desc);
		intel.getSide(true).getMovementPointsPerTurn().modifyFlat("fleetSupport",
				atkBonus * GBConstants.FLEET_SUPPORT_MOVEMENT_MULT, desc);
		
		atkBonus = 0;
		defBonus = 0;
		atkStrSum = 0;
		defStrSum = 0;
		
		atkStrSumEst = 0f;
		defStrSumEst = 0f;
		atkBonusEst = null;
		defBonusEst = null;
		
		super.afterTurnResolve(turn);
	}
	
	protected boolean hasTooltip(boolean isAttacker) {
		if (isAttacker && atkBonus > 0) return true;
		if (!isAttacker && defBonus > 0) return true;
		
		List<CampaignFleetAPI> fleets = intel.getSupportingFleets(isAttacker);
		float increment = getBonusFromFleets(fleets);
		return increment > 0;
	}
	
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {
		
		if (isAttacker == null) return;
		
		if (!hasTooltip(isAttacker)) return;
		
		String icon = "graphics/hullmods/ground_support.png";
		
		NexUtilsGUI.CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, GroundBattleIntel.getString("modifierGroundSupport"), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				icon, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				Misc.getPositiveHighlightColor(), true, getModifierTooltip(isAttacker));
		
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
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam, boolean isAttacker) 
	{
		Color h = Misc.getHighlightColor();
		
		String str = GroundBattleIntel.getString("modifierGroundSupportDesc1");
		tooltip.addPara(str, 0, h, (int)GBConstants.MAX_SUPPORT_DIST + "");
		
		String currBonus = String.format("%.0f", isAttacker ? atkBonus : defBonus);
		String maxBonus = String.format("%.0f", getBonusFromFleets(intel.getSupportingFleets(isAttacker)));
		str = GroundBattleIntel.getString("modifierGroundSupportDesc2");
		tooltip.addPara(str, 10, h, currBonus, maxBonus);
		
		str = GroundBattleIntel.getString("modifierGroundSupportDesc3");
		tooltip.addPara(str, 10);
	}

	@Override
	public float getSortOrder() {
		return -700;
	}
}
