package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import java.util.List;
import java.util.Map;

public abstract class AgentActionBase extends BaseCommandPlugin {
	
	protected boolean useSpecialPerson(String typeId, int count) {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		List<CargoStackAPI> stacks = cargo.getStacksCopy();
		boolean agentSpent = false;
		for (CargoStackAPI stack : stacks)
		{
			if (stack.isNull()) continue;
			if (stack.getCommodityId() != null && stack.getCommodityId().equals(typeId))
			{
			if (stack.getSize() < count) return false;
			stack.subtract(count);
			agentSpent = true;
			// hax to prevent zero-stacks
			if (stack.getSize() < 1)
				cargo.removeEmptyStacks();
			break;
			}
		}
		return agentSpent;
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		MemoryAPI memory = memoryMap.get(MemKeys.MARKET);
		memory.set("$alertLevel", CovertOpsManager.getAlertLevel(dialog.getInteractionTarget().getMarket()), 0);
		return true;
	}
}
