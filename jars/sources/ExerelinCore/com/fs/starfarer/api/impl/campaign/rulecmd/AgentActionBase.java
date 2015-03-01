package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;

public abstract class AgentActionBase extends BaseCommandPlugin {
        
        @Override
        public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                SectorAPI sector = Global.getSector();

                List<CargoStackAPI> stacks = sector.getPlayerFleet().getCargo().getStacksCopy();
                boolean agentSpent = false;
                for (CargoStackAPI stack : stacks)
                {
                    if (stack.isNull()) continue;
                    if (stack.getCommodityId() != null && stack.getCommodityId().equals("agent") && stack.getSize() >= 1)
                    {
                        if (stack.getSize() < 1) return false;
                        stack.subtract(1f);
                        agentSpent = true;
                        break;
                    }
                }                
                return agentSpent;
        }
}
