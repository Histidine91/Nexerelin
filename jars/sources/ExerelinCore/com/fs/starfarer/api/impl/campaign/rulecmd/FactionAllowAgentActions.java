package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import java.util.List;
import java.util.Map;


public class FactionAllowAgentActions extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String factionId = params.get(0).getString(memoryMap);
        ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (conf == null) return true;
        return conf.allowAgentActions;
    }
}
