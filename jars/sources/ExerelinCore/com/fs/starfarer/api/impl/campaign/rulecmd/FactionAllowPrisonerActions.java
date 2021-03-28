package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import java.util.List;
import java.util.Map;


public class FactionAllowPrisonerActions extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String factionId = params.get(0).getString(memoryMap);
        NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
        if (conf == null) return true;
        return conf.allowPrisonerActions;
    }
}
