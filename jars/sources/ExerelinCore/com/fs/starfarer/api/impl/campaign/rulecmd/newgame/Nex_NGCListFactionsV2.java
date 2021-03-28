package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_NEXT_PAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_PREV_PAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.newgame.Nex_NGCListFactions.EMPTY_PARAMS;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

// it's bad 
// selecting a main faction, then going back, doesn't return you to previous faction page 
// due to use of nested pages
@Deprecated
public class Nex_NGCListFactionsV2 extends PaginatedOptions {
	
	public static final String JOIN_FACTION_OPTION_PREFIX = "nex_NGCJoinFaction_";
	public static List<Pair<String, String>> factionOptions = new ArrayList<>();
	public static Map<String, String> optionTooltips = new HashMap<>();
	public static Map<String, Color> optionColors = new HashMap<>();
	public static String lastArg = "menu";
	
	public static boolean loaded = false;
	
	public static void loadOptions() {
		if (loaded) return;
		
		List<String> factionIds = NexConfig.getFactions(false, true);
		factionIds.remove(Factions.PLAYER);
		
		List<FactionAPI> factions = new ArrayList<>();
		for (String factionId : factionIds)
		{
			FactionAPI faction = Global.getSettings().createBaseFaction(factionId);
			if (faction != null)
				factions.add(faction);
		}
		
		// order by name
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR);
		
		for (FactionAPI faction : factions) {
			String factionId = faction.getId();
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			String optId = JOIN_FACTION_OPTION_PREFIX + factionId;
			String text = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
			if (!conf.difficultyString.isEmpty())
			{
				text = text + " (" + conf.difficultyString + ")";
			}
			Pair<String, String> option = new Pair<>(optId, text);
			factionOptions.add(option);
			optionColors.put(optId, faction.getBaseUIColor());
			
			if (conf.ngcTooltip != null)
			{
				optionTooltips.put(optId, conf.ngcTooltip);
			}
		}
		
		loaded = true;
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		if (arg.equals("back"))
			arg = lastArg;
		
		switch (arg)
		{
			case "menu":
				generateInitialMenu(dialog);
				lastArg = arg;
				return true;
				
			case "listFactions":
				//optionsPerPage = 6;
				super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
				showFactionsMenu();
				lastArg = arg;
				return true;
		}
		
		return false;
	}
	
	protected void showFactionsMenu() {
		loadOptions();
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		for (Pair<String, String> opt : factionOptions) {
			addOption(opt.two, opt.one);
		}
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "nex_NGCFactionsBack");
		showOptions();
		for (Pair<String, String> opt : factionOptions) {
			String optId = opt.one;
			opts.setTooltip(optId, optionTooltips.get(optId));
		}
		
		opts.setShortcut("nex_NGCFactionsBack", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	protected void generateInitialMenu(InteractionDialogAPI dialog)
	{
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();

		opts.addOption(StringHelper.getString("exerelin_ngc", "mainFactions", true), "nex_NGCFactionMenu");
		opts.addOption(StringHelper.getString("exerelin_ngc", "ownFaction", true), "nex_NGCJoinOwnFaction");
		if (ExerelinSetupData.getInstance().corvusMode)
		{
			opts.setTooltip("nex_NGCJoinOwnFaction", StringHelper.getString("exerelin_ngc", "ownFactionDisabledTooltip"));
			opts.setEnabled("nex_NGCJoinOwnFaction", false);
		}
		opts.addOption(StringHelper.getString("exerelin_ngc", "freeStartHard", true), "nex_NGCFreeStart");
		opts.setTooltip("nex_NGCFreeStart", StringHelper.getString("exerelin_ngc", "freeStartTooltip"));
		
		opts.addOption(StringHelper.getString("exerelin_ngc", "randomFaction", true), "nex_NGCJoinRandomFaction");
		
		opts.addOption(StringHelper.getString("exerelin_ngc", "customStart", true), "nex_NGCCustomStart");

		if (Global.getSettings().isDevMode())
		{
			opts.addOption(StringHelper.getString("exerelin_ngc", "devStart"), "nex_NGCDevStart");
			opts.addOption(StringHelper.getString("exerelin_ngc", "devStartFast"), "nex_NGCDevStartFast");
			NexUtils.addDevModeDialogOptions(dialog);
		}

		opts.addOption(StringHelper.getString("back", true), "exerelinNGCOptionsBack");
		opts.setShortcut("exerelinNGCOptionsBack", Keyboard.KEY_ESCAPE, false, false, false, false);
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
				else
					dialog.getOptionPanel().addOption(option.text, option.id);
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