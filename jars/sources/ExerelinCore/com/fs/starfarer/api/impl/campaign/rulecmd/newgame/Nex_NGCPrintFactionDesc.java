package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

import java.util.List;
import java.util.Map;

public class Nex_NGCPrintFactionDesc extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String factionId = memoryMap.get(MemKeys.LOCAL).getString("$playerFaction");
        try {
            Description desc = Global.getSettings().getDescription(factionId, Description.Type.FACTION);
            FactionAPI faction = Global.getSector().getFaction(factionId);
            TooltipMakerAPI tt = dialog.getTextPanel().beginTooltip();
            TooltipMakerAPI sub = tt.beginImageWithText(faction.getCrest(), 64);
            String str = desc.getText1FirstPara();
            if (faction.isPlayerFaction()) {
                str = StringHelper.getString("exerelin_ngc", "factionDesc_player");
            }
            if (!str.isBlank() && !str.equals("No description... yet")) {
                sub.addPara(str, 0);
                tt.addImageWithText(0);
                dialog.getTextPanel().addTooltip();
            }
        } catch (Exception ex) {

        }
        return true;
    }
}
