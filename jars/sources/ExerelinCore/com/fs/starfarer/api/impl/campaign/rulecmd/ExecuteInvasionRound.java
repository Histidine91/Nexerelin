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
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;

public class ExecuteInvasionRound extends BaseCommandPlugin {

	protected static final String STRING_CATEGORY = "exerelin_invasion";
	public static final float ALERT_RANGE_HYPERSPACE = 2500;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		boolean isRaid = params.get(0).getBoolean(memoryMap);
		SectorEntityToken target = dialog.getInteractionTarget();
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
		
		RepActions repAct;
		boolean playerKnown = ExerelinUtilsFleet.isPlayerSeenAndIdentified(faction);
		
		if (isRaid)
		{
			if (result.success)
			{
				repAct = playerKnown ? RepActions.COMBAT_AGGRESSIVE : RepActions.COMBAT_AGGRESSIVE_TOFF;
			}
			else
			{
				repAct = playerKnown ? RepActions.COMBAT_NORMAL : RepActions.COMBAT_NORMAL_TOFF;
			}
			if (playerKnown) SectorManager.createWarmongerEvent(faction.getId(), target);
			else makeFleetsHostile(target);
		}
		else
		{
			repAct = result.success ? RepActions.COMBAT_AGGRESSIVE : RepActions.COMBAT_NORMAL;
		}
		
		Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(repAct, 0),
				faction.getId());
		if (result.success)
		{
			memoryMap.get(MemKeys.MARKET).set(InvasionRound.LOOT_MEMORY_KEY, result.loot, 0);
		}

		return true;
	}
	
	protected void makeFleetsHostile(SectorEntityToken target) {
        boolean hyperspace = target.getContainingLocation().isHyperspace();
        boolean warnedAny = false;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        for (CampaignFleetAPI fleet : target.getContainingLocation().getFleets()) {
			if (fleet == playerFleet) continue;
            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
			
            // hyperspace range limit
            if (hyperspace && MathUtils.isWithinRange(fleet, target, ALERT_RANGE_HYPERSPACE)) {
                continue;
            }
            // only fleets which can see player fleet are warned
            if (!canSeeFleet(fleet, playerFleet)) {
                continue;
            }
			if (fleet.getAI() == null) continue;
			if (fleet.isStationMode()) continue;
            warnedAny = true;
            Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE, "nex_saw_raid", true, 7);
		}
        if (warnedAny) {
            Global.getSector().addPing(target, "nex_raid_alert");
        }
    }
		
	protected boolean canSeeFleet(CampaignFleetAPI seer, CampaignFleetAPI target) {
        SectorEntityToken.VisibilityLevel vis = target.getVisibilityLevelTo(seer);
        return vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS || vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS;
    }
}
