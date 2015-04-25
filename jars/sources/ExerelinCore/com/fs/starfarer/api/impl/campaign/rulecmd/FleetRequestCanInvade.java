package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;

public class FleetRequestCanInvade extends FleetRequestActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                SectorEntityToken target = dialog.getInteractionTarget();
                MarketAPI targetMarket = target.getMarket();
                if (targetMarket == null) return false;
                
                String factionId = targetMarket.getFactionId();        
                return !(factionId.equals("player_npc") || factionId.equals(PlayerFactionStore.getPlayerFactionId()) || factionId.equals("independent"));
        }
}
