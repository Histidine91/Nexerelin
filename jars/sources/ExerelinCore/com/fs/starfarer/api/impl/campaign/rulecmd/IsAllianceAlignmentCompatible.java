package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.ExerelinConfig;
import java.util.List;
import java.util.Map;


public class IsAllianceAlignmentCompatible extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        
        String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!playerFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) {
			return false;
		}
		if (ExerelinConfig.ignoreAlignmentForAlliances) return true;
		
		Alliance alliance = AllianceManager.getFactionAlliance(playerFactionId);
		String factionId = params.get(0).getString(memoryMap);
		float compat = AllianceManager.getAlignmentCompatibilityWithAlliance(factionId, alliance);
		return compat >= AllianceManager.MIN_ALIGNMENT_TO_JOIN_ALLIANCE;
    }
}
