package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;


public class IsFactionAlive extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		
		if (ExerelinUtilsFaction.isExiInCorvus(factionId))
		{
			return true;	// so directory can be opened
		}
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		return (conf.playableFaction && ExerelinUtilsFaction.getFactionMarkets(factionId).size() > 0);
		
		// don't use this, since a faction can be dead while still having markets if some of them are tagged as non-invadable
		//return SectorManager.isFactionAlive(factionId);
	}
}