package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.util.List;

public abstract class AgentActionBase extends BaseCommandPlugin {
        
        public boolean useAgent(String typeId, int count) {
                SectorAPI sector = Global.getSector();

                List<CargoStackAPI> stacks = sector.getPlayerFleet().getCargo().getStacksCopy();
                boolean agentSpent = false;
                for (CargoStackAPI stack : stacks)
                {
                    if (stack.isNull()) continue;
                    if (stack.getCommodityId() != null && stack.getCommodityId().equals(typeId))
                    {
                        if (stack.getSize() < count) return false;
                        stack.subtract(count);
                        agentSpent = true;
                        break;
                    }
                }                
                return agentSpent;
        }
}
