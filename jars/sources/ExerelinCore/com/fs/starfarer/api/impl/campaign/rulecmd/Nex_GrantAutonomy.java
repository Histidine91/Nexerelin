package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ColonyManager;
import exerelin.utilities.StringHelper;


public class Nex_GrantAutonomy extends BaseCommandPlugin {
	
	public static final int REVOKE_UNREST = 2;
	
	// TODO	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		
		switch(arg)
		{
			case "init":
				memoryMap.get(MemKeys.LOCAL).set("$nex_revokeAutonomyUnrest", REVOKE_UNREST, 0);
				break;
			case "grant":
				grantAutonomy(market);
				break;
			case "revoke":
				revokeAutonomy(market);
				break;
			case "isAutonomous":
				return !market.isPlayerOwned();
		}
		return true;
	}
	
	public static void grantAutonomy(MarketAPI market) {
		market.setPlayerOwned(false);
		ColonyManager.getManager().checkGatheringPoint(market);
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
	}
	
	public static void revokeAutonomy(MarketAPI market) {
		market.setPlayerOwned(true);
		RecentUnrest.get(market, true).add(REVOKE_UNREST, 
				StringHelper.getString("nex_colonies", "autonomyRevoked"));
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
	}
}