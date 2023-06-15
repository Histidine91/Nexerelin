package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.BuyColonyIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class Nex_BuyColony extends BaseCommandPlugin {
	
	public static final float SIZE_VALUE_MULT = 5000;
	public static final String MEMORY_KEY_NO_BUY = "$nex_unbuyable";
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		
		switch(arg)
		{
			case "init":
				//int value = getValue(market, market.isPlayerOwned(), true).getModifiedInt();
				//memoryMap.get(MemKeys.LOCAL).set("$nex_colonyPrice", value);
				break;
			case "canBuy":
				return canBuy(market);
			case "printCostAndProcessOptions":
				printCostAndProcessOptions(market, dialog, memoryMap.get(MemKeys.LOCAL));
				break;
			case "buy":
				buy(market, dialog);
				break;
			case "cede":
				setColonyPlayerOwned(market, false, dialog);
				BuyColonyIntel intel = BuyColonyIntel.getOngoingIntel(market);
				if (intel != null) intel.cancel(BuyColonyIntel.Status.QUIT);
				break;
			case "isPlayerOwned":
				return market.isPlayerOwned() && !market.getMemoryWithoutUpdate()
						.contains(ColonyManager.MEMORY_KEY_RULER_TEMP_OWNERSHIP);
		}
		return true;
	}
	
	protected void printCostAndProcessOptions(MarketAPI market, InteractionDialogAPI dialog,
			MemoryAPI mem) {
		int credits = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		MutableStat cost = getValue(market, false, true);
		int required = cost.getModifiedInt();
		if (required < 0) required = 0;
		boolean enough = credits >= required;
		String creditsStr = Misc.getWithDGS(credits);
		String requiredStr = Misc.getWithDGS(required);
		Color hl = Misc.getHighlightColor();
		
		String str = StringHelper.getString("nex_buyColony", "dialogCost");
		TextPanelAPI text = dialog.getTextPanel();
		text.setFontSmallInsignia();
		LabelAPI label = text.addPara(str, hl, requiredStr, creditsStr);
		label.setHighlight(requiredStr, creditsStr);
		label.setHighlightColors(hl, enough ? hl : Misc.getNegativeHighlightColor());
		
		TooltipMakerAPI info = text.beginTooltip();
		info.addStatModGrid(350, 80, 10, 0, cost, true, NexUtils.getStatModValueGetter(true, 0));
		text.addTooltip();
		
		text.setFontInsignia();
		
		if (!enough) {
			dialog.getOptionPanel().setEnabled("nex_buyColony_confirm", false);
		}
	}
	
	public static boolean canBuy(MarketAPI market) {
		if (market.isHidden()) return false;

		if (market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_NO_BUY))
			return false;
		
		if (Nex_IsFactionRuler.isRuler(market.getFactionId()))
			return true;
		
		int size = market.getSize();
		
		// always enable governorship for player-founded colonies (if they're size 3 or lower)
		if (NexUtilsMarket.getOriginalOwner(market) == null && size <= 3) 
		{
			return true;
		}
		// enable for markets freshly captured by player
		if (market.getMemoryWithoutUpdate().getBoolean(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER))
		{
			return true;
		}		
		
		FactionAPI faction = market.getFaction();
		if (size <= 3)
			return faction.isAtWorst(Factions.PLAYER, RepLevel.FRIENDLY);
		else if (size <= Global.getSettings().getInt("nex_governorship_maxSize"))
			return faction.isAtWorst(Factions.PLAYER, RepLevel.COOPERATIVE);
		
		return false;
	}
	
	public static MutableStat getValue(MarketAPI market, boolean isRefund, boolean includeBonus) 
	{
		MutableStat stat = new MutableStat(0);
		
		// (practically) free for size 3 player-founded colonies
		String origOwner = NexUtilsMarket.getOriginalOwner(market);
		if ((Factions.PLAYER.equals(origOwner)) && market.getSize() <= 3)
		{
			stat.modifyFlat("playerFounded", 1, StringHelper.getString("nex_buyColony", 
				"costFactorPlayerFounded", true));
			return stat;
		}
		
		float value = NexUtilsMarket.getMarketIndustryValue(market);
		float valueMult = Global.getSettings().getFloat("nex_governorship_industryValueMult");
		value *= valueMult;
		
		if (isRefund)
			value *= Global.getSettings().getFloat("industryRefundFraction");
		
		String desc = StringHelper.getString("nex_buyColony", "costFactorIndustry", true);
		float modifier = valueMult - 1; 
		desc = String.format(desc, StringHelper.toPercent(modifier));
		stat.modifyFlat("industry", value, desc);
		
		float income = NexUtilsMarket.getIncomeNetPresentValue(market, 6, isRefund ? 0.1f : 0);
		stat.modifyFlat("income", income, StringHelper.getString("nex_buyColony", 
				"costFactorIncome", true));
		
		if (includeBonus) {
			float sizeBonus = (float)(Math.pow(market.getSize(), 2) * SIZE_VALUE_MULT);
			float stabilityMult = (market.getStabilityValue() + 5)/15;
			stat.modifyFlat("sizeAndStability", (sizeBonus * stabilityMult), StringHelper.getString("nex_buyColony", 
				"costFactorSizeAndStability", true));
		}
		
		if (market.getMemoryWithoutUpdate().getBoolean(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED_BY_PLAYER))
		{
			stat.modifyMult("recentlyCapturedByPlayer", 0.5f, StringHelper.getString("nex_buyColony", 
				"costFactorRecentlyCapturedByPlayer", true));
		}
			
		return stat;
	}
	
	public static void buy(MarketAPI market, InteractionDialogAPI dialog) {
		int value = getValue(market, false, true).getModifiedInt();
		if (value < 0) value = 0;
		buy(market, value, dialog);
	}
	
	public static void buy(MarketAPI market, int value, InteractionDialogAPI dialog) {
		setColonyPlayerOwned(market, true, value, dialog);
		BuyColonyIntel intel = new BuyColonyIntel(market.getFactionId(), market);
		intel.init();
		if (dialog != null)
			Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
	}
	
	public static void setColonyPlayerOwned(MarketAPI market, boolean owned, InteractionDialogAPI dialog) {
		int value = getValue(market, false, true).getModifiedInt();
		if (value < 0) value = 0;
		setColonyPlayerOwned(market, owned, value, dialog);
	}
	
	public static void setColonyPlayerOwned(MarketAPI market, boolean owned, int value, InteractionDialogAPI dialog) 
	{
		market.setPlayerOwned(owned);
		if (owned)
			SectorManager.updateSubmarkets(market, Factions.PLAYER, Factions.PLAYER);
		else
			SectorManager.updateSubmarkets(market, market.getFactionId(), market.getFactionId());
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
		
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();		
		
		if (owned) {	// buying
			cargo.getCredits().subtract(value);
			// unlock storage
			if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
				((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE)
						.getPlugin()).setPlayerPaidToUnlock(true);
			}
		} else {	// ceding ("selling")
			//cargo.getCredits().add(value);
			//AddRemoveCommodity.addCreditsGainText(value, text);
		}
		if (owned && dialog != null) {
			TextPanelAPI text = dialog.getTextPanel();
			if (value != 0) AddRemoveCommodity.addCreditsLossText(value, text);
			((RuleBasedDialog)dialog.getPlugin()).updateMemory();
		}
		market.getMemoryWithoutUpdate().unset(ColonyManager.MEMORY_KEY_RULER_TEMP_OWNERSHIP);
		
	}
}