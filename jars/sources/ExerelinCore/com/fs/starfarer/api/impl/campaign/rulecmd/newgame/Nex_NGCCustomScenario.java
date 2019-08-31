package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.StringHelper;
import exerelin.world.scenarios.ScenarioManager;
import exerelin.world.scenarios.ScenarioManager.StartScenarioDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.input.Keyboard;


public class Nex_NGCCustomScenario extends PaginatedOptions {
	
	public static final String CUSTOM_SCENARIO_OPTION_PREFIX = "nex_NGCCustomScenario_";
	public static final int PREFIX_LENGTH = CUSTOM_SCENARIO_OPTION_PREFIX.length();
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	
	protected Map<String, String> tooltips = new HashMap<>();
	protected Set<String> disabled = new HashSet<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg)
		{
			case "menuOption":
				//addOpenMenuOption(dialog.getOptionPanel());
				setMenuOptionText(memoryMap.get(MemKeys.LOCAL));
				return true;
			case "menu":
				optionsPerPage = 5;
				super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
				generateMenu(ruleId);
				return true;
			case "select":
				selectScenario(memoryMap.get(MemKeys.LOCAL));
				return true;
		}
		
		return false;
	}
	
	//public void addOpenMenuOption(OptionPanelAPI opts) {
	public static void setMenuOptionText(MemoryAPI mem) {
		String currentScenario = ExerelinSetupData.getInstance().startScenario;
		String scenarioName = StringHelper.getString("none");
		if (currentScenario != null && !currentScenario.isEmpty()) {
			scenarioName = ScenarioManager.getScenarioDef(currentScenario).name;
		}
		mem.set("$nex_customScenarioName", scenarioName);
	}
	
	public static void selectScenario(MemoryAPI mem) {
		String option = mem.getString("$option");
		String scenarioId = option.substring(PREFIX_LENGTH);
		selectScenario(mem, scenarioId);
	}
	
	public static void selectScenario(MemoryAPI mem, String scenarioId) {		
		if (scenarioId == null || scenarioId.isEmpty() || scenarioId.equals("none")) {
			ExerelinSetupData.getInstance().startScenario = null;
		} else {
			ExerelinSetupData.getInstance().startScenario = scenarioId;
		}
		setMenuOptionText(mem);
	}
	
	@Override
	public void showOptions()
	{
		super.showOptions();
		
		// add the tooltips
		for (Map.Entry<String, String> tmp : tooltips.entrySet())
		{	
			dialog.getOptionPanel().setTooltip(tmp.getKey(), tmp.getValue());
		}
		for (String option : disabled)
		{
			dialog.getOptionPanel().setEnabled(option, false);
			dialog.getOptionPanel().setTooltipHighlightColors(option, Misc.getNegativeHighlightColor());
			dialog.getOptionPanel().setTooltipHighlights(option, Nex_NGCCustomStart.tooltipDisabledNonRandom);
		}
		
		dialog.getOptionPanel().setShortcut("exerelinNGCOtherOptions", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		super.optionSelected(optionText, optionData);
	}
	
	protected void generateMenu(String ruleId) {
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "exerelinNGCOtherOptions");
		addScenarioOptions();
		
		showOptions();
	}
	
	protected void addScenarioOptions()
	{
		options.clear();
		tooltips.clear();
		
		boolean corvus = ExerelinSetupData.getInstance().corvusMode;
		
		addOption(StringHelper.getString("none", true), CUSTOM_SCENARIO_OPTION_PREFIX + "none");
		
		for (StartScenarioDef def : ScenarioManager.getScenarioDefs())
		{
			if (def.requiredModId != null && !Global.getSettings().getModManager().isModEnabled(def.requiredModId))
				continue;
			
			String option = CUSTOM_SCENARIO_OPTION_PREFIX + def.id;
			String tooltip = def.desc;
			
			if (corvus && def.randomSectorOnly) {
				disabled.add(option);
				tooltip = Nex_NGCCustomStart.tooltipDisabledNonRandom	+ "\n\n" + tooltip;
			}
			
			addOption(def.name, option);
			tooltips.put(option, tooltip);
		}
	}
}