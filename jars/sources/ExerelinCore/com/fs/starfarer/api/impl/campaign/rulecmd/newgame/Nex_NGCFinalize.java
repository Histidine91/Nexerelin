package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.world.scenarios.StartScenarioManager;
import java.util.List;
import java.util.Map;

public class Nex_NGCFinalize extends BaseCommandPlugin {
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		if (data.startScenario != null) {
			StartScenarioManager.prepScenario(data.startScenario);
		}
		return true;
	}
	
}
