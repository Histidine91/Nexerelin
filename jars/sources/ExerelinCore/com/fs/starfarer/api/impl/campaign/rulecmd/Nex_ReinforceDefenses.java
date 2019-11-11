package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.econ.ReinforcedDefenses;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class Nex_ReinforceDefenses extends BaseCommandPlugin {
	
	public static final List<String> COMMODITIES = new ArrayList<>(Arrays.asList(
			Commodities.SUPPLIES, Commodities.MARINES, Commodities.HAND_WEAPONS
	));
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		
		switch(arg)
		{
			case "init":
				boolean haveEnoughCommodities = displayCostAndCargo(market, memoryMap.get(MemKeys.MARKET), dialog.getTextPanel());
				if (!haveEnoughCommodities)	{
					// disable rules command
					dialog.getOptionPanel().setEnabled("nex_reinforceDefenses_proceed", false);
					dialog.getOptionPanel().setTooltip("nex_reinforceDefenses_proceed", 
							StringHelper.getString("exerelin_markets", "reinforceTooltipDisabled"));
				}
				break;
			case "proceed":
				deliver(market, dialog);
				break;
			case "enabled":
				return isEnabled(market);
		}
		return true;
	}
	
	protected boolean isEnabled(MarketAPI market) {
		//return market.isPlayerOwned() || market.getFaction().isPlayerFaction() 
		//		|| PlayerFactionStore.getPlayerFaction() == market.getFaction();
		return true;
	}
	
	protected int getSizeMult(MarketAPI market)
	{
		return (int)(10 * Math.pow(2, market.getSize() - 3));
	}
	
	protected int getNeededCommodityAmount(MarketAPI market, String commodityId)
	{
		float mult = 0;
		switch (commodityId)
		{
			case Commodities.HAND_WEAPONS:
				mult = 0.5f;
				break;
			case Commodities.MARINES:
				mult = 2.5f;
				break;
			case Commodities.SUPPLIES:
				mult = 2f;
				break;
		}
		return (int)(getSizeMult(market) * mult);
	}
	
	protected int getNeededCredits(MarketAPI market) {
		return (int)(getSizeMult(market) * 200);
	}
	
	protected ResourceCostPanelAPI makeCostPanel(TextPanelAPI text, Color color, Color color2) {
		ResourceCostPanelAPI cost = text.addCostPanel(Misc.ucFirst(StringHelper.getString("exerelin_misc", "resourcesNeeded")), 
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
	protected boolean displayCostAndCargo(MarketAPI market, MemoryAPI mem, TextPanelAPI text) {
		text.setFontSmallInsignia();
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Color color = playerFaction.getColor();
		Color color2 = playerFaction.getDarkUIColor();
		CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
		
		int cash = getNeededCredits(market);
		boolean haveEnough = true;
		if (cash > playerCargo.getCredits().get())
			haveEnough = false;
		
		text.addParagraph(StringHelper.HR);
		Map<String, String> sub = new HashMap<>();
		String cashStr = Misc.getDGSCredits(cash);
		String timeStr = Math.round(ReinforcedDefenses.BASE_TIME) + "";
		sub.put("$market", market.getName());
		sub.put("$time", timeStr);
		sub.put("$credits", cashStr);
		
		String header = StringHelper.getStringAndSubstituteTokens("exerelin_markets", "reinforcedDefensesHeader", sub);
		LabelAPI label = text.addParagraph(header);
		label.setHighlightColors(Misc.getHighlightColor(), haveEnough ? 
				Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
		label.setHighlight(timeStr, cashStr);
		
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
		
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
		
		return haveEnough;
	}
	
	protected void deliver(MarketAPI market, InteractionDialogAPI dialog)
	{
		MarketConditionAPI cond = market.getCondition("nex_reinforced_defenses");
		if (cond == null)
			market.addCondition("nex_reinforced_defenses");
		else
			((ReinforcedDefenses)cond.getPlugin()).extend(ReinforcedDefenses.BASE_TIME);
		
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
		
		int credits = getNeededCredits(market);
		if (credits != 0)
		{
			playerCargo.getCredits().subtract(credits);
			AddRemoveCommodity.addCreditsLossText(credits, dialog.getTextPanel());
		}
	}
}