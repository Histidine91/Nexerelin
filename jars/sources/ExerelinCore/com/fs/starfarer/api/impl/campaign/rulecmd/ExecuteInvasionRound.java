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
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.utilities.StringHelper;

public class ExecuteInvasionRound extends BaseCommandPlugin {

    protected static final String STRING_CATEGORY = "exerelin_invasion";
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
		
		boolean isRaid = params.get(0).getBoolean(memoryMap);
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		TextPanelAPI text = dialog.getTextPanel();

		/*if (!(target instanceof MarketAPI ))
		{
			text.addParagraph("Damnit, something's broken here!");
			return false;
		}*/
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		FactionAPI faction = target.getFaction();

		InvasionRoundResult result = InvasionRound.AttackMarket(playerFleet, target, isRaid);
		if (result == null)
		{
			if (isRaid) memory.set("$exerelinRaidCancelled", true, 0);
			else memory.set("$exerelinInvasionCancelled", true, 0);
			return false;
		}

		if (isRaid) memory.set("$exerelinRaidSuccessful", result.success, 0);
		else memory.set("$exerelinInvasionSuccessful", result.success, 0);

		int marinesLost = result.marinesLost;
		int marinesRemaining = playerFleet.getCargo().getMarines();
		String attackerStrength = String.format("%.1f", result.attackerStrength);
		String defenderStrength = String.format("%.1f", result.defenderStrength);

		text.setFontVictor();
		text.setFontSmallInsignia();

		Color hl = Misc.getHighlightColor();
		Color red = Misc.getNegativeHighlightColor();
		text.addParagraph(StringHelper.HR);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "attackerStrength")) + ": " + attackerStrength);
		text.highlightInLastPara(hl, "" + attackerStrength);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "defenderStrength")) + ": " + defenderStrength);
		text.highlightInLastPara(red, "" + defenderStrength);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "marinesLost")) + ": " + marinesLost);
		text.highlightInLastPara(red, "" + marinesLost);
		text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "marinesRemaining")) + ": " + marinesRemaining);
		text.highlightInLastPara(hl, "" + marinesRemaining);
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();

		if (result.success)
		{
			Global.getSector().adjustPlayerReputation(
				new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.COMBAT_AGGRESSIVE, 0),
				faction.getId());
			memoryMap.get(MemKeys.MARKET).set(InvasionRound.LOOT_MEMORY_KEY, result.loot, 0);
		}

		return true;
	}
}
