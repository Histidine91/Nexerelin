package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;


public class Nex_NGCSetOption extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg) {
			case "startingDMods":
				cycleStartingDMods(memoryMap.get(MemKeys.LOCAL));
				return true;
			case "randomStartLocation":
				toggleRandomStartLocation(memoryMap.get(MemKeys.LOCAL));
				return true;
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
	
	public static void toggleRandomStartLocation(MemoryAPI localMem) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		setupData.randomStartLocation = !setupData.randomStartLocation;
		localMem.set("$randomStartLocation", setupData.randomStartLocation, 0);
	}
}