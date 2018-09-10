package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;

public class PrisonerSellSlave extends AgentActionBase {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
                SectorEntityToken target = dialog.getInteractionTarget();
                
                boolean superResult = useSpecialPerson("prisoner", 1);
                if (superResult == false)
                    return false;
                
                int level = Global.getSector().getPlayerPerson().getStats().getLevel();
		int ransomValue = (int)(ExerelinConfig.prisonerBaseSlaveValue + ExerelinConfig.prisonerSlaveValueIncrementPerLevel * (level - 1));
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(ransomValue);
                
                SectorManager.notifySlavesSold(target.getMarket(), 1);
                StatsTracker.getStatsTracker().notifySlavesSold(1);
                return true;
        }
}
