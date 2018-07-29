package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.covertops.InstigateRebellion;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Map;

public class Nex_InstigateRebellion extends AgentActionBase {

	// needs: credits, agents, saboteurs, marines, hand weapons, supplies
	// easier iteration
	public static final String[] commodities = {
		"agent", "saboteur", Commodities.MARINES, Commodities.HAND_WEAPONS, Commodities.SUPPLIES
	};
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken entity = dialog.getInteractionTarget();
		if (entity == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = entity.getMarket();
		
		switch (arg)
		{
			case "init":
				boolean haveEnoughCommodities = printCargo(entity.getMarket(), memoryMap.get(MemKeys.MARKET), dialog.getTextPanel());
				float rebellionPoints = RebellionEventCreator.getRebellionPointsStatic(market);
				if (!haveEnoughCommodities)
				{
					// disable rules command
				}
				/*
				else if (rebellionPoints < 25)
				{
					
				}
				*/
				
				
				break;
			case "proceed":
				instigateRebellion(market);
				break;
		}
		
		return false;
	}
	
	protected void instigateRebellion(MarketAPI market)
	{
		FactionAPI myFaction = PlayerFactionStore.getPlayerFaction();
		InstigateRebellion rebellion = new InstigateRebellion(market, myFaction, market.getFaction(), true, null);
		result = CovertActionResult.SUCCESS_DETECTED;
		rebellion.setResult(result);
		rebellion.onSuccess();
	}
	
	protected float getNeededCommodityAmount(MarketAPI market, String commodityId)
	{
		float mult = 0;
		switch (commodityId)
		{
			case "agent":
			case "saboteur":
				return market.getSize();
			case Commodities.MARINES:
				mult = 0.5f;
				break;
			case Commodities.HAND_WEAPONS:
				mult = 2;
				break;
			case Commodities.SUPPLIES:
				mult = 1;
				break;
		}
		return (int)Math.min(RebellionEvent.getSizeMod(market) * mult, 1);
	}
	
	protected ResourceCostPanelAPI makeCostPanel(TextPanelAPI text, Color color, Color color2) {
		ResourceCostPanelAPI cost = text.addCostPanel("Resources needed (available)", 67, color, color2);
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		return cost;
	}
	
	protected void addCargoEntry(ResourceCostPanelAPI cost, String commodityId, int available, int needed) {
		Color curr = Global.getSector().getPlayerFaction().getColor();
		cost.addCost(commodityId, "" + needed + " (" + available + ")", curr);
	}
	
	@SuppressWarnings("unchecked")
	protected boolean printCargo(MarketAPI market, MemoryAPI mem, TextPanelAPI text) {
		text.setFontSmallInsignia();
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Color color = playerFaction.getColor();
		Color color2 = playerFaction.getDarkUIColor();
		CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
		
		boolean haveEnough = true;
		
		text.addParagraph(StringHelper.HR);
		//text.addParagraph("Cargo scanner: Cargo to drop (available)", color);

		ResourceCostPanelAPI cost = makeCostPanel(text, color, color2);
		int numEntries = 0;

		for (String commodityId : commodities) {
			if (numEntries >= 3) {
				cost = makeCostPanel(text, color, color2);
				numEntries = 0;
			}
			numEntries++;
			float neededAmount = getNeededCommodityAmount(market, commodityId);
			float haveAmount = playerCargo.getCommodityQuantity(commodityId);
			if (neededAmount > haveAmount)
				haveEnough = false;
			
			addCargoEntry(cost, commodityId, (int)neededAmount, (int)haveAmount / 2);
			cost.update();
		}
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
		
		return haveEnough;
	}
}
