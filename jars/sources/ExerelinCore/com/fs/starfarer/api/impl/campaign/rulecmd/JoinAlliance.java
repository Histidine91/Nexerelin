package com.fs.starfarer.api.impl.campaign.rulecmd;

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


public class JoinAlliance extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!playerFactionId.equals("player_npc")) {
			return false;
		}
		
		//SectorAPI sector = Global.getSector();
		//FactionAPI playerFaction = sector.getFaction(playerFactionId);
		Alliance oldAlliance = AllianceManager.getFactionAlliance(playerFactionId);
		String newAllianceName = params.get(0).getString(memoryMap);
		Alliance newAlliance = AllianceManager.getAllianceByName(newAllianceName);
		boolean oldAllianceDissolved = false;
		
		TextPanelAPI text = dialog.getTextPanel();
		String str;
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		
		if (oldAlliance != null && oldAlliance != newAlliance) {
			AllianceManager.leaveAlliance(playerFactionId, false);
			
			oldAllianceDissolved = (oldAlliance.members.size() <= 1);
			
			str = StringHelper.getString("exerelin_alliances", "switchedAlliances");
			str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance.name);
		} else {
			str = StringHelper.getString("exerelin_alliances", "joinedAlliance");
		}
		if (oldAlliance != newAlliance)
			AllianceManager.joinAllianceStatic(playerFactionId, newAlliance);
		
		AllianceManager.setPlayerInteractionTarget(null);
		
		//ExerelinUtilsReputation.syncPlayerRelationshipsToFaction("player_npc"); //?
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$isInAlliance", true, 0);
		memory.set("$allianceId", newAllianceName, 0);
		
		// events are already reported by AllianceManager
		
		str = StringHelper.substituteToken(str, "$NewAlliance", newAlliance.name);
		text.addParagraph(str, Misc.getPositiveHighlightColor());
		
		if (oldAllianceDissolved) {
			str = StringHelper.getString("exerelin_alliances", "allianceDissolved");
			str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance.name);
			text.addParagraph(str, Misc.getHighlightColor());
		}
		
		return true;
	}
}