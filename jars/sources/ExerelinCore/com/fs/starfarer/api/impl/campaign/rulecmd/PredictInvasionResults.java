package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
import exerelin.campaign.InvasionRound.InvasionSimulationType;
import exerelin.utilities.StringHelper;


/**
 *
 * @author Histidine
 * Gets worst and best-case invasion outcomes and notifies the player ahead of time.
 * 
 */
public class PredictInvasionResults extends BaseCommandPlugin {

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
                boolean isRaid = false; //params.get(0).getBoolean(memoryMap);

                InvasionRoundResult worst = InvasionRound.GetInvasionRoundResult(playerFleet, target, isRaid, InvasionSimulationType.PESSIMISTIC);
                InvasionRoundResult best = InvasionRound.GetInvasionRoundResult(playerFleet, target, isRaid, InvasionSimulationType.OPTIMISTIC);

                float attackerStrengthWorst = worst.getAttackerStrength();
                float attackerStrengthBest = best.getAttackerStrength();
                float defenderStrength = best.getDefenderStrength();
                int marinesLostWorst = worst.getMarinesLost();
                int marinesLostBest = best.getMarinesLost();

                String a1 = String.format("%.1f", attackerStrengthWorst);
                String a2 = String.format("%.1f", attackerStrengthBest);
                String attackerStrength = a1 + " - " + a2;
                String defenderStrengthStr = String.format("%.1f", defenderStrength);
                String marinesLost = marinesLostBest + " - " + marinesLostWorst; 

                float defenderAdvantageOverWorstCase = defenderStrength - attackerStrengthWorst;
                float attackerStrengthRange = attackerStrengthBest - attackerStrengthWorst;
                float winChance = 1 - (defenderAdvantageOverWorstCase / attackerStrengthRange);
                winChance = winChance * 100;
                if (winChance < 0) winChance = 0;
                if (winChance > 100) winChance = 100;
                String winChanceStr = String.format("%.1f", winChance) + "%";


                text.setFontVictor();
                text.setFontSmallInsignia();

                Color hl = Misc.getHighlightColor();
                Color red = Misc.getNegativeHighlightColor();
                text.addParagraph("-----------------------------------------------------------------------------");
                text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_invasion", "attackerStrength")) + ": " + attackerStrength);
                text.highlightInLastPara(hl, "" + attackerStrength);
                text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_invasion", "defenderStrength")) + ": " + defenderStrengthStr);
                text.highlightInLastPara(red, "" + defenderStrengthStr);
                text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_invasion", "captureChance")) + ": " + winChanceStr);
                if (winChance < 50)
                        text.highlightInLastPara(red, "" + winChanceStr);
                else
                        text.highlightInLastPara(hl, "" + winChanceStr);
                text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_invasion", "projectedLosses")) + ": " + marinesLost);
                text.highlightInLastPara(red, "" + marinesLost);
 
                text.addParagraph("-----------------------------------------------------------------------------");
                text.setFontInsignia();
                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                memory.set("$exerelinInvasionTimeTaken", best.getTimeTaken(), 0);

                return true;
        }
}