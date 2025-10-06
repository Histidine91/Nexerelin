package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.plugins.ExerelinModPlugin;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaUtil.LunaCommons;

import java.util.List;
import java.util.Map;

public class Nex_NGCIntroPicker extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (!ExerelinModPlugin.HAVE_LUNALIB) return true;

        boolean set = params.get(0).getBoolean(memoryMap);

        if (set) {
            LunaCommons.set(ExerelinConstants.MOD_ID, "nex_ngcViewedIntro", true);
            return true;
        }

        if (LunaSettings.getBoolean(ExerelinConstants.MOD_ID, "nex_ngcShowIntro")) return true;
        return Boolean.FALSE.equals(LunaCommons.getBoolean(ExerelinConstants.MOD_ID, "nex_ngcViewedIntro")) && !Global.getSettings().isDevMode();
    }
}