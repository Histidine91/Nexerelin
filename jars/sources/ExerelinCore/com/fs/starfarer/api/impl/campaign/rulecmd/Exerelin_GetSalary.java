package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.ExerelinConfig;


public class Exerelin_GetSalary extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		int level = Global.getSector().getPlayerPerson().getStats().getLevel();
		int salary = (int)(ExerelinConfig.playerBaseSalary + ExerelinConfig.playerSalaryIncrementPerLevel * (level - 1));

		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$salary", Misc.getWithDGS(salary), 0);
		
		return true;
	}
}