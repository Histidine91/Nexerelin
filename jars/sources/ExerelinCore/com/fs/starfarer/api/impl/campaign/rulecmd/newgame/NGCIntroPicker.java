package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import lunalib.lunaUtil.LunaCommons;

public class NGCIntroPicker extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        boolean set = params.get(0).getBoolean(memoryMap);

        if (set) {
            LunaCommons.set(ExerelinConstants.MOD_ID, "nex_ngcViewedIntro", true);
            return true;
        }

        return Boolean.TRUE.equals(LunaCommons.getBoolean(ExerelinConstants.MOD_ID, "nex_ngcViewedIntro")) && !Global.getSettings().isDevMode();
    }
}