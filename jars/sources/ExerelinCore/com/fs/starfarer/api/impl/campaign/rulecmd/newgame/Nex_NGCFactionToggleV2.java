package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.ui.CustomPanelPluginWithInput.ButtonEntry;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.rulecmd.newgame.Nex_NGCPopulateCustomPanelOptions.initRadioButton;
import static exerelin.campaign.ui.CustomPanelPluginWithInput.RadioButtonEntry;

public class Nex_NGCFactionToggleV2 extends BaseCommandPlugin {
		
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg)
		{				
			case "listFactions":
				addFactionOptions(dialog, memoryMap);
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
		List<String> spawnableFactionIds = NexConfig.getFactions(true, false);
		Random random = new Random();
		
		for (String factionId : spawnableFactionIds) {
			if (factionId.equals(Factions.INDEPENDENT)) continue;
			if (factionId.equals(Factions.PLAYER)) continue;
			Boolean enabled = data.factions.get(factionId);
			
			// if faction enable state is unspecified, flip a coin
			if (enabled == null) {
				enabled = random.nextBoolean();
				data.factions.put(factionId, enabled);
				Global.getLogger(this.getClass()).info("Randomized faction " + factionId + ": " + enabled);
			}
			if (!enabled) continue;
			
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
		
	public void addFactionOptions(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI info = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		List<String> spawnableFactionIds = NexConfig.getFactions(true, false, true);
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
		
		info.addPara(StringHelper.getString("exerelin_ngc", "factionToggleInfo"), 3);
		ButtonAPI button = info.addButton(StringHelper.getString("exerelin_ngc", "factionToggleBtnRandomize"), 
				"nex_randomizeAll", 128, 24, 3);
		
		for (String factionId : spawnableFactionIds) {
			if (!data.factions.containsKey(factionId))
				data.factions.put(factionId, true);
				
			addFactionOption(factionId, panel, info, plugin);
		}
		
		plugin.addButton(new ButtonEntry(button, "nex_randomizeAll") {
			@Override
			public void onToggle() {
				randomizeFactions(data);
				memoryMap.get(MemKeys.LOCAL).set("$option", "nex_NGCFactionToggleMenu", 0);
				FireBest.fire(null, dialog, memoryMap, "NewGameOptionSelected");
			}
		});
		
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	public void randomizeFactions(ExerelinSetupData data) {
		Random random = new Random();		
		
		List<String> factions = NexConfig.getFactions(true, false);
		for (String factionId : factions) {
			data.factions.put(factionId, random.nextBoolean());
		}
	}
	
	public String getButtonStr(int index) {
		switch (index) {
			case 0:
				return StringHelper.getString("enabled", true);
			case 1:
				return StringHelper.getString("disabled", true);
			case 2:
				return StringHelper.getString("random", true);
				
			default:
				return "error";
		}
	}
	
	public void addFactionOption(final String factionId, CustomPanelAPI panel, 
			TooltipMakerAPI info, InteractionDialogCustomPanelPlugin plugin) 
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
		CustomPanelAPI buttonPanel = Nex_NGCPopulateCustomPanelOptions.prepOption(panel, info, 
				Nex_FactionDirectoryHelper.getFactionDisplayName(factionId), 
				faction.getCrest(), faction.getBaseUIColor(), plugin, null);
		
		final List<ButtonAPI> buttons = new ArrayList<>();
		TooltipMakerAPI lastHolder = null;
		final ExerelinSetupData data = ExerelinSetupData.getInstance();
		boolean enabled = data.factions.get(factionId);
		
		for (int i=0; i<3; i++) {			
			String name = getButtonStr(i);
			
			lastHolder = initRadioButton("nex_enableFaction_" + factionId + " " + i, name, 
					(i == 0) == enabled && i != 2, buttonPanel, lastHolder, buttons);
		}
		final List<RadioButtonEntry> buttonEntries = new ArrayList<>();
		
		for (int i=0; i<3; i++) {
			final int index = i;
			RadioButtonEntry radio = new RadioButtonEntry(buttons.get(i),
					"nex_enableFaction_" + factionId + " " + i, buttonEntries)
			{
				@Override
				public void onToggleImpl() {
					if (index < 2)
						data.factions.put(factionId, index == 0);
					else
						data.factions.remove(factionId);
				}
			};
			buttonEntries.add(radio);
			plugin.addButton(radio);
		}
	}
}