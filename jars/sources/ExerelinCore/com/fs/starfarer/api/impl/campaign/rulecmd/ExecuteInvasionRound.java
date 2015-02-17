package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.InvasionRound.InvasionRoundResult;

public class ExecuteInvasionRound extends BaseCommandPlugin {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
                TextPanelAPI text = dialog.getTextPanel();

                /*if (!(target instanceof MarketAPI ))
                {
                        text.addParagraph("Damnit, something's broken here!");
                        return false;
                }*/

                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                FactionAPI faction = target.getFaction();

                InvasionRoundResult result = InvasionRound.AttackMarket(playerFleet, target, false);

                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                memory.set("$exerelinInvasionSuccessful", result.getSuccess(), 0);

                int marinesLost = result.getMarinesLost();
                int marinesRemaining = playerFleet.getCargo().getMarines();
                String attackerStrength = String.format("%.1f", result.getAttackerStrength());
                String defenderStrength = String.format("%.1f", result.getDefenderStrength());

                text.setFontVictor();
                text.setFontSmallInsignia();

                Color hl = Misc.getHighlightColor();
                Color red = Misc.getNegativeHighlightColor();
                text.addParagraph("-----------------------------------------------------------------------------");
                text.addParagraph("Attacker strength: " + attackerStrength);
                text.highlightInLastPara(hl, "" + attackerStrength);
                text.addParagraph("Defender strength: " + defenderStrength);
                text.highlightInLastPara(red, "" + defenderStrength);
                text.addParagraph("Marines lost: " + marinesLost);
                text.highlightInLastPara(red, "" + marinesLost);
                text.addParagraph("Marines remaining: " + marinesRemaining);
                text.highlightInLastPara(hl, "" + marinesRemaining);
                /*
                if (!illegalFound.isEmpty()) {
                        text.addParagraph("Contraband found!", red);
                        String para = "";
                        List<String> highlights = new ArrayList<String>();
                        for (CargoStackAPI stack : illegalFound.getStacksCopy()) {
                                        para += stack.getDisplayName() + " x " + (int)stack.getSize() + "\n";
                                        highlights.add("" + (int)stack.getSize());
                        }
                        para = para.substring(0, para.length() - 1);
                        text.addParagraph(para);
                        text.highlightInLastPara(hl, highlights.toArray(new String [0]));

                        text.addParagraph("Fine: " + (int) fine);
t			ext.highlightInLastPara(hl, "" + (int) fine);
                }
                */
                text.addParagraph("-----------------------------------------------------------------------------");
                text.setFontInsignia();

                return true;
        }
}
