package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;


public class Nex_RebellionActions extends BaseCommandPlugin {
	
	public static final String OPT_PREFIX = "nex_supplyInsurgency_deliver_";
	public static final int PREFIX_LENGTH = OPT_PREFIX.length();
	
	public static final float DELIVERY_REP_MULT = 0f;
	
	public static final String[] COMMODITIES = new String[]{
		Commodities.SUPPLIES,
		Commodities.HAND_WEAPONS,
		Commodities.MARINES,
	};
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		
		switch(arg)
		{
			case "main":
				addOptions(dialog);
				break;
			case "printPriceMult":
				//printPriceMult(dialog);
				break;
			case "deliver":
				deliverCommodities(dialog, memoryMap.get(MemKeys.LOCAL).getString("$option"));
				addOptions(dialog);
				break;
			case "isOngoing":
				return RebellionIntel.isOngoing(dialog.getInteractionTarget().getMarket());
			case "enoughRepToSupply":
				return enoughRepToSupply(dialog);
		}
		return true;
	}
	
	public void printPriceMult(InteractionDialogAPI dialog)
	{
		TextPanelAPI text = dialog.getTextPanel();
		text.setFontSmallInsignia();
		//text.addParagraph(StringHelper.HR);
		//String str = StringHelper.getString("exerelin_misc", "counterInsurgencyRep");
		String str = StringHelper.getString("exerelin_misc", "counterInsurgencyRepFixed");
		FactionAPI faction = dialog.getInteractionTarget().getFaction();
		//str = StringHelper.substituteFactionTokens(str, faction);
		float mult = getRewardMult(faction.getId()) * 100;
		String multStr = String.format("%.0f", mult);
		str = StringHelper.substituteToken(str, "$mult", multStr + "%");

		text.addParagraph(str);
		text.highlightLastInLastPara(multStr, Misc.getHighlightColor());

		//text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
	}
	
	public void deliverCommodities(InteractionDialogAPI dialog, String option)
	{
		//if (option == null) throw new IllegalStateException("No $option set");
		String deliverArg = option.substring(PREFIX_LENGTH);
		String[] args = deliverArg.split(":");
		if (args.length < 2) throw new IllegalArgumentException("Malformed 'deliver commodities' argument");
		String commodity = args[0];
		String amountStr = args[1];
		
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		
		// determine commodities to be delivered
		float amount = 0;
		if (amountStr.equals("all"))
			amount = cargo.getCommodityQuantity(commodity);
		else
			amount = Float.parseFloat(args[1]);
		
		// transfer cargo
		cargo.removeCommodity(commodity, amount);
		AddRemoveCommodity.addCommodityLossText(commodity, (int)amount, dialog.getTextPanel());
		
		// handle credits payment
		if (!market.isPlayerOwned()) {
			float basePrice = market.getDemandPrice(commodity, amount, true);
			int payment = (int)(basePrice * getRewardMult(market.getFactionId()));
			cargo.getCredits().add(payment);
			AddRemoveCommodity.addCreditsGainText(payment, dialog.getTextPanel());
		}
		
		ExerelinUtilsCargo.addCommodityStockpile(market, commodity, amount);
		
		// impact on rebellion strength
		float valueMult = 0.02f;
		switch (commodity)
		{
			case Commodities.SUPPLIES:
				valueMult = RebellionIntel.VALUE_SUPPLIES;
				break;
			case Commodities.HAND_WEAPONS:
				valueMult = RebellionIntel.VALUE_WEAPONS;
				break;
			case Commodities.MARINES:
				valueMult = RebellionIntel.VALUE_MARINES;
				break;
		}
		float points = amount * valueMult;
		
		// reputation
		/*
		float rep = points * 0.01f / RebellionIntel.getSizeMod(market) * DELIVERY_REP_MULT;
		NexUtilsReputation.adjustPlayerReputation(market.getFaction(), dialog.getInteractionTarget().getActivePerson(), 
				rep, rep * 1.5f, null, dialog.getTextPanel());
		*/
		
		// modify event strength
		RebellionIntel event = RebellionIntel.getOngoingEvent(market);
		if (event != null)
		{
			event.modifyPoints(points, isRebel(dialog));
		}
	}
	
	public boolean isRebel(InteractionDialogAPI dialog) {
		return dialog.getInteractionTarget().getActivePerson().getMemoryWithoutUpdate()
					.getBoolean("$nex_rebel_representative");
	}
	
	public void addDeliverOption(InteractionDialogAPI dialog, String commodity, int amount, 
			String idSuffix) {
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		OptionPanelAPI opts = dialog.getOptionPanel();
		
		String optId = OPT_PREFIX + commodity + idSuffix;
		opts.addOption(getDeliverString(commodity, amount), optId);
		
		if (!market.isPlayerOwned()) {
			float basePrice = market.getDemandPrice(commodity, amount, true);
			int payment = (int)(basePrice * getRewardMult(market.getFactionId()));
			String paymentStr = Misc.getDGSCredits(payment);
			String tooltip = StringHelper.getString("exerelin_misc", "counterInsurgencyCredits");
			tooltip = String.format(tooltip, paymentStr);
			opts.setTooltip(optId, tooltip);
			opts.setTooltipHighlights(optId, paymentStr);
			opts.setTooltipHighlightColors(optId, Misc.getHighlightColor());
		}
	}
	
	public void addOptions(InteractionDialogAPI dialog) 
	{
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		for (String commodity : COMMODITIES)
		{
			int have = (int)cargo.getCommodityQuantity(commodity);
			if (have >= 20)
				addDeliverOption(dialog, commodity, 20, ":20");
			if (have >= 100)
				addDeliverOption(dialog, commodity, 100, ":100");
			if (have >= 500)
				addDeliverOption(dialog, commodity, 500, ":500");
			if (have >= 1 && have != 20 && have != 100 && have != 500)
				addDeliverOption(dialog, commodity, have, ":all");
		}
		if (isRebel(dialog))
			opts.addOption(Misc.ucFirst(StringHelper.getString("leave")), "nex_supplyInsurgencyBack");
		else
			opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_supplyCounterInsurgencyBack");
		
		ExerelinUtils.addDevModeDialogOptions(dialog);
	}
	
	public String getDeliverString(String commodityId, int num)
	{
		String base = StringHelper.getString("exerelin_misc", "deliverCommodities");
		String commodityName = Global.getSector().getEconomy().getCommoditySpec(commodityId).getName().toLowerCase();
		
		String result = StringHelper.substituteToken(base, "$num", num + "");
		result = StringHelper.substituteToken(result, "$commodity", commodityName);
		
		return result;
	}
	
	public float getRewardMult(String factionId)
	{
		//return 0.25f + 0.25f * Global.getSector().getPlayerFaction().getRelationship(factionId);
		return 1f;
	}
	
	public boolean enoughRepToSupply(InteractionDialogAPI dialog) {
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		RebellionIntel rebellion = RebellionIntel.getOngoingEvent(market);
		if (rebellion == null) return false;
		FactionAPI faction = isRebel(dialog) ? rebellion.getRebelFaction() : market.getFaction();
		if (ExerelinUtilsFaction.isPirateFaction(faction.getId()))
			return true;
		return faction.isAtWorst(Factions.PLAYER, RepLevel.INHOSPITABLE);
	}
}