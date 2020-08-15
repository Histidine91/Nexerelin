package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_NEXT_PAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_PREV_PAGE;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaginatedOptionsPlus extends PaginatedOptions {
	
	protected Map<String, String> optionTooltips = new HashMap<>();
	protected Map<String, Color> optionColors = new HashMap<>();
	protected Map<String, List<String>> tooltipHighlights = new HashMap<>();
	protected Map<String, List<Color>> tooltipHighlightColors = new HashMap<>();
	protected Map<String, Integer> optionShortcuts = new HashMap<>();
	
	public void addColor(String id, Color color) {  
		optionColors.put(id, color);
	}
	
	public void addTooltip(String id, String tooltip) {  
		optionTooltips.put(id, tooltip);
	}
	
	public void addHighlights(String id, List<String> highlights) {
		tooltipHighlights.put(id, highlights);
	}
	
	public void addHighlightColors(String id, List<Color> colors) {
		tooltipHighlightColors.put(id, colors);
	}
	
	public void addShortcut(String id, int code) {
		optionShortcuts.put(id, code);
	}
	
	protected void processOption(PaginatedOption option) {
		OptionPanelAPI opts = dialog.getOptionPanel();
		String optId = option.id;
		String tooltip = optionTooltips.containsKey(optId) ? optionTooltips.get(optId) : null; 
		
		if (optionColors.containsKey(optId))
			opts.addOption(option.text, optId, optionColors.get(optId), tooltip);  
		else {
			opts.addOption(option.text, optId);
			opts.setTooltip(optId, tooltip);
		}
		if (tooltipHighlights.containsKey(optId)) {
			opts.setTooltipHighlights(optId, tooltipHighlights.get(optId).toArray(new String[0]));
		}
		if (tooltipHighlightColors.containsKey(optId)) {
			opts.setTooltipHighlightColors(optId, tooltipHighlightColors.get(optId).toArray(new Color[0]));
		}
		
		if (optionShortcuts.containsKey(optId)) {
			opts.setShortcut(optId, optionShortcuts.get(optId), false, false, false, false);
		}
	}
	
	@Override
	public void showOptions() {
		OptionPanelAPI opts = dialog.getOptionPanel();
		
		opts.clearOptions();  

		int maxPages = (int) Math.ceil((float)options.size() / (float)optionsPerPage);
		boolean singlePageMode = false;
		// Use the space for the back/forward buttons to fit more options instead, if we can
		if (options.size() + optionsAllPages.size() <= optionsPerPage + 2) {
			maxPages = 1;
			singlePageMode = true;
		}
		
		if (currPage > maxPages - 1) currPage = maxPages - 1;  
		if (currPage < 0) currPage = 0;

		int start = currPage * optionsPerPage;
		int end = start + optionsPerPage;
		if (singlePageMode) end = optionsPerPage + 2;
		
		for (int i = start; i < end; i++) {  
			if (i >= options.size()) {  
				if (maxPages > 1 && withSpacers) {
					opts.addOption("", "spacer" + i);  
					opts.setEnabled("spacer" + i, false);  
				}  
			} else {
				processOption(options.get(i));
			} 
		}

		if (maxPages > 1) {  
			opts.addOption(getPreviousPageText(), OPTION_PREV_PAGE);  
			opts.addOption(getNextPageText(), OPTION_NEXT_PAGE);  

			if (currPage >= maxPages - 1) {  
				opts.setEnabled(OPTION_NEXT_PAGE, false);  
			}  
			if (currPage <= 0) {  
				opts.setEnabled(OPTION_PREV_PAGE, false);  
			}  
		}

		for (PaginatedOption option : optionsAllPages) {  
			processOption(option);
		}

		if (Global.getSettings().isDevMode()) {  
			DevMenuOptions.addOptions(dialog);  
		}  
	}  
}
