package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import java.util.List;
import java.util.Map;


public class IsValidInvasionTarget extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        SectorEntityToken entity = dialog.getInteractionTarget();
		if (entity == null) return false;
		MarketAPI market = entity.getMarket();
		if (market == null) return false;
		if (market.hasCondition(Conditions.ABANDONED_STATION)) return false;
		if (market.getId().equals("prismFreeport_market")) return false;
		FactionAPI faction = market.getFaction();
		if (faction.isPlayerFaction()) return false;
		if (faction.isNeutralFaction()) return false;
		String factionId = faction.getId();
		if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return false;
		if (factionId.equals(PlayerFactionStore.getPlayerFactionId())) return false;
		
        return true;
    }
}
