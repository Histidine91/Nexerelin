package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.StringHelper;
import exerelin.world.scenarios.ScenarioManager;
import exerelin.world.scenarios.ScenarioManager.StartScenarioDef;


public class NGCSetCorvusMode extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean setting = params.get(0).getBoolean(memoryMap);
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		data.corvusMode = setting;
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$corvusMode", setting, 0);
		
		// disable custom scenario if appropriate
		if (data.startScenario != null) {
			StartScenarioDef def = ScenarioManager.getScenarioDef(data.startScenario);
			if (setting && def.randomSectorOnly) {
				Nex_NGCCustomScenario.selectScenario(memory, null);
				String msg = StringHelper.getStringAndSubstituteToken(
						"exerelin_ngc", "customScenarioRemoved", "$name", def.name);
				dialog.getTextPanel().setFontSmallInsignia();
				dialog.getTextPanel().addPara(msg, Misc.getNegativeHighlightColor());
				dialog.getTextPanel().setFontInsignia();
			}
		}
		
		return true;
	}
}