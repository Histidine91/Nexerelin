package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.ExerelinSetupData;


public class NGCSetNumFactions extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		float num = params.get(0).getFloat(memoryMap);
		boolean absolute = params.get(1).getBoolean(memoryMap);
		if (absolute)
		{
			setupData.numStartFactions = (int)num;
		}
		else
		{
			List<String> availableFactions = setupData.getAvailableFactions();
			availableFactions.remove(ExerelinConstants.PLAYER_NPC_ID);
			setupData.numStartFactions = (int)(num * availableFactions.size() + 0.5f);
		}
		String numFactionsStr = setupData.numStartFactions + "";
		if (setupData.numStartFactions <= 0)
		{
			numFactionsStr = "all";
		}
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$numStartFactions", numFactionsStr, 0);
		
		Global.getLogger(this.getClass()).info("Number of starting factions: " + setupData.numStartFactions);
		return true;
	}
}