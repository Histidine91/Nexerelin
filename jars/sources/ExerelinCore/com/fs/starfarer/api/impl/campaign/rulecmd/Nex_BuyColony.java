package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.intel.BuyColonyIntel;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class Nex_BuyColony extends BaseCommandPlugin {
	
	public static final float SIZE_VALUE_MULT = 2500;
	
	// TODO	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		BuyColonyIntel intel;
		
		switch(arg)
		{
			case "init":
				int value = getValue(market, market.isPlayerOwned(), true);
				memoryMap.get(MemKeys.LOCAL).set("$nex_colonyPrice", value);
				break;
			case "canBuy":
				return canBuy(market);
			case "printCostAndProcessOptions":
				printCostAndProcessOptions(market, dialog, memoryMap.get(MemKeys.LOCAL));
				break;
			case "buy":
				setColonyPlayerOwned(market, true, dialog);
				intel = new BuyColonyIntel(market.getFactionId(), market);
				intel.init();
				Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
				break;
			case "cede":
				setColonyPlayerOwned(market, false, dialog);
				intel = BuyColonyIntel.getOngoingIntel(market);
				if (intel != null) intel.cancel(BuyColonyIntel.Status.QUIT);
				break;
			case "isPlayerOwned":
				return !market.isPlayerOwned();
		}
		return true;
	}
	
	protected void printCostAndProcessOptions(MarketAPI market, InteractionDialogAPI dialog,
			MemoryAPI mem) {
		int credits = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		int required = (int)mem.getFloat("$nex_colonyPrice");
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
		text.setFontInsignia();
		
		if (!enough) {
			dialog.getOptionPanel().setEnabled("nex_buyColony_confirm", false);
		}
	}
	
	public static boolean canBuy(MarketAPI market) {
		FactionAPI faction = market.getFaction();
		int size = market.getSize();
		if (size <= 3)
			return faction.isAtWorst(Factions.PLAYER, RepLevel.FRIENDLY);
		else if (size == 4)
			return faction.isAtWorst(Factions.PLAYER, RepLevel.COOPERATIVE);
		
		return false;
	}
	
	public static int getValue(MarketAPI market, boolean isRefund, boolean includeBonus) 
	{
		float value = ExerelinUtilsMarket.getMarketIndustryValue(market);
		if (isRefund)
			value *= Global.getSettings().getFloat("industryRefundFraction");
		
		if (includeBonus) {
			float sizeBonus = (float)(Math.pow(market.getSize(), 2) * SIZE_VALUE_MULT);
			float stabilityMult = (market.getStabilityValue() + 5)/15;
			value += (sizeBonus * stabilityMult);
		}
			
		return (int)value;
	}
	
	
	// TODO: add intel
	public static void setColonyPlayerOwned(MarketAPI market, boolean owned, InteractionDialogAPI dialog) 
	{
		market.setPlayerOwned(owned);
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
		
		TextPanelAPI text = dialog.getTextPanel();
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		int value = getValue(market, !owned, true);
		if (owned) {	// buying
			cargo.getCredits().subtract(value);
			AddRemoveCommodity.addCreditsLossText(value, text);
			
			// unlock storage
			if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
				((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE)
						.getPlugin()).setPlayerPaidToUnlock(true);
			}
		} else {	// ceding ("selling")
			//cargo.getCredits().add(value);
			//AddRemoveCommodity.addCreditsGainText(value, text);
		}
		((RuleBasedDialog)dialog.getPlugin()).updateMemory();
	}
}