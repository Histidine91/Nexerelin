package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;


public class Nex_IsFactionRuler extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId;
		if (params.size() > 0) factionId = params.get(0).getString(memoryMap);
		else factionId = PlayerFactionStore.getPlayerFactionId();
		
		//FactionAPI faction = Global.getSector().getFaction(factionId);
		
		return isRuler(factionId);
	}
	
	public static boolean isRuler(String factionId)
	{
		if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
			return true;
		
		if (factionId.equals(PlayerFactionStore.getPlayerFactionId()))		
			return ExerelinConfig.factionRuler;
		
		return false;
	}
}