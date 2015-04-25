package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

public class FleetRequestChangeMarineCount extends FleetRequestActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                int marines = (int)memory.getFloat("$fleetRequestMarines");                
                marines += 250;
                if (marines > 1000) marines = 0;
                
                memory.set("$fleetRequestMarines", marines, 0);
                return true;
        }
}
