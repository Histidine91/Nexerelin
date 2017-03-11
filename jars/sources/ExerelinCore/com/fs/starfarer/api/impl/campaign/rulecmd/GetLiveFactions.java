package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.HashSet;
import java.util.Set;


public class GetLiveFactions extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		Set<String> liveFactions = new HashSet<String>(SectorManager.getLiveFactionIdsCopy());
		if (ExerelinUtilsFaction.isExiInCorvus("exigency"))
			liveFactions.add("exigency");
		
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		for (String factionId: liveFactions)
		{
			memory.set("$liveFactions:" + factionId, true, 0);
		}
		
		return true;
	}
}