package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;
import java.util.Map;

public class Nex_AndradaOption extends ShowDefaultVisual {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		
		SectorEntityToken target = dialog.getInteractionTarget();
		if (target == null) return false;
		MarketAPI market = target.getMarket();
		if (market == null) return false;
		
		FactionAPI curr = PlayerFactionStore.getPlayerFaction();
		FactionAPI followers = Global.getSector().getFaction(ExerelinConstants.PLAYER_NPC_ID);
		
		SectorManager.transferMarket(market, followers, curr, true, true, null, 0);
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = -0.02f * market.getSize();
		impact.ensureAtBest = RepLevel.SUSPICIOUS;
		Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(
				CoreReputationPlugin.RepActions.CUSTOM, impact, null, null, true), curr.getId());
	
		return true;
	}
}