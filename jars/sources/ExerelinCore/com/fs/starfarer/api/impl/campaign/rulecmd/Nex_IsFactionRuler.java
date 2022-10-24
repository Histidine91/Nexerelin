package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexConfig;

public class Nex_IsFactionRuler extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId;
		if (params.size() > 0) factionId = params.get(0).getString(memoryMap);
		else factionId = PlayerFactionStore.getPlayerFactionId();
		
		//FactionAPI faction = Global.getSector().getFaction(factionId);
		
		return isRuler(factionId);
	}
	
	public static boolean isRuler(FactionAPI faction) {
		return isRuler(faction.getId());
	}
	
	public static boolean isRuler(String factionId)
	{
		if (factionId == null) return false;
		if (factionId.equals(Factions.PLAYER))
			return true;
		
		if (factionId.equals(PlayerFactionStore.getPlayerFactionId()))
			return NexConfig.factionRuler || NexConfig.getFactionConfig(factionId).isPlayerRuled;
		
		return false;
	}
}