package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.backgrounds.CharacterBackgroundUtils;

import java.util.List;
import java.util.Map;

public class Nex_HasBackground extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String backgroundId = params.get(0).getString(memoryMap);
        if (CharacterBackgroundUtils.isBackgroundActive(backgroundId)) return true;
        return false;
    }
}
