package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import java.util.List;
import java.util.Map;

public class Nex_ResignCommission extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<com.fs.starfarer.api.util.Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		// -5 rep to negate join/leave infinite rep exploit
		ExerelinUtilsReputation.adjustPlayerReputation(Misc.getCommissionFaction(), null, -0.05f, null, dialog.getTextPanel());
		ExerelinUtilsFaction.revokeCommission();
		return true;
	}
}
