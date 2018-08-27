package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;


public class Nex_IsFactionRuler extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId;
		if (params.size() > 0) factionId = params.get(0).getString(memoryMap);
		else factionId = PlayerFactionStore.getPlayerFactionId();
		
		//FactionAPI faction = Global.getSector().getFaction(factionId);
		
		if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
			return true;
		
		// TODO: faction boss of non-follower factions
		
		return false;
	}
}