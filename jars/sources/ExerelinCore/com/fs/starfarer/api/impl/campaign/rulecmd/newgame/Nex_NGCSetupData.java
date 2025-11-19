package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.questskip.QuestChainSkipEntry;

import java.util.List;
import java.util.Map;

public class Nex_NGCSetupData extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String arg = params.get(0).getString(memoryMap);
        switch (arg) {
            case "save":
                ExerelinSetupData.writeToFile();
                return true;
            case "load":
                ExerelinSetupData.readFromFile();
                return true;
            case "reset":
                ExerelinSetupData.resetInstance();
                ExerelinSetupData.writeToFile();
                QuestChainSkipEntry.resetEnabledEntries();
                NGCGetExerelinDefaults.loadMemoryKeysAndMisc(memoryMap.get(MemKeys.LOCAL));
                return true;
            case "debug":
                ExerelinSetupData data = ExerelinSetupData.getInstance();
                return false;
        }
        return false;
    }
}
