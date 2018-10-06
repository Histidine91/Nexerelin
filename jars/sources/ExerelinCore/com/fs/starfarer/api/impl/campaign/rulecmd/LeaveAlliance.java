package com.fs.starfarer.api.impl.campaign.rulecmd;

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


public class LeaveAlliance extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!isRuler(playerFactionId)) {
			return false;
		}
		
		Alliance oldAlliance = AllianceManager.getFactionAlliance(playerFactionId);
		boolean oldAllianceDissolved = false;
		
		TextPanelAPI text = dialog.getTextPanel();
		String str = StringHelper.getString("exerelin_alliances", "leftAlliance");
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		AllianceManager.leaveAlliance(playerFactionId, false);
		AllianceManager.setPlayerInteractionTarget(null);
		
		//PlayerFactionStore.loadIndependentPlayerRelations(false); //true?
		//ExerelinUtilsReputation.syncFactionRelationshipsToPlayer("player_npc");
		
		oldAllianceDissolved = (oldAlliance.getMembersCopy().size() <= 1);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		AllianceManager.unsetMemoryKeys(memory);
		
		if (oldAllianceDissolved) {
			memory = memoryMap.get(MemKeys.FACTION);
			AllianceManager.unsetMemoryKeys(memory);			
		}

		// events are already reported by AllianceManager
		
		str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance.getName());
		text.addParagraph(str, Misc.getHighlightColor());
		
		if (oldAllianceDissolved) {
			str = StringHelper.getString("exerelin_alliances", "allianceDissolved");
			str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance.getName());
			text.addParagraph(str, Misc.getHighlightColor());
		}
		
		return true;
	}
}