package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.AllianceManager.Alliance;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;


public class FormAlliance extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!playerFactionId.equals("player_npc")) {
			return false;
		}
		
		//SectorAPI sector = Global.getSector();
		//FactionAPI playerFaction = sector.getFaction(playerFactionId);
		String factionId = params.get(0).getString(memoryMap);
		
		TextPanelAPI text = dialog.getTextPanel();
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		Alliance alliance = AllianceManager.createAlliance(playerFactionId, factionId, AllianceManager.getBestAlignment(factionId, playerFactionId));
		AllianceManager.setPlayerInteractionTarget(null);
		
		ExerelinUtilsReputation.syncPlayerRelationshipsToFaction("player_npc", true);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$isInAlliance", true, 0);
		memory.set("$allianceId", alliance.name, 0);
		memory = memoryMap.get(MemKeys.FACTION);
		memory.set("$isInAlliance", true, 0);
		memory.set("$allianceId", alliance.name, 0);
		
		// events are already reported by AllianceManager
		String str = StringHelper.getString("exerelin_alliances", "formedAlliance");
		str = StringHelper.substituteToken(str, "$NewAlliance", alliance.name);
		str = StringHelper.substituteToken(str, "$theFaction", Global.getSector().getFaction(factionId).getDisplayNameLongWithArticle());
		text.addParagraph(str, Misc.getHighlightColor());
		
		return true;
	}
}