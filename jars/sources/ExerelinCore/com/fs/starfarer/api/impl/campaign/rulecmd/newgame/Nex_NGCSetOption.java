package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;

@Deprecated
public class Nex_NGCSetOption extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		
		switch (arg) {
			case "startingDMods":
				cycleStartingDMods(local);
				return true;
			case "randomStartLocation":
				toggleRandomStartLocation(dialog.getTextPanel(), local);
				return true;
			case "randomAntioch":
				toggleRandomAntioch(dialog.getTextPanel(), local);
		}
		return false;
	}
	
	public static void cycleStartingDMods(MemoryAPI localMem) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		setupData.dModLevel += 1;
		if (setupData.dModLevel > ExerelinSetupData.NUM_DMOD_LEVELS - 1)
			setupData.dModLevel = 0;
		
		String str = ExerelinSetupData.getDModCountText(setupData.dModLevel);
		localMem.set("$nex_ngcDModsString", str);
	}
	
	public static void toggleRandomStartLocation(TextPanelAPI text, MemoryAPI localMem) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		setupData.randomStartLocation = !setupData.randomStartLocation;
		
		/*
		String str = String.format(StringHelper.getString("exerelin_ngc", "randomStartLocation_msg"),
				StringHelper.getString(setupData.randomStartLocation ? "enabled" : "disabled"));
		text.setFontSmallInsignia();
		text.addPara(str);
		text.setFontInsignia();
		*/

		localMem.set("$randomStartLocation", setupData.randomStartLocation, 0);
	}
	
	public static void toggleRandomAntioch(TextPanelAPI text, MemoryAPI localMem) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean value = !setupData.randomAntiochEnabled;
		setupData.randomAntiochEnabled = value;
		localMem.set("$nex_antiochInRandom", value, 0);
	}
	
	public static void skipStory(TextPanelAPI text, MemoryAPI localMem) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		boolean value = !setupData.skipStory;
		setupData.skipStory = value;
		localMem.set("$nex_skipStory", value, 0);
	}
}