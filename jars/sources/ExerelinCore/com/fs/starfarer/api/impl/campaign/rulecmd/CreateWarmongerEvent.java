package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import java.util.List;
import java.util.Map;

public class CreateWarmongerEvent extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		
		String targetFactionId = params.get(0).getString(memoryMap);
		SectorManager.createWarmongerEvent(targetFactionId, dialog.getInteractionTarget());
		return true;
	}

}
