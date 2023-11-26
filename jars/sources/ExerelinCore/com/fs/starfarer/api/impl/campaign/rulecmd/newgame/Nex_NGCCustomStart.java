package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.customstart.CustomStartDefs;
import exerelin.campaign.customstart.CustomStartDefs.CustomStartDef;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;


public class Nex_NGCCustomStart extends PaginatedOptions {
	
	public static final String CUSTOM_START_OPTION_PREFIX = "nex_NGCCustomStart_";
	public static final int PREFIX_LENGTH = CUSTOM_START_OPTION_PREFIX.length();
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	
	public static String tooltipDisabledRandom = StringHelper.getString("exerelin_ngc", "customStartOrScenario_tooltipDisabledInRandom");
	public static String tooltipDisabledNonRandom = StringHelper.getString("exerelin_ngc", "customStartOrScenario_tooltipDisabledInNonRandom");
	public static String tooltipLoadFailure = StringHelper.getString("exerelin_ngc", "customStartOrScenario_tooltipFailedToLoad");
	
	protected Map<String, String> tooltips = new HashMap<>();
	protected Map<String, List<String>> highlights = new HashMap<>();
	protected Map<String, List<Color>> highlightColors = new HashMap<>();
	protected Set<String> disabled = new HashSet<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg)
		{
			case "menu":
				optionsPerPage = 5;
				super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
				generateMenu(ruleId);
				return true;
			case "select":
				selectCustomStart(dialog, memoryMap);
				return true;
			case "unset":
				unsetCustomStart(memoryMap);
				return true;
		}
		
		return false;
	}
	
	public static void selectCustomStart(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
		String startId = option.substring(PREFIX_LENGTH);
		selectCustomStart(dialog, memoryMap, startId);
	}
	
	public static void selectCustomStart(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, String startId) {
		dialog.getOptionPanel().clearOptions();
		memoryMap.get(MemKeys.LOCAL).set("$nex_customStart", startId);
		ExerelinSetupData.getInstance().startFleetType = null;
		CustomStartDefs.loadCustomStart(startId, dialog, memoryMap);
		// no need to decide whether to go to starting level/resources dialog or straight to completion; let the custom start code handle it
	}
	
	public static void unsetCustomStart(Map<String, MemoryAPI> memoryMap) {
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		
		data.getScripts().clear();
		data.getScriptsBeforeTimePass().clear();
		data.setWithTimePass(true);
		
		memoryMap.get(MemKeys.LOCAL).unset("$nex_customStart");
	}
	
	@Override
	public void showOptions()
	{
		super.showOptions();
		
		// add the tooltips
		for (Map.Entry<String, String> tmp : tooltips.entrySet())
		{
			String opt = tmp.getKey();
			dialog.getOptionPanel().setTooltip(opt, tmp.getValue());
			dialog.getOptionPanel().setTooltipHighlights(opt, 
					highlights.get(opt).toArray(new String[0]));
			dialog.getOptionPanel().setTooltipHighlightColors(opt, 
					highlightColors.get(opt).toArray(new Color[0]));
		}
		for (String option : disabled)
		{
			dialog.getOptionPanel().setEnabled(option, false);
		}
		
		dialog.getOptionPanel().setShortcut("nex_NGCFactionsBack", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		super.optionSelected(optionText, optionData);
	}
	
	protected void generateMenu(String ruleId) {
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "nex_NGCFactionsBack");
		addCustomStartOptions();
		
		showOptions();
	}
	
	protected void addCustomStartOptions()
	{
		options.clear();
		tooltips.clear();
		
		boolean corvus = ExerelinSetupData.getInstance().corvusMode;
		
		for (CustomStartDef def : CustomStartDefs.getStartDefs())
		{
			if (def.requiredModId != null && !Global.getSettings().getModManager().isModEnabled(def.requiredModId))
				continue;
			
			String option = CUSTOM_START_OPTION_PREFIX + def.id;
			
			// check if this custom start is compatible with our current random sector setting
			boolean disabledRandom = false, disabledNonRandom = false;
			if (def.randomSector == 1 && corvus) {
				disabledNonRandom = true;
				disabled.add(option);
			}
			else if (def.randomSector == -1 && !corvus) {
				disabledRandom = true;
				disabled.add(option);
			}
			
			boolean valid = CustomStartDefs.validateCustomStart(def.id);
			if (!valid) {
				disabled.add(option);
			}
			
			StringBuilder tb = new StringBuilder();
			if (!valid) {
				tb.append(tooltipLoadFailure);
				tb.append("\n\n");
			}
			else if (disabledRandom) {
				tb.append(tooltipDisabledRandom);
				tb.append("\n\n");
			}
			else if (disabledNonRandom) {
				tb.append(tooltipDisabledNonRandom);
				tb.append("\n\n");
			}
			FactionAPI faction = Global.getSettings().createBaseFaction(def.factionId);
			String factionName = Misc.ucFirst(faction.getDisplayName());
			tb.append(StringHelper.getString("exerelin_ngc", "customStartTooltipFaction", true) + ": " + factionName);
			tb.append("\n\n");
			String difficulty = Misc.ucFirst(def.difficulty);
			tb.append(StringHelper.getString("exerelin_ngc", "customStartTooltipDifficulty", true) + ": " + difficulty);
			tb.append("\n\n");
			tb.append(def.desc);
			
			List<String> hl = new ArrayList<>(Arrays.asList(factionName, difficulty));
			List<Color> hlColors = new ArrayList<>(Arrays.asList(faction.getBaseUIColor(), 
					CustomStartDefs.getDifficultyColor(difficulty)));
			// add disabled tooltip if needed
			if (!valid) {
				hl.add(0, tooltipLoadFailure);
				hlColors.add(0, Misc.getNegativeHighlightColor());
			} else if (disabledRandom) {
				hl.add(0, tooltipDisabledRandom);
				hlColors.add(0, Misc.getNegativeHighlightColor());
			} else if (disabledNonRandom) {
				hl.add(0, tooltipDisabledNonRandom);
				hlColors.add(0, Misc.getNegativeHighlightColor());
			}
			
			addOption(def.name, option);
			tooltips.put(option, tb.toString());
			highlights.put(option, hl);
			highlightColors.put(option, hlColors);
		}
	}
}