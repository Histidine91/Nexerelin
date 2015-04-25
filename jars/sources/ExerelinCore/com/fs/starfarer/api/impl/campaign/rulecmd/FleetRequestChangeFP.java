package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

public class FleetRequestChangeFP extends FleetRequestActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                int fp = (int)memory.getFloat("$fleetRequestFP");                
                fp += 60;
                if (fp > 240) fp = 60;
                
                memory.set("$fleetRequestFP", fp, 0);
                return true;
        }
}
