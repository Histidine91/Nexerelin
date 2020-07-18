package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.StringHelper;
import java.awt.Color;


public class Nex_NGCProcessSectorGenerationSliders extends BaseCommandPlugin {
	
	public static final float BAR_WIDTH = 256;
	
	protected String getString(String id)
	{
		return StringHelper.getString("exerelin_ngc", id);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg) {
			case "create":
				createSliders(dialog.getOptionPanel());
				break;
			case "save":
				saveValues(dialog.getOptionPanel());
				break;
			case "reset":
				resetValues();
				break;
			default:
				return false;
		}
		return true;
	}
	
	protected void createSliders(OptionPanelAPI opts)
	{
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		opts.addSelector(getString("populatedSystemsTitle"), "systemCountSelector", Color.YELLOW, BAR_WIDTH, 48, 4, 48, ValueDisplayMode.VALUE, 
				getString("populatedSystemsTooltip"));
		opts.setSelectorValue("systemCountSelector", data.numSystems);
		
		opts.addSelector(getString("populatedPlanetsTitle"), "planetCountSelector", Color.GREEN, BAR_WIDTH, 48, 8, 80, ValueDisplayMode.VALUE, 
				getString("populatedPlanetsTooltip"));
		opts.setSelectorValue("planetCountSelector", data.numPlanets);
		
		opts.addSelector(getString("stationsTitle"), "stationCountSelector", Color.GRAY, BAR_WIDTH, 48, 4, 80, ValueDisplayMode.VALUE, 
				getString("stationsTooltip"));
		opts.setSelectorValue("stationCountSelector", data.numStations);
		
		opts.addSelector(getString("maxPlanetsTitle"), "planetMaxSelector", Color.CYAN, BAR_WIDTH, 48, 1, 5, ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("planetMaxSelector", data.maxPlanetsPerSystem);
		
		opts.addSelector(getString("maxMarketsTitle"), "marketMaxSelector", Color.orange, BAR_WIDTH, 48, 1, 8, ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("marketMaxSelector", data.maxMarketsPerSystem);
		
		opts.addOption(StringHelper.getString("reset", true), "exerelinNGCSectorOptionsReset");
	}
	
	protected void saveValues(OptionPanelAPI opts)
	{
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		if (data.corvusMode) return;
		
		data.numSystems = Math.round(opts.getSelectorValue("systemCountSelector"));
		data.numPlanets = Math.round(opts.getSelectorValue("planetCountSelector"));
		data.numStations = Math.round(opts.getSelectorValue("stationCountSelector"));
		data.maxPlanetsPerSystem = Math.round(opts.getSelectorValue("planetMaxSelector"));
		data.maxMarketsPerSystem = Math.round(opts.getSelectorValue("marketMaxSelector"));
	}
	
	protected void resetValues() {
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		ExerelinSetupData defaultData = new ExerelinSetupData();
		
		data.numSystems = defaultData.numSystems;
		data.numPlanets = defaultData.numPlanets;
		data.numStations = defaultData.numStations;
		data.maxPlanetsPerSystem = defaultData.maxPlanetsPerSystem;
		data.maxMarketsPerSystem = defaultData.maxMarketsPerSystem;
	}
}