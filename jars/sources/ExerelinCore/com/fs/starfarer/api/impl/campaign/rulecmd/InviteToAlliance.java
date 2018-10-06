package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import static com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler.isRuler;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.StringHelper;


public class InviteToAlliance extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!isRuler(playerFactionId)) {
			return false;
		}
		
		//SectorAPI sector = Global.getSector();
		//FactionAPI playerFaction = sector.getFaction(playerFactionId);
		Alliance alliance = AllianceManager.getFactionAlliance(playerFactionId);
		String factionId = params.get(0).getString(memoryMap);
		
		TextPanelAPI text = dialog.getTextPanel();
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		
		AllianceManager.joinAllianceStatic(factionId, alliance);
		AllianceManager.setPlayerInteractionTarget(null);
		
		//ExerelinUtilsReputation.syncPlayerRelationshipsToFaction("player_npc"); //?
		
		MemoryAPI memory = memoryMap.get(MemKeys.FACTION);
		AllianceManager.setMemoryKeys(memory, alliance);
		
		// events are already reported by AllianceManager
		String str = StringHelper.getString("exerelin_alliances", "invitedToAlliance");
		str = StringHelper.substituteToken(str, "$TheFaction", Misc.ucFirst(Global.getSector().getFaction(factionId).getDisplayNameLongWithArticle()));
		str = StringHelper.substituteToken(str, "$NewAlliance", alliance.getName());
		text.addParagraph(str, Misc.getHighlightColor());
		
		return true;
	}
}