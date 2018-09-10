package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.StatsTracker;

public class SaboteurSabotageReserve extends AgentActionBase {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		boolean superResult = useSpecialPerson("saboteur", 1);
		if (superResult == false)
			return false;
		
		SectorAPI sector = Global.getSector();
		SectorEntityToken target = dialog.getInteractionTarget();
		MarketAPI market = target.getMarket();
		FactionAPI playerAlignedFaction = sector.getFaction(PlayerFactionStore.getPlayerFactionId());
		result = CovertOpsManager.saboteurSabotageReserve(market, playerAlignedFaction, market.getFaction(), true);
		StatsTracker.getStatsTracker().notifySaboteursUsed(1);
		return super.execute(ruleId, dialog, params, memoryMap);
	}
}
