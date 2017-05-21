package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class GetLiveFactions extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		addLiveFactionsToMemory(memory);
		
		return true;
	}
	
	public static void addLiveFactionsToMemory(MemoryAPI memory)
	{
		Set<String> liveFactions = new HashSet<>(SectorManager.getLiveFactionIdsCopy());
		List<FactionAPI> allFactions = Global.getSector().getAllFactions();
		if (ExerelinUtilsFaction.isExiInCorvus("exigency"))
			liveFactions.add("exigency");
		for (String factionId: liveFactions)
		{
			memory.set("$liveFactions:" + factionId, true, 0);
		}
		for (FactionAPI faction : allFactions)
		{
			String factionId = faction.getId();
			if (liveFactions.contains(factionId) || ExerelinUtilsFaction.hasAnyMarkets(factionId) || ExerelinUtilsFaction.isExiInCorvus(factionId))
				memory.set("$directoryFactions:" + factionId, true, 0);
		}
	}
}