package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.NexUtilsGUI;

public class GeneralPlugin extends BaseGroundBattlePlugin {
	
	@Override
	public void apply() {
		int size = intel.getMarket().getSize();
		int baseMovePoints = getBaseMovementPointsPerTurn(size);
		String desc = GroundBattleIntel.getString("modifierMovementPointsBase");
		desc = String.format(desc, size);
		intel.getSide(false).getMovementPointsPerTurn().modifyFlat("base", 
				baseMovePoints, desc);
		intel.getSide(true).getMovementPointsPerTurn().modifyFlat("base", 
				baseMovePoints, desc);
		
		if (intel.getTurnNum() == 1) {
			intel.getSide(true).getMovementPointsPerTurn().modifyMult("turn1", 
				GBConstants.TURN_1_MOVE_POINT_MULT, GroundBattleIntel.getString("modifierMovementPointsTurn1"));
		}
		else {
			intel.getSide(true).getMovementPointsPerTurn().unmodify("turn1");
		}
	}

	@Override
	public void unapply() {
		intel.getSide(false).getMovementPointsPerTurn().unmodify("base");
		intel.getSide(true).getMovementPointsPerTurn().unmodify("base");
	}
	
	public static int getBaseMovementPointsPerTurn(int marketSize) {
		int points = Math.round(GBConstants.BASE_MOVEMENT_POINTS_PER_TURN * (marketSize + 1)*(marketSize/2f));
		if (points < 50) points = 50;
		return points;
	}
	
	// Graphical only: Station modifier
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {
		
		if (!Boolean.FALSE.equals(isAttacker)) return;
		
		if (!intel.hasStationFleet()) return;
				
		String icon = "graphics/icons/markets/orbital_station.png";
		
		NexUtilsGUI.CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, GroundBattleIntel.getString("modifierStation"), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				icon, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				Misc.getPositiveHighlightColor(), true, getModifierTooltip());
		
		info.addCustom(gen.panel, pad);
	}
	
	@Override
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		tooltip.addPara(GroundBattleIntel.getString("modifierStationDesc"), 0,
				Misc.getHighlightColor(), GroundBattleIntel.getString("modifierStationDescHighlight"));
	}

	/*
	@Override
	public void onBattleStart() {

		Global.getLogger(this.getClass()).info("Battle started");
		for (GroundUnit unit : intel.getAllUnits()) {
			Global.getLogger(this.getClass()).info("  Found " + (unit.isAttacker() ? "attacker" : "defender") + " unit " + unit.toString() + ": from fleet " + unit.getFleet());
		}
	}

	@Override
	public void onPlayerJoinBattle() {
		//Global.getLogger(this.getClass()).info("Player joined battle, turn " + intel.getTurnNum());
	}
	*/

	@Override
	public float getSortOrder() {
		return -1000;
	}
}
