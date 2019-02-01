package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;

public class PrisonerRansom extends AgentActionBase {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		boolean superResult = useSpecialPerson("prisoner", 1);
		if (superResult == false)
			return false;
		
		int level = Global.getSector().getPlayerPerson().getStats().getLevel();
		int ransomValue = (int)(ExerelinConfig.prisonerBaseRansomValue + ExerelinConfig.prisonerRansomValueIncrementPerLevel * (level - 1));
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(ransomValue);
		StatsTracker.getStatsTracker().notifyPrisonersRansomed(1);
		return true;
	}
}
