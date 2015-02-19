package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import java.util.Arrays;


public class NGCDoesFactionExist extends BaseCommandPlugin {
	
	static List<String> factions = null;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		if(factions == null)
		{
			ExerelinSetupData data = ExerelinSetupData.getInstance();   
			factions = Arrays.asList(data.getPossibleFactions(false));
		}
		return factions.contains(factionId);
	}
}






