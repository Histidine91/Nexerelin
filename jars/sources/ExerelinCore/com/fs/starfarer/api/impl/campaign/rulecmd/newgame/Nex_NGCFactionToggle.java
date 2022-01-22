package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptionsPlus;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.lwjgl.input.Keyboard;

@Deprecated
public class Nex_NGCFactionToggle extends PaginatedOptionsPlus {
	
	public static final String TOGGLE_FACTION_OPTION_PREFIX = "nex_NGCToggleFaction_";
	public static final int OPT_LENGTH = TOGGLE_FACTION_OPTION_PREFIX.length();
	public static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	public static final String RANDOMIZE_OPT = "nex_NGCRandomStartFactions";
	protected static int lastPage = 0;
	protected static List<String> spawnableFactionIds = null;
	protected static Random random = new Random();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg)
		{				
			case "listFactions":
				lastPage = 0;
				init(ruleId, dialog, memoryMap);
				return true;
				
			case "toggle":
				ExerelinSetupData data = ExerelinSetupData.getInstance();
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				//if (option == null) throw new IllegalStateException("No $option set");
				String factionId = option.substring(OPT_LENGTH);
				boolean state = !isFactionEnabled(factionId, data);
				data.factions.put(factionId, state);
				
				// reload options
				init(ruleId, dialog, memoryMap);
				
				return true;
			
			case "processRecommendedSize":
				handleRecommendedSectorSize(dialog.getTextPanel(), 
						memoryMap.get(MemKeys.LOCAL).getBoolean("$nex_ngcSectorGenSlidersSeen"));
				return true;
		}
		
		return false;
	}
	
	// If player has seen sector generation sliders, print recommended settings
	// If not, modify the settings directly and notify player
	protected void handleRecommendedSectorSize(TextPanelAPI text, boolean seenSliders) {
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		int count = 0;
		float expectedStations = 0;
		float expectedPlanets = 0;
		for (Map.Entry<String, Boolean> tmp : data.factions.entrySet()) {
			if (!tmp.getValue()) continue;
			String factionId = tmp.getKey();
			if (factionId.equals(Factions.INDEPENDENT)) continue;
			
			count++;
			float mult = NexConfig.getFactionConfig(factionId).marketSpawnWeight;
			expectedStations += 1f * mult;
			expectedPlanets += 3f * mult;
		}
		
		if (expectedPlanets < 8) expectedPlanets = 8;
		if (expectedStations < 4) expectedStations = 4;
		
		int expectedSystems = (int)Math.ceil(Math.max(count * 1.2f, count + 3));
		
		Color hl = Misc.getHighlightColor();
		String key = seenSliders ? "recommendedSizeMsg2Alt" : "recommendedSizeMsg2";
		
		text.setFontSmallInsignia();
		text.addPara(StringHelper.getString("exerelin_ngc", "recommendedSizeMsg1"),  hl, count + "");
		text.addPara(StringHelper.getString("exerelin_ngc", key),  hl, 
					expectedSystems + "", 
					Math.round(expectedPlanets) + "", 
					Math.round(expectedStations) + "");
		text.setFontInsignia();
		
		if (!seenSliders) {
			data.numSystems = expectedSystems;
			data.numPlanets = (int)Math.ceil(expectedPlanets);
			data.numStations = (int)Math.ceil(expectedStations);
		}
	}
	
	protected void init(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		//optionsPerPage = 6;
		super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
		listFactions();
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "nex_NGCFactionToggleMenu_leave");
		currPage = lastPage;
		showOptions();
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		if (optionData.equals(RANDOMIZE_OPT))
		{
			ExerelinSetupData data = ExerelinSetupData.getInstance();
			for (Map.Entry<String, Boolean> tmp : data.factions.entrySet())
			{
				String factionId = tmp.getKey();
				if (factionId.equals(Factions.INDEPENDENT)) continue;
				boolean enabled = random.nextBoolean();
				data.factions.put(factionId, enabled);
			}
			options.clear();
			listFactions();
			showOptions();
			return;
		}
		
		super.optionSelected(optionText, optionData);
		lastPage = currPage;
	}
	
	@Override
	public void showOptions() {
		super.showOptions();
		dialog.getOptionPanel().setTooltip(RANDOMIZE_OPT, StringHelper.getString("exerelin_ngc", "randomizeFactionsTooltip"));
		dialog.getOptionPanel().setShortcut("nex_NGCFactionToggleMenu_leave", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	/**
	 * Lists all the factions in a subgroup and allows the user to toggle those factions on/off
	 */
	protected void listFactions()
	{
		if (spawnableFactionIds == null)
		{
			spawnableFactionIds = NexConfig.getFactions(true, false);
			spawnableFactionIds.add(Factions.INDEPENDENT);
			spawnableFactionIds.remove(Factions.PLAYER);
			Collections.sort(spawnableFactionIds, new Comparator<String>()
			{
				@Override
				public int compare(String f1, String f2)
				{
					String n1 = Nex_FactionDirectoryHelper.getFactionDisplayName(f1);
					String n2 = Nex_FactionDirectoryHelper.getFactionDisplayName(f2);
					return n1.compareTo(n2);
				}
			});
		}
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		for (String factionId : spawnableFactionIds)
		{
			//ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
			String optId = TOGGLE_FACTION_OPTION_PREFIX + factionId;
			String text = getText(factionId, data);

			addOption(text, optId);
			addColor(optId, Global.getSector().getFaction(factionId).getBaseUIColor());
		}
		addOption(Misc.ucFirst(StringHelper.getString("randomize")), RANDOMIZE_OPT);
	}
	
	public String getText(String factionId, ExerelinSetupData data)
	{
		if (data == null) data = ExerelinSetupData.getInstance();
		String text = Nex_FactionDirectoryHelper.getFactionDisplayName(factionId) + ": ";
		text += isFactionEnabled(factionId, data) ? StringHelper.getString("enabled") :
				StringHelper.getString("disabled");
		return text;
	}
	
	public boolean isFactionEnabled(String factionId, ExerelinSetupData data)
	{
		if (!data.factions.containsKey(factionId))
			data.factions.put(factionId, true);
		return data.factions.get(factionId);
	}
	
	@Override
	public String getPreviousPageText() {  
		return Misc.ucFirst(StringHelper.getString("previousPage"));  
	}
	
	@Override
	public String getNextPageText() {  
		return Misc.ucFirst(StringHelper.getString("nextPage"));  
	}
}