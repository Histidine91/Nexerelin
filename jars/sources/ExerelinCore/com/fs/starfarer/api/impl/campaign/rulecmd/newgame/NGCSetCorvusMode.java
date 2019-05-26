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
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import exerelin.world.scenarios.StartScenarioManager;
import exerelin.world.scenarios.StartScenarioManager.StartScenarioDef;
import java.awt.Color;


public class NGCSetCorvusMode extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean setting = params.get(0).getBoolean(memoryMap);
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		data.corvusMode = setting;
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$corvusMode", setting, 0);
		
		// disable Prism if SCY will create its own
		if (setting == true && ExerelinConfig.getFactions(false, false).contains("SCY"))
		{
			data.prismMarketPresent = false;
			memory.set("$prismMarketPresent", false, 0);
		}
		// disable custom scenario if appropriate
		if (data.startScenario != null) {
			StartScenarioDef def = StartScenarioManager.getScenarioDef(data.startScenario);
			if (setting == true && def.randomSectorOnly) {
				Nex_NGCCustomStartScenario.selectScenario(memory, null);
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