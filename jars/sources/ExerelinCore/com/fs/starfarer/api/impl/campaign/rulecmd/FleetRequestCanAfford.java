package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

public class FleetRequestCanAfford extends FleetRequestActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                
                SectorEntityToken target = dialog.getInteractionTarget();
                MarketAPI targetMarket = target.getMarket();
                if (targetMarket == null) return false;
                
                MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
                int fp = (int)memory.getFloat("$fleetRequestFP");
                int marines = (int)memory.getFloat("$fleetRequestMarines");                
                float moneyRequired = getMoneyRequiredForFleet(fp, marines);
                float moneyHave = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
                
                
                memory.set("$creditsRequiredForFleet", (int)moneyRequired, 0);
                memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$creditsHave", (int)moneyHave, 0);
                
                return moneyHave > moneyRequired;
        }
}
