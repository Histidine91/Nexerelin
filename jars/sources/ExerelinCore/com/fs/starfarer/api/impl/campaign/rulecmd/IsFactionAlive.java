package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;


public class IsFactionAlive extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		
		if (ExerelinUtilsFaction.isExiInCorvus(factionId))
		{
			return true;	// so directory can be opened
		}
		
		return SectorManager.isFactionAlive(factionId);
	}
}