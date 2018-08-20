package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;


public class Nex_IsFactionRuler extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		FactionAPI faction = dialog.getInteractionTarget().getFaction();
		
		if (faction.getId().equals(ExerelinConstants.PLAYER_NPC_ID))
			return true;
		
		// TODO: faction boss of non-follower factions
		
		return false;
	}
}