package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.List;
import java.util.Map;

public class Nex_IsPlayerSeenAndIdentified extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		FactionAPI faction = Global.getSector().getFaction(params.get(0).getString(memoryMap));
		return ExerelinUtilsFleet.isPlayerSeenAndIdentified(faction);
	}
}
