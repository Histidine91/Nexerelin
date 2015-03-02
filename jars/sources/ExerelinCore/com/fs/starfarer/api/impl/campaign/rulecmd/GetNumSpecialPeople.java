package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;


public class GetNumSpecialPeople extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        int numAgents = 0;
        int numSaboteurs = 0;
        int numPrisoners = 0;
        
        List<CargoStackAPI> stacks = Global.getSector().getPlayerFleet().getCargo().getStacksCopy();
        for (CargoStackAPI stack : stacks)
        {
            if (stack.isNull()) continue;
            String commodityId = stack.getCommodityId();
            if (commodityId != null && stack.getSize() >= 1) 
            {
                if(commodityId.equals("agent"))
                    numAgents += (int)stack.getSize();
                else if(commodityId.equals("saboteur"))
                    numSaboteurs += (int)stack.getSize();
                else if(commodityId.equals("prisoner"))
                    numPrisoners += (int)stack.getSize();
            }
        }
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        memory.set("$numAgents", numAgents, 0);
        memory.set("$numSaboteurs", numSaboteurs, 0);
        memory.set("$numPrisoners", numPrisoners, 0);
        
        return true;
    }
}
