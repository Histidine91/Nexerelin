package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class Nex_NGCMisc extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String arg = params.get(0).getString(memoryMap);
        switch (arg) {
            case "showFleet":
                CharacterCreationData ccd = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
                NGCAddStartingShipsByFleetType.generateFleetFromVariantIds(dialog, ccd,
                        memoryMap.get(MemKeys.LOCAL).getString("$nex_lastSelectedFleetType"), ccd.getStartingShips(), true);
                return true;
        }
        return false;
    }
}
