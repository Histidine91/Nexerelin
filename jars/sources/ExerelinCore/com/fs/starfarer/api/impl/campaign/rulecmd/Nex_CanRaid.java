package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import java.util.List;
import java.util.Map;

public class Nex_CanRaid extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		SectorEntityToken entity = dialog.getInteractionTarget();
		if (entity == null) return false;
		MarketAPI market = entity.getMarket();
		if (market == null) return false;
		if (!market.isInEconomy()) return false;
		if (market.isPlanetConditionMarketOnly()) return false;
		String factionId = market.getFactionId();
		if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID) || factionId.equals(PlayerFactionStore.getPlayerFactionId()))
			return false;
		
		return true;
	}
}
