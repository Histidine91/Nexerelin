package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.VarAndMemory;
import java.util.List;
import java.util.Map;

public class Nex_IsNull extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		VarAndMemory var = params.get(0).getVarNameAndMemory(memoryMap);
		return !var.memory.contains(var.name);
	}
	
}
