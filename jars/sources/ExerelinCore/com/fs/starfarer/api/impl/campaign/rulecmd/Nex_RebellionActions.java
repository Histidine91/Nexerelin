package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsCargo;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Nex_RebellionActions extends PaginatedOptions {
	
	public static final String OPT_PREFIX = "nex_supplyInsurgency_deliver_";
	public static final int PREFIX_LENGTH = OPT_PREFIX.length();
	
	public static final float DELIVERY_REP_MULT = 0f;
	
	public static final String[] COMMODITIES = new String[]{
		Commodities.SUPPLIES,
		Commodities.HAND_WEAPONS,
		Commodities.MARINES,
	};
	
	public static Map<String, String[]> tooltips = new HashMap<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		
		switch(arg)
		{
			case "main":
				initPaginatedDialog(dialog, memoryMap);
				addOptions(dialog);
				break;
			case "printPriceMult":
				//printPriceMult(dialog);
				break;
			case "deliver":
				deliverCommodities(dialog, memoryMap.get(MemKeys.LOCAL).getString("$option"));
				initPaginatedDialog(dialog, memoryMap);
				addOptions(dialog);
				break;
			case "isOngoing":
				return RebellionIntel.isOngoing(dialog.getInteractionTarget().getMarket());
			case "enoughRepToSupply":
				return enoughRepToSupply(dialog);
		}
		return true;
	}
	
	public void initPaginatedDialog(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		this.dialog = dialog;  
		this.memoryMap = memoryMap;
		originalPlugin = dialog.getPlugin();
		dialog.setPlugin(this); 
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
		if (commodity.equals(Commodities.MARINES)) {
			PlayerFleetPersonnelTracker.getInstance().getMarineData().numMayHaveChanged(cargo.getMarines(), true);
		}
		
		// handle credits payment
		if (!market.isPlayerOwned() && !market.getFaction().isPlayerFaction()) {
			float basePrice = market.getDemandPrice(commodity, amount, true);
			int payment = (int)(basePrice * getRewardMult(market.getFactionId()));
			cargo.getCredits().add(payment);
			AddRemoveCommodity.addCreditsGainText(payment, dialog.getTextPanel());
		}
		
		NexUtilsCargo.addCommodityStockpile(market, commodity, amount);
		Global.getSector().getEconomy().tripleStep();
		
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
		
		String optId = OPT_PREFIX + commodity + idSuffix;
		addOption(getDeliverString(commodity, amount), optId);
		
		if (!market.isPlayerOwned() && !market.getFaction().isPlayerFaction()) {
			float basePrice = market.getDemandPrice(commodity, amount, true);
			int payment = (int)(basePrice * getRewardMult(market.getFactionId()));
			String paymentStr = Misc.getDGSCredits(payment);
			String tooltip = StringHelper.getString("exerelin_misc", "counterInsurgencyCredits");
			tooltip = String.format(tooltip, paymentStr);
			String[] tooltipHolder = new String[] {tooltip, paymentStr};
			tooltips.put(optId, tooltipHolder);
		}
	}
	
	public void addTooltipsAndHotkeys(InteractionDialogAPI dialog) {
		OptionPanelAPI opts = dialog.getOptionPanel();
		Color color = Misc.getHighlightColor();
		for (Map.Entry<String, String[]> tmp : tooltips.entrySet()) {
			String id = tmp.getKey();
			String tooltip = tmp.getValue()[0];
			String hl = tmp.getValue()[1];
			opts.setTooltip(id, tooltip);
			opts.setTooltipHighlights(id, hl);
			opts.setTooltipHighlightColors(id, color);
		}
		
		// also hotkeys
		if (isRebel(dialog)) {
			dialog.getOptionPanel().setShortcut("nex_supplyInsurgencyBack", 
					Keyboard.KEY_ESCAPE, false, false, false, false);
		}
		else {
			dialog.getOptionPanel().setShortcut("nex_supplyCounterInsurgencyBack", 
					Keyboard.KEY_ESCAPE, false, false, false, false);
		}
	}
	
	public void addOptions(InteractionDialogAPI dialog) 
	{
		tooltips.clear();
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
			if (have >= 1 && have != 20 && have != 100 && have < 500)
				addDeliverOption(dialog, commodity, have, ":all");
		}
		if (isRebel(dialog)) {
			addOptionAllPages(Misc.ucFirst(StringHelper.getString("leave")), "nex_supplyInsurgencyBack");
		}
		else {
			addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "nex_supplyCounterInsurgencyBack");
		}
		showOptions();
		addTooltipsAndHotkeys(dialog);		
		NexUtils.addDevModeDialogOptions(dialog);
	}
	
	public String getDeliverString(String commodityId, int num)
	{
		String base = StringHelper.getString("exerelin_misc", "deliverCommodities");
		String commodityName = StringHelper.getCommodityName(commodityId).toLowerCase();
		
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
		if (NexUtilsFaction.isPirateFaction(faction.getId()))
			return true;
		return faction.isAtWorst(Factions.PLAYER, RepLevel.INHOSPITABLE);
	}
}