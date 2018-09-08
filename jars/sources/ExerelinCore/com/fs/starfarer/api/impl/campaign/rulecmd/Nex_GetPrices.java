package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class Nex_NGCFactionToggle extends PaginatedOptions {
	
	public static final String TOGGLE_FACTION_OPTION_PREFIX = "nex_NGCToggleFaction_";
	public static final int OPT_LENGTH = TOGGLE_FACTION_OPTION_PREFIX.length();
	public static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	protected static int lastPage = 0;
	protected static List<String> spawnableFactionIds = null;
	
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
		}
		
		return false;
	}
	
	protected void init(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		//optionsPerPage = 6;
		super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
		listFactions();
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "exerelinNGCFactionOptions");
		dialog.getOptionPanel().setShortcut("exerelinNGCFactionOptions", Keyboard.KEY_ESCAPE, false, false, false, false);
		currPage = lastPage;
		showOptions();
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		super.optionSelected(optionText, optionData);
		lastPage = currPage;
	}
	
	/**
	 * Lists all the factions in a subgroup and allows the user to toggle those factions on/off
	 */
	protected void listFactions()
	{
		if (spawnableFactionIds == null)
		{
			spawnableFactionIds = ExerelinConfig.getFactions(true, false);
			spawnableFactionIds.add(Factions.INDEPENDENT);
			spawnableFactionIds.remove(ExerelinConstants.PLAYER_NPC_ID);
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
		}
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