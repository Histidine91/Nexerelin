package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;


public class NGCGetExerelinDefaults extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
                ExerelinSetupData.resetInstance();
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
                MemoryAPI map = memoryMap.get(MemKeys.LOCAL);
                map.set("$numSystems", setupData.numSystems, 0);
                map.set("$maxPlanets", setupData.maxPlanets, 0);
                map.set("$maxStations", setupData.maxStations, 0);
                return true;
	}
}






