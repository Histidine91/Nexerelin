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
import static com.fs.starfarer.api.impl.campaign.rulecmd.AgentActionBase.STRING_CATEGORY;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
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
	public static final String[] COMMODITIES = {
		"agent", "saboteur", Commodities.MARINES, Commodities.HAND_WEAPONS, Commodities.SUPPLIES
	};
	public static final int MIN_REBLLION_POINTS = 25;
	public static final float STRENGTH_MULT = 1.25f;
	
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
					dialog.getOptionPanel().setEnabled("nex_instigateRebellionProceed", false);
				}
				/*
				else if (rebellionPoints < MIN_REBLLION_POINTS)
				{
					dialog.getOptionPanel().setEnabled("nex_instigateRebellionProceed", false);
				}
				*/				
				break;
			case "proceed":
				instigateRebellion(market, dialog);
				break;
		}
		
		return false;
	}
	
	protected void instigateRebellion(MarketAPI market, InteractionDialogAPI dialog)
	{	
		// generate result
		FactionAPI myFaction = PlayerFactionStore.getPlayerFaction();
		InstigateRebellion rebellion = new InstigateRebellion(market, myFaction, market.getFaction(), true, null);
		result = rebellion.rollSuccess();
		if (result == CovertActionResult.FAILURE)
			result = CovertActionResult.SUCCESS;
		if (result == CovertActionResult.FAILURE_DETECTED)
			result = CovertActionResult.SUCCESS_DETECTED;
		rebellion.setResult(result);
		rebellion.onSuccess();
		
		if (result != null)
		{
			String str = StringHelper.getString(STRING_CATEGORY, RESULT_STRINGS.get(result));
			str = StringHelper.substituteToken(str, "$agentType", StringHelper.getString(STRING_CATEGORY, agentType));
			String verb = StringHelper.getString(STRING_CATEGORY, "verbSuccess");
			Color color = Misc.getHighlightColor();
			if (!result.isSucessful())
			{
				verb = StringHelper.getString(STRING_CATEGORY, "verbFailed");
				color = Misc.getNegativeHighlightColor();
			}
			
			str = StringHelper.substituteToken(str, "$resultVerb", verb);
			
			TextPanelAPI text = dialog.getTextPanel();
			text.addParagraph(str);
			text.highlightInLastPara(color, verb);
		}
		
		RebellionEvent event = RebellionEvent.getOngoingEvent(market);
		if (event != null)
		{
			event.setRebelStrength(event.getRebelStrength() * STRENGTH_MULT);
		}
		
		// remove commodities from cargo
		CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
		for (String commodityId : COMMODITIES) {
			int neededAmount = getNeededCommodityAmount(market, commodityId);
			float haveAmount = playerCargo.getCommodityQuantity(commodityId);
			if (neededAmount > haveAmount)
				return;
			
			playerCargo.removeCommodity(commodityId, neededAmount);
			AddRemoveCommodity.addCommodityLossText(commodityId, neededAmount, dialog.getTextPanel());
		}
	}
	
	protected int getNeededCommodityAmount(MarketAPI market, String commodityId)
	{
		float mult = 0;
		switch (commodityId)
		{
			case "agent":
			case "saboteur":
				return market.getSize();
			case Commodities.MARINES:
				mult = 1.25f;
				break;
			case Commodities.HAND_WEAPONS:
				mult = 5f;
				break;
			case Commodities.SUPPLIES:
				mult = 2.5f;
				break;
		}
		return (int)Math.max(RebellionEvent.getSizeMod(market) * mult, 1);
	}
	
	protected ResourceCostPanelAPI makeCostPanel(TextPanelAPI text, Color color, Color color2) {
		ResourceCostPanelAPI cost = text.addCostPanel(Misc.ucFirst(StringHelper.getString("exerelin_agents", "resourcesNeeded")), 
				67, color, color2);
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		return cost;
	}
	
	protected void addCargoEntry(ResourceCostPanelAPI cost, String commodityId, int available, int needed) {
		Color curr = Global.getSector().getPlayerFaction().getColor();
		if (available < needed)
			curr = Misc.getNegativeHighlightColor();
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
		String header = StringHelper.getString("exerelin_markets", "marketDirectoryEntryNoLocation");
		header = StringHelper.substituteToken(header, "$market", market.getName());
		header = StringHelper.substituteToken(header, "$size", market.getSize() + "");
		text.addParagraph(header, market.getFaction().getBaseUIColor());
		
		ResourceCostPanelAPI cost = makeCostPanel(text, color, color2);
		int numEntries = 0;

		for (String commodityId : COMMODITIES) {
			if (numEntries >= 3) {
				cost = makeCostPanel(text, color, color2);
				numEntries = 0;
			}
			numEntries++;
			int neededAmount = getNeededCommodityAmount(market, commodityId);
			float haveAmount = playerCargo.getCommodityQuantity(commodityId);
			if (neededAmount > haveAmount)
				haveEnough = false;
			
			addCargoEntry(cost, commodityId, (int)haveAmount, neededAmount);
			cost.update();
		}
		float rebellionPoints = RebellionEventCreator.getRebellionPointsStatic(market);
		if (rebellionPoints < MIN_REBLLION_POINTS)
		{
			
		}
		
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
		
		return haveEnough;
	}
}
