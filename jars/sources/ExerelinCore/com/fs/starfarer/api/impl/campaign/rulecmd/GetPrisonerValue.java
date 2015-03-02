package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.ExerelinConfig;


public class GetPrisonerValue extends BaseCommandPlugin {
	
        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
                int level = Global.getSector().getPlayerPerson().getStats().getLevel();
		int ransomValue = (int)(ExerelinConfig.prisonerBaseRansomValue + ExerelinConfig.prisonerRansomValueIncrementPerLevel * (level - 1));
                int slaveValue = (int)(ExerelinConfig.prisonerBaseSlaveValue + ExerelinConfig.prisonerSlaveValueIncrementPerLevel * (level - 1));
                
                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                memory.set("$ransomValue", ransomValue, 0);
                memory.set("$slaveValue", slaveValue, 0);
                
		return true;
	}
}






