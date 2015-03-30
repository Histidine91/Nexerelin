package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.util.List;

public abstract class AgentActionBase extends BaseCommandPlugin {
        
        protected boolean useSpecialPerson(String typeId, int count) {
                SectorAPI sector = Global.getSector();

                CargoAPI cargo = sector.getPlayerFleet().getCargo();
                List<CargoStackAPI> stacks = cargo.getStacksCopy();
                boolean agentSpent = false;
                for (CargoStackAPI stack : stacks)
                {
                    if (stack.isNull()) continue;
                    if (stack.getCommodityId() != null && stack.getCommodityId().equals(typeId))
                    {
                        if (stack.getSize() < count) return false;
                        stack.subtract(count);
                        agentSpent = true;
                        // hax to prevent zero-stacks
                        if (stack.getSize() < 1)
                            cargo.removeEmptyStacks();
                        break;
                    }
                }                
                return agentSpent;
        }
}
