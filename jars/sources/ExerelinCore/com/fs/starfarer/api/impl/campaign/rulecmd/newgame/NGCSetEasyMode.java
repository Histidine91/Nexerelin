package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;

// use this instead of vanilla's NGCSetDifficulty to make sure ExerelinSetupData remembers our setting
public class NGCSetEasyMode extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean setting = params.get(0).getBoolean(memoryMap);
		ExerelinSetupData.getInstance().easyMode = setting;
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$easyMode", setting, 0);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		if (setting) data.setDifficulty("easy");
		else data.setDifficulty("normal");
		return true;
	}
}