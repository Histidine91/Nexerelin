package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_NEXT_PAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_PREV_PAGE;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class PaginatedOptionsPlus extends PaginatedOptions {
	
	protected Map<String, String> optionTooltips = new HashMap<>();
	protected Map<String, Color> optionColors = new HashMap<>();
	
	public void addColor(String id, Color color) {  
		optionColors.put(id, color);
	}
	
	public void addTooltip(String id, String tooltip) {  
		optionTooltips.put(id, tooltip);
	}
	
	// Same as superclass, excepts colors dialog options and adds tooltips as appropriate
	@Override
	public void showOptions() {  
		dialog.getOptionPanel().clearOptions();  

		int maxPages = (int) Math.ceil((float)options.size() / (float)optionsPerPage);  
		if (currPage > maxPages - 1) currPage = maxPages - 1;  
		if (currPage < 0) currPage = 0;  

		int start = currPage * optionsPerPage;  
		for (int i = start; i < start + optionsPerPage; i++) {  
			if (i >= options.size()) {  
				if (maxPages > 1 && withSpacers) {
					dialog.getOptionPanel().addOption("", "spacer" + i);  
					dialog.getOptionPanel().setEnabled("spacer" + i, false);  
				}  
			} else {  
				PaginatedOption option = options.get(i);
				String tooltip = optionTooltips.containsKey(option.id) ? optionTooltips.get(option.id) : null; 
				
				if (optionColors.containsKey(option.id))
					dialog.getOptionPanel().addOption(option.text, option.id, optionColors.get(option.id), tooltip);  
				else {
					dialog.getOptionPanel().addOption(option.text, option.id);
					dialog.getOptionPanel().setTooltip(option.id, tooltip);
				}
					
			}  
		}

		if (maxPages > 1) {  
			dialog.getOptionPanel().addOption(getPreviousPageText(), OPTION_PREV_PAGE);  
			dialog.getOptionPanel().addOption(getNextPageText(), OPTION_NEXT_PAGE);  

			if (currPage >= maxPages - 1) {  
				dialog.getOptionPanel().setEnabled(OPTION_NEXT_PAGE, false);  
			}  
			if (currPage <= 0) {  
				dialog.getOptionPanel().setEnabled(OPTION_PREV_PAGE, false);  
			}  
		}

		for (PaginatedOption option : optionsAllPages) {  
			dialog.getOptionPanel().addOption(option.text, option.id);
		}

		if (Global.getSettings().isDevMode()) {  
			DevMenuOptions.addOptions(dialog);  
		}  
	}  
}
