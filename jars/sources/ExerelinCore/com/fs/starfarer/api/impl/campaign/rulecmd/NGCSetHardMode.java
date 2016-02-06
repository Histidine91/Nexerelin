package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;


public class NGCSetHardMode extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean setting = params.get(0).getBoolean(memoryMap);
		ExerelinSetupData.getInstance().hardMode = setting;
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$hardMode", setting, 0);
		return true;
	}
}