package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paginated options where multiple rules rows can add to the options before they're displayed.
 */
public class Nex_DynamicPaginatedOptions extends PaginatedOptionsPlus {
	
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	
	protected static List<PaginatedOption> optionsSt = new ArrayList<>();
	protected static List<PaginatedOption> optionsAllPagesSt = new ArrayList<>();
	protected static Map<String, String> optionTooltipsSt = new HashMap<>();
	protected static Map<String, Color> optionColorsSt = new HashMap<>();
	protected static Map<String, List<String>> tooltipHighlightsSt = new HashMap<>();
	protected static Map<String, List<Color>> tooltipHighlightColorsSt = new HashMap<>();
	protected static Map<String, Integer> optionShortcutsSt = new HashMap<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		//Global.getLogger(this.getClass()).info("Processing rule " + ruleId);
		this.dialog = dialog;
		String arg = params.get(0).getString(memoryMap);
		switch (arg)
		{
			case "show":
				//Global.getLogger(this.getClass()).info("Showing options");
				optionsPerPage = 5;
				super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
				showOptions();
				return true;
			case "addOption":
				addOption(ruleId, params, memoryMap);
				return true;
			case "addOptionAllPages":
				addOptionAllPages(ruleId, params, memoryMap);
				return true;
			case "addTooltip":
				addTooltip(ruleId, params, memoryMap);
				return true;
			case "addTooltipHighlights":
				addTooltipHighlights(ruleId, params, memoryMap);
				return true;
			case "addTooltipHighlightColors":
				addTooltipHighlightColors(ruleId, params, memoryMap);
				return true;
			case "addShortcut":
				return addShortcut(ruleId, params, memoryMap);
		}
		
		return false;
	}
	
	public void addOption(String ruleId, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String optId = params.get(1).getString(memoryMap);
		String text = params.get(2).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
		optionsSt.add(new PaginatedOption(text, optId));
	}
	
	public void addOptionAllPages(String ruleId, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String optId = params.get(1).getString(memoryMap);
		String text = params.get(2).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
		optionsAllPagesSt.add(new PaginatedOption(text, optId));
	}
	
	public void addTooltip(String ruleId, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String optId = params.get(1).getString(memoryMap);
		String tooltip = params.get(2).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
		optionTooltipsSt.put(optId, tooltip);
	}
	
	public void addTooltipHighlights(String ruleId, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String optId = params.get(1).getString(memoryMap);
		List<String> strings = new ArrayList<>();
		for (int i = 2; i < params.size(); i++) {
			String string = params.get(i).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
			if (string != null) strings.add(string);
		}
		tooltipHighlightsSt.put(optId, strings);
	}
	
	public void addTooltipHighlightColors(String ruleId, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String optId = params.get(1).getString(memoryMap);
		List<Color> colors = new ArrayList<>();
		for (int i = 2; i < params.size(); i++) {
			Color color = params.get(i).getColor(memoryMap);
			colors.add(color);
		}
		tooltipHighlightColorsSt.put(optId, colors);
	}
	
	public boolean addShortcut(String ruleId, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String optId = params.get(1).getString(memoryMap);
		String keyName = params.get(2).getString(memoryMap);
		int code = Global.getSettings().getCodeFor(keyName);
		//Global.getLogger(this.getClass()).info(String.format("wololo %s, %s, %s", optId, keyName, code));
		if (code == -1) return false;
		
		optionShortcutsSt.put(optId, code);
		return true;
	}
	
	@Override
	public void showOptions() {
		options.addAll(optionsSt);
		optionsAllPages.addAll(optionsAllPagesSt);
		optionTooltips.putAll(optionTooltipsSt);
		optionColors.putAll(optionColorsSt);
		tooltipHighlights.putAll(tooltipHighlightsSt);
		tooltipHighlightColors.putAll(tooltipHighlightColorsSt);
		optionShortcuts.putAll(optionShortcutsSt);
		
		super.showOptions();
		// clear static values we no longer need
		optionsSt.clear();
		optionsAllPagesSt.clear();
		optionTooltipsSt.clear();
		optionColorsSt.clear();
		tooltipHighlightsSt.clear();
		tooltipHighlightColorsSt.clear();
		optionShortcutsSt.clear();
	}

}