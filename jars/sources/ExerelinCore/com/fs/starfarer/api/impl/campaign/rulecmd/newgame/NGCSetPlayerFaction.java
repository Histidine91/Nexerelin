package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import exerelin.campaign.PlayerFactionStore;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;


public class NGCSetPlayerFaction extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		PlayerFactionStore.setPlayerFactionIdNGC(factionId);
		if (!factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
			ExerelinSetupData.getInstance().freeStart = conf.freeStart;
		}
		return true;
	}
}