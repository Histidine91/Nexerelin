package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;


public class HasItem extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String itemId = params.get(0).getString(memoryMap);
        List<CargoStackAPI> stacks = Global.getSector().getPlayerFleet().getCargo().getStacksCopy();
        for (CargoStackAPI stack : stacks)
        {
            if (stack.isNull()) continue;
            if (stack.getCommodityId() != null && stack.getCommodityId().equals(itemId) && stack.getSize() >= 1) 
                return true;
        }
        return false;
    }
}
