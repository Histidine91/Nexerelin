package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;


public class NGCSetMaxPlanets extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
                int num = (int)params.get(0).getFloat(memoryMap);
                setupData.maxPlanets = num;
                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                memory.set("$maxPlanets", num, 0);
                return true;
	}
}






