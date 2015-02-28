package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import static com.fs.starfarer.api.Global.getSector;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.PlayerFactionStore;

public class AgentRaiseRelations extends AgentActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                SectorAPI sector = Global.getSector();
                SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();

                List<CargoStackAPI> stacks = sector.getPlayerFleet().getCargo().getStacksCopy();
                for (CargoStackAPI stack : stacks)
                {
                    if (stack.getCommodityId().equals("agent"))
                    {
                        if (stack.getSize() < 1) return false;
                        stack.subtract(1f);
                        break;
                    }
                }
                
                MarketAPI market = target.getMarket();
                FactionAPI playerAlignedFaction = sector.getFaction(PlayerFactionStore.getPlayerFactionId());
                CovertOpsManager.agentRaiseRelations(market, playerAlignedFaction, market.getFaction(), true);
                
                return true;
        }
}
