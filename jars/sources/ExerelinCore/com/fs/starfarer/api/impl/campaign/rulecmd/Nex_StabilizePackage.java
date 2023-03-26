package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.Morality;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.*;


public class Nex_StabilizePackage extends BaseCommandPlugin {
	
	public static final String MEMORY_KEY_RECENT = "$nex_stabilizePackage_cooldown";
	public static final int STABILIZE_INTERVAL = 60;
	public static final int RECENT_UNREST_DIVISOR_FOR_BONUS = 4;
	
	public static final List<String> COMMODITIES_RELIEF = new ArrayList<>(Arrays.asList(
			Commodities.SUPPLIES, Commodities.FOOD	//, Commodities.DOMESTIC_GOODS
	));
	public static final List<String> COMMODITIES_REPRESSION = new ArrayList<>(Arrays.asList(
			Commodities.SUPPLIES, Commodities.HAND_WEAPONS	//, Commodities.MARINES
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
					dialog.getOptionPanel().setEnabled("nex_stabilizePackage_proceed", false);
					dialog.getOptionPanel().setTooltip("nex_stabilizePackage_proceed", 
							StringHelper.getString("exerelin_markets", "stabilizeTooltipDisabled"));
				}
				break;
			case "proceed":
				deliver(market, dialog);
				break;
			case "enabled":
				setVars(market, memoryMap);
				return isAllowed(market);
		}
		return true;
	}
	
	protected void setVars(MarketAPI market, Map<String, MemoryAPI> memoryMap) {
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains(MEMORY_KEY_RECENT)) {
			float timeRemaining = mem.getExpire(MEMORY_KEY_RECENT);
			memoryMap.get(MemKeys.LOCAL).set("$nex_stabilizePackage_textCooldown", Misc.getAtLeastStringForDays((int)timeRemaining), 0);
		}
		if (mem.getBoolean(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT)) {
			float timeRemaining = mem.getExpire(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT);
			memoryMap.get(MemKeys.LOCAL).set("$nex_stabilizePackage_textInvadeCooldown", Misc.getAtLeastStringForDays((int)timeRemaining), 0);
		}
		mem.set("$nex_stabilizePackage_type", getStabilizeMethod(market), 0);
	}
	
	public static int getSizeMult(MarketAPI market)
	{
		return (int)(10 * Math.pow(2, market.getSize() - 3));
	}
	
	public static String getStabilizeMethod(MarketAPI market) {
		Morality moral = NexConfig.getFactionConfig(market.getFactionId()).morality;
		if (moral == Morality.AMORAL || moral == Morality.EVIL)
			return "repression";
		return "relief";
	}
	
	public static List<String> getCommodityTypes(MarketAPI market) {
		NexFactionConfig conf = NexConfig.getFactionConfig(market.getFactionId());
		if (conf.stabilizeCommodities != null)
			return conf.stabilizeCommodities;
		
		List<String> commodities = COMMODITIES_RELIEF;
		String type = getStabilizeMethod(market);
		if (type.equals("repression"))
			commodities = COMMODITIES_REPRESSION;
		
		return commodities;
	}
	
	protected static boolean isAllowed(MarketAPI market) {
		if (market.isPlayerOwned()) return false;
		if (market.getMemoryWithoutUpdate().getBoolean(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT))
			return false;
		
		int min = Math.min(NexConfig.stabilizePackageEffect, 2);
		float recentUnrest = RecentUnrest.getPenalty(market);
		return recentUnrest >= min || (recentUnrest >= 1 && market.getStabilityValue() == 0);
	}
	
	public static int getNeededCommodityAmount(MarketAPI market, String commodityId)
	{
		float mult = 0;
		switch (commodityId)
		{
			case Commodities.HAND_WEAPONS:
				mult = 0.5f;
				break;
			case Commodities.FOOD:
				mult = 5f;
				break;
			case Commodities.SUPPLIES:
				mult = 2f;
				break;
			default:
				float price = Global.getSettings().getCommoditySpec(commodityId).getBasePrice();
				mult = 100/price;
		}
		return (int)(getSizeMult(market) * mult);
	}
	
	public static int getNeededCredits(MarketAPI market) {
		return (int)(getSizeMult(market) * 500);
	}
	
	public static int getNominalCost(MarketAPI market) {
		int credits = getNeededCredits(market);
		List<String> commodities = getCommodityTypes(market);
		for (String commodity : commodities) {
			int count = getNeededCommodityAmount(market, commodity);
			credits += count * Global.getSettings().getCommoditySpec(commodity).getBasePrice();
		}
		return credits;
	}
	
	public static float getReputation(MarketAPI market) {
		return (float)Math.pow(2, market.getSize() - 3) * 0.01f;
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
		FactionAPI faction = market.getFaction();
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
		String rep = (int)(getReputation(market) * 100) + "";
		String cashStr = Misc.getDGSCredits(cash);
		sub.put("$market", market.getName());
		sub.put("$reputation", rep);
		sub.put("$theFaction", faction.getDisplayNameWithArticle());
		sub.put("$credits", cashStr);
		
		String header = StringHelper.getStringAndSubstituteTokens("exerelin_markets", "stabilizeHeader", sub);
		LabelAPI label = text.addParagraph(header);
		label.setHighlightColors(faction.getBaseUIColor(), Misc.getHighlightColor(), faction.getBaseUIColor(), 
				haveEnough ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
		label.setHighlight(market.getName(), rep, faction.getDisplayNameWithArticleWithoutArticle(), cashStr);

		List<String> commodities = getCommodityTypes(market);
		
		ResourceCostPanelAPI cost = makeCostPanel(text, color, color2);
		int numEntries = 0;
		for (String commodityId : commodities) {
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
		RecentUnrest ru = RecentUnrest.get(market);
		TextPanelAPI text = dialog.getTextPanel();
		if (ru != null) {
			text.setFontSmallInsignia();
			int before = ru.getPenalty();
			int baseEffect = NexConfig.stabilizePackageEffect;
			int fullEffect = getStabilizePackageEffect(market);
			ru.add(-fullEffect, 
					StringHelper.getString("exerelin_markets", "stabilizeRecentUnrestEntry"));
			int diff = before - ru.getPenalty();
			
			int bonus = diff - baseEffect;
			if (bonus > 0) {
				text.addPara(StringHelper.getStringAndSubstituteToken("exerelin_markets",
						"stabilizeEffectBonus", "$market", market.getName()),
						Misc.getPositiveHighlightColor(), "" + diff, "" + bonus);
			} else {
				text.addPara(StringHelper.getStringAndSubstituteToken("exerelin_markets",
						"stabilizeEffect", "$market", market.getName()), 
						Misc.getPositiveHighlightColor(), "" + diff);
			}
			
			text.setFontInsignia();
		}
		
		float rep = getReputation(market);
		NexUtilsReputation.adjustPlayerReputation(market.getFaction(), dialog.getInteractionTarget().getActivePerson(), 
				rep, rep * 1.5f, null, dialog.getTextPanel());
		
		// remove commodities from cargo
		CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
		List<String> commodities = getCommodityTypes(market);
		for (String commodityId : commodities) {
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
		
		market.getMemoryWithoutUpdate().set(MEMORY_KEY_RECENT, true, STABILIZE_INTERVAL);
	}
	
	public static int getStabilizePackageEffect(MarketAPI market) {
		int num = NexConfig.stabilizePackageEffect;
		num += RecentUnrest.getPenalty(market)/RECENT_UNREST_DIVISOR_FOR_BONUS;
		
		return num;
	}
}