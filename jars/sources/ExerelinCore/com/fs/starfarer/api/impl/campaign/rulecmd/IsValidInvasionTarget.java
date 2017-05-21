package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.List;
import java.util.Map;

public class IsValidInvasionTarget extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		SectorEntityToken entity = dialog.getInteractionTarget();
		if (entity == null) return false;
		MarketAPI market = entity.getMarket();
		if (market == null) return false;
		return ExerelinUtilsMarket.canBeInvaded(market);
	}
}
