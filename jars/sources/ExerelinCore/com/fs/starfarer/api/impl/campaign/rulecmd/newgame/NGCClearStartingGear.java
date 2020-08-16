package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;


public class NGCClearStartingGear extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
					
		data.getStartingCargo().clear();
		data.clearAdditionalShips();
		data.getStartingCargo().getCredits().set(0);
		data.getPerson().getStats().setPoints(0);
		//data.getScripts().clear();
		//data.getScriptsBeforeTimePass().clear();
		
		dialog.getVisualPanel().showPersonInfo(data.getPerson(), true);
		
		//memoryMap.get(MemKeys.LOCAL).unset("$nex_customStart");
		
		return true;
	}
}