package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc.VarAndMemory;

public class Exerelin_ShowShipVisual extends BaseCommandPlugin {

    @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		
		VarAndMemory handle = params.get(0).getVarNameAndMemory(memoryMap);
		if (handle.memory.contains(handle.name)) {
			FleetMemberAPI member = (FleetMemberAPI) handle.memory.get(handle.name);
			dialog.getVisualPanel().showFleetMemberInfo(member);
			return true;
		}
		return false;
	}
}
