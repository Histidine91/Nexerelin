package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.events.RebellionEvent;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;


public class Nex_RebellionActions extends BaseCommandPlugin {
	
	public static final String OPT_PREFIX = "nex_supplyCounterInsurgency_deliver_";
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
		ExerelinUtilsCargo.addCommodityStockpile(market, commodity, amount);
		
		// handle credits payment
		float basePrice = market.getDemandPrice(commodity, amount, true);
		int payment = (int)(basePrice * getRewardMult(market.getFactionId()));
		cargo.getCredits().add(payment);
		AddRemoveCommodity.addCreditsGainText(payment, dialog.getTextPanel());
		
		// reputation
		float valueMult = 0.02f;
		switch (commodity)
		{
			case Commodities.SUPPLIES:
				valueMult = RebellionEvent.VALUE_SUPPLIES;
				break;
			case Commodities.HAND_WEAPONS:
				valueMult = RebellionEvent.VALUE_WEAPONS;
				break;
			case Commodities.MARINES:
				valueMult = RebellionEvent.VALUE_MARINES;
				break;
		}
		float points = amount * valueMult;
		float rep = points * 0.01f / RebellionEvent.getSizeMod(market) * DELIVERY_REP_MULT;
		
		NexUtilsReputation.adjustPlayerReputation(market.getFaction(), dialog.getInteractionTarget().getActivePerson(), 
				rep, rep * 1.5f, null, dialog.getTextPanel());
		
		// modify event strength
		CampaignEventPlugin event = Global.getSector().getEventManager().getOngoingEvent(
				new CampaignEventTarget(market), "nex_rebellion");
		if (event != null)
		{
			((RebellionEvent)event).modifyPoints(points, false);
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
				opts.addOption(getDeliverString(commodity, 20), OPT_PREFIX + commodity + ":20");
			if (have >= 100)
				opts.addOption(getDeliverString(commodity, 100), OPT_PREFIX + commodity + ":100");
			if (have >= 500)
				opts.addOption(getDeliverString(commodity, 500), OPT_PREFIX + commodity + ":500");
			if (have >= 1 && have != 20 && have != 100 && have != 500)
				opts.addOption(getDeliverString(commodity, have), OPT_PREFIX + commodity + ":all");
		}
		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_supplyCounterInsurgencyBack");
		ExerelinUtils.addDevModeDialogOptions(dialog, false);
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
}