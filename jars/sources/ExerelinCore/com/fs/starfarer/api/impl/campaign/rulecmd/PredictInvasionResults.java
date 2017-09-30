package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
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

	protected static final String STRING_CATEGORY = "exerelin_invasion";
	
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
		boolean isRaid = params.get(0).getBoolean(memoryMap);

		InvasionRoundResult worst = InvasionRound.GetInvasionRoundResult(playerFleet,
						target, isRaid, InvasionSimulationType.PESSIMISTIC);
		InvasionRoundResult best = InvasionRound.GetInvasionRoundResult(playerFleet,
						target, isRaid, InvasionSimulationType.OPTIMISTIC);

		float attackerStrengthWorst = worst.attackerStrength;
		float attackerStrengthBest = best.attackerStrength;
		float defenderStrength = best.defenderStrength;
		int marinesLostWorst = worst.marinesLost;
		int marinesLostBest = best.marinesLost;

		String a1 = String.format("%.1f", attackerStrengthWorst);
		String a2 = String.format("%.1f", attackerStrengthBest);
		String attackerStrength = a1 + " - " + a2;
		String defenderStrengthStr = String.format("%.1f", defenderStrength);
		String marinesLost = marinesLostBest + " - " + marinesLostWorst;		 

		text.setFontVictor();
		text.setFontSmallInsignia();

		Color hl = Misc.getHighlightColor();
		Color red = Misc.getNegativeHighlightColor();
		text.addParagraph("-----------------------------------------------------------------------------");
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "attackerStrength")) + ": " + attackerStrength);
		text.highlightInLastPara(hl, "" + attackerStrength);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "defenderStrength")) + ": " + defenderStrengthStr);
		text.highlightInLastPara(red, "" + defenderStrengthStr);
		if (isRaid)
		{
			float lootLevelLow = Math.min(1.75f, worst.attackerStrength/worst.defenderStrength - 1);
			float lootLevelHigh = Math.min(1.75f, best.attackerStrength/best.defenderStrength - 1);
			if (worst.success)
				lootLevelLow = Math.max(lootLevelLow, InvasionRound.RAID_LOOT_MULT_FLOOR);
			else
				lootLevelLow = Math.max(lootLevelLow, 0);
			if (best.success)
				lootLevelHigh = Math.max(lootLevelHigh, InvasionRound.RAID_LOOT_MULT_FLOOR);
			else
				lootLevelHigh = Math.max(lootLevelHigh, 0);

			float lootLevelAvg = (lootLevelLow + lootLevelHigh) * 0.5f;
			String lootLevel = String.format("%.1f", lootLevelLow) + " – " + String.format("%.1f", lootLevelHigh);

			text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "lootRating")) + ": " + lootLevel );
			if (!worst.success)
					text.highlightInLastPara(Misc.getNegativeHighlightColor(), "" + lootLevel);
			else if (lootLevelAvg < 0.7)
					text.highlightInLastPara(hl, "" + lootLevel);
			else
					text.highlightInLastPara(Misc.getPositiveHighlightColor(), "" + lootLevel);
		}
		else
		{
			float defenderAdvantageOverWorstCase = defenderStrength - attackerStrengthWorst;
			float attackerStrengthRange = attackerStrengthBest - attackerStrengthWorst;
			float winChance = 1 - (defenderAdvantageOverWorstCase / attackerStrengthRange);
			winChance = winChance * 100;
			if (winChance < 0) winChance = 0;
			if (winChance > 100) winChance = 100;
			String winChanceStr = String.format("%.1f", winChance) + "%";

			text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "captureChance")) + ": " + winChanceStr);
			if (winChance < 50)
					text.highlightInLastPara(red, "" + winChanceStr);
			else
					text.highlightInLastPara(hl, "" + winChanceStr);
		}
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "projectedLosses")) + ": " + marinesLost);
		text.highlightInLastPara(red, "" + marinesLost);
 
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$exerelinInvasionTimeTaken", best.timeTaken, 0);

		return true;
	}
}