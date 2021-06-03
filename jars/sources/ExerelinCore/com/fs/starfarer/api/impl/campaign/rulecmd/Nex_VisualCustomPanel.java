package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;

public class Nex_VisualCustomPanel extends BaseCommandPlugin {
	
	public static final float PANEL_WIDTH = 600;
	public static final float PANEL_HEIGHT = 480;
	
	protected static CustomPanelAPI panel;
	protected static TooltipMakerAPI tooltip;
	protected static InteractionDialogCustomPanelPlugin plugin;
	
	public static TooltipMakerAPI getTooltip() {
		return tooltip;
	}
	
	public static InteractionDialogCustomPanelPlugin getPlugin() {
		return plugin;
	}
	
	public static CustomPanelAPI getPanel() {
		return panel;
	}
	
    @Override
	public boolean execute(String ruleId, final InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg)
		{
			case "create":
				boolean replace = params.get(1).getBoolean(memoryMap);
				createPanel(dialog, replace);
				return true;
			case "readdTooltip":
				if (tooltip == null) return false;
				readdTooltip(dialog);
				return true;
			case "clear":
				clearPanel(dialog, memoryMap);
				return true;
			case "add":
				addTooltipToPanel();
				return true;
		}
				
		return false;
	}
	
	public static void clearPanel(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		tooltip = null;
		plugin = null;
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		if (data != null) dialog.getVisualPanel().showPersonInfo(data.getPerson(), true);
	}
	
	public static void createPanel(InteractionDialogAPI dialog, boolean replace) {
		if (!replace && tooltip != null)
			return;
		
		VisualPanelAPI vp = dialog.getVisualPanel();
		plugin = new InteractionDialogCustomPanelPlugin();
		panel = vp.showCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, plugin);
		tooltip = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, true);
		
		//tooltip.setForceProcessInput(true);
					
		//panel.addUIElement(tooltip);	// do this later, so the tooltip correctly gets its scrollbar
	}
	
	/**
	 * Creates a new custom panel and readds the existing plugin and tooltip to it.<br/>
	 * This resets the extent of the tooltip's scrollbar, for when new elements are added to the tooltip.
	 * @param dialog
	 */
	public static void readdTooltip(InteractionDialogAPI dialog) {
		VisualPanelAPI vp = dialog.getVisualPanel();
		panel = vp.showCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, plugin);
		addTooltipToPanel();
	}
	
	/**
	 * Call this after all desired elements have been added to the tooltip; otherwise the scrollbar may not cover all elements.
	 */
	public static void addTooltipToPanel() {
		panel.addUIElement(tooltip);
	}
}