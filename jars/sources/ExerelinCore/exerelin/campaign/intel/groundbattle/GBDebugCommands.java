package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.VIEW_BUTTON_HEIGHT;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class GBDebugCommands {
		
	public static final Object BUTTON_RESOLVE = new Object();
	public static final Object BUTTON_DEBUG_AI = new Object();
	public static final Object BUTTON_SHOW_ALL_UNITS = new Object();
	
	/**
	 * Run the AI for the specified side, or both if side is not specified.
	 * @param gb
	 * @param ui
	 * @param isAttacker
	 */
	public static void debugAI(GroundBattleIntel gb, IntelUIAPI ui, Boolean isAttacker) 
	{
		if (isAttacker == null) {
			gb.runAI(true, false);
			gb.runAI(false, false);
		}
		else {
			gb.runAI(isAttacker, false);
		}
		ui.updateUIForItem(gb);
	}
	
	public static void destroyUnits(GroundBattleIntel gb, boolean isAttacker) {
		// new list, to avoid concurrent modification exception when the units get wiped
		List<GroundUnit> units = new ArrayList<>(gb.getSide(isAttacker).getUnits());
		for (GroundUnit unit : units) {
			if (!unit.isDeployed()) continue;
			unit.inflictAttrition(1000000, null, null);
		}
	}
	
	public static void addDebugButtons(GroundBattleIntel intel, CustomPanelAPI outer, 
			TooltipMakerAPI info, float width, float buttonWidth) 
	{
		FactionAPI fc = intel.getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor(), bright = fc.getBrightUIColor();
		
		CustomPanelAPI buttonDebugRow = outer.createCustomPanel(width, 24, null);
			
		TooltipMakerAPI btnHolder1 = buttonDebugRow.createUIElement(buttonWidth, 
			VIEW_BUTTON_HEIGHT, false);
		btnHolder1.addButton(getString("btnResolveRound"), GBDebugCommands.BUTTON_RESOLVE, base, bg, buttonWidth, 24, 0);
		buttonDebugRow.addUIElement(btnHolder1).inTL(0, 0);

		TooltipMakerAPI btnHolder2 = buttonDebugRow.createUIElement(buttonWidth, 
			VIEW_BUTTON_HEIGHT, false);
		btnHolder2.addButton(getString("btnAIDebug"), GBDebugCommands.BUTTON_DEBUG_AI, base, bg, buttonWidth, 24, 0);
		buttonDebugRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);

		TooltipMakerAPI btnHolder3 = buttonDebugRow.createUIElement(buttonWidth, 
			VIEW_BUTTON_HEIGHT, false);
		ButtonAPI check = btnHolder3.addAreaCheckbox(getString("btnShowAllUnits"), GBDebugCommands.BUTTON_SHOW_ALL_UNITS, 
				base, bg, bright, buttonWidth, VIEW_BUTTON_HEIGHT, 0);
		check.setChecked(GroundBattleIntel.showAllUnits);
		buttonDebugRow.addUIElement(btnHolder3).rightOfTop(btnHolder2, 4);

		TooltipMakerAPI btnHolder4 = buttonDebugRow.createUIElement(buttonWidth, 
			VIEW_BUTTON_HEIGHT, false);
		btnHolder4.addButton(getString("btnWipeAttackers"), intel.getSide(true), base, bg, buttonWidth, 24, 0);
		buttonDebugRow.addUIElement(btnHolder4).rightOfTop(btnHolder3, 4);

		TooltipMakerAPI btnHolder5 = buttonDebugRow.createUIElement(buttonWidth, 
			VIEW_BUTTON_HEIGHT, false);
		btnHolder5.addButton(getString("btnWipeDefenders"), intel.getSide(false), base, bg, buttonWidth, 24, 0);
		buttonDebugRow.addUIElement(btnHolder5).rightOfTop(btnHolder4, 4);

		info.addCustom(buttonDebugRow, 3);
	}
	
	/**
	 * Handle pressing of debug buttons in the ground battle intel.
	 * @param intel
	 * @param ui
	 * @param buttonId
	 * @return True if any action was taken.
	 */
	public static boolean processDebugButtons(GroundBattleIntel intel, IntelUIAPI ui, Object buttonId) 
	{
		if (buttonId == BUTTON_RESOLVE) {
			intel.advanceTurn(true);
			ui.updateUIForItem(intel);
			return true;
		}		
		if (buttonId == BUTTON_DEBUG_AI) {
			debugAI(intel, ui, null);
			return true;
		}
		if (buttonId == BUTTON_SHOW_ALL_UNITS) {
			GroundBattleIntel.showAllUnits = !GroundBattleIntel.showAllUnits;
			ui.updateUIForItem(intel);
			return true;
		}
		if (buttonId instanceof GroundBattleSide) {
			GroundBattleSide side = (GroundBattleSide)buttonId;
			destroyUnits(intel, side.isAttacker);
			ui.updateUIForItem(intel);
			return true;
		}
		
		return false;
	}
}
