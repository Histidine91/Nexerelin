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
			case "clear":
				tooltip = null;
				plugin = null;
				CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
				dialog.getVisualPanel().showPersonInfo(data.getPerson(), true);
				return true;
		}
				
		return false;
	}
	
	public void createPanel(InteractionDialogAPI dialog, boolean replace) {
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
	 * Call this after all desired elements have been added to the tooltip; otherwise the scrollbar may not cover all elements.
	 */
	public static void addTooltipToPanel() {
		panel.addUIElement(tooltip);
	}
}