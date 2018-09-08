package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_NEXT_PAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions.OPTION_PREV_PAGE;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.NexTradeInfoUpdateEvent;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class Nex_GetPrices extends PaginatedOptions {
	
	public static final String GETPRICE_OPTION_PREFIX = "nex_getPrices_";
	public static final int OPT_LENGTH = GETPRICE_OPTION_PREFIX.length();
	public static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	protected static int lastPage = 0;
	protected static float lastDist = 10;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg)
		{
			case "listCommodities":
				lastPage = 0;
				init(ruleId, dialog, memoryMap);
				return true;
				
			case "print":
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				//if (option == null) throw new IllegalStateException("No $option set");
				init(ruleId, dialog, memoryMap);
				String commodityId = option.substring(OPT_LENGTH);
				printPriceInfo(commodityId);				
				
				return true;
				
			case "canUse":
				return canUse(dialog);
		}
		
		return false;
	}
	
	protected void init(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		//optionsPerPage = 6;
		super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
		listCommodities(dialog.getInteractionTarget().getMarket());
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "exerelinBaseCommanderMenuRepeat");
		dialog.getOptionPanel().setShortcut("exerelinBaseCommanderMenuRepeat", 
				Keyboard.KEY_ESCAPE, false, false, false, false);
		currPage = lastPage;
		showOptions();
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		lastDist = dialog.getOptionPanel().getSelectorValue("priceDistanceSelector");
		super.optionSelected(optionText, optionData);
		lastPage = currPage;
	}
	
	@Override
	public void showOptions() {
		dialog.getOptionPanel().clearOptions();  
  
		int maxPages = (int) Math.ceil((float)options.size() / (float)optionsPerPage);  
		if (currPage > maxPages - 1) currPage = maxPages - 1;  
		if (currPage < 0) currPage = 0;  

		int start = currPage * optionsPerPage;  
		for (int i = start; i < start + optionsPerPage; i++) {  
			if (i >= options.size()) {  
			   if (maxPages > 1) {
					//I don't like the spacer
					//dialog.getOptionPanel().addOption("", "spacer" + i);  
					//dialog.getOptionPanel().setEnabled("spacer" + i, false);  
			   }  
			} else {  
				PaginatedOption option = options.get(i);  
				dialog.getOptionPanel().addOption(option.text, option.id);  
			}  
		}

		if (maxPages > 1) {  
			dialog.getOptionPanel().addOption(getPreviousPageText(), OPTION_PREV_PAGE);  
			dialog.getOptionPanel().addOption(getNextPageText(), OPTION_NEXT_PAGE);  

			if (currPage >= maxPages - 1) {  
				dialog.getOptionPanel().setEnabled(OPTION_NEXT_PAGE, false);  
			}  
			if (currPage <= 0) {  
				dialog.getOptionPanel().setEnabled(OPTION_PREV_PAGE, false);  
			}  
		}
		
		dialog.getOptionPanel().addSelector("Max distance (LY)", "priceDistanceSelector", Color.YELLOW, 256, 
				48, 0, 20, ValueDisplayMode.VALUE, null);
		dialog.getOptionPanel().setSelectorValue("priceDistanceSelector", lastDist);

		for (PaginatedOption option : optionsAllPages) {  
		   dialog.getOptionPanel().addOption(option.text, option.id);
		}

		if (Global.getSettings().isDevMode()) {  
		   DevMenuOptions.addOptions(dialog);  
		}
	}
	
	protected void listCommodities(MarketAPI market)
	{
		List<String> commodities = Global.getSector().getEconomy().getAllCommodityIds();
		Collections.sort(commodities);
		for (String commodityId : commodities)
		{
			if (commodityId.equals("agent") || commodityId.equals("saboteur")) continue;
			CommodityOnMarketAPI data = market.getCommodityData(commodityId);
			if (data.isNonEcon()) continue;
			if (data.isPersonnel()) continue;
			String optId = GETPRICE_OPTION_PREFIX + commodityId;
			String text = data.getCommodity().getName();
			
			addOption(text, optId);
		}
	}
	
	protected boolean canUse(InteractionDialogAPI dialog)
	{
		if (dialog.getInteractionTarget().getActivePerson().getRelToPlayer().isAtWorst(RepLevel.FAVORABLE))
			return true;
		return (dialog.getInteractionTarget().getMarket().getFaction().isAtWorst(Factions.PLAYER, RepLevel.WELCOMING));
	}
	
	protected void printPriceInfo(String commodityId)
	{
		float searchRadius = lastDist;
		float minAmount = 1000;
		CampaignFleetAPI playerFlt = Global.getSector().getPlayerFleet();
		if (playerFlt != null)
			minAmount = playerFlt.getCargo().getMaxCapacity();
		
		List<PriceInfo> pricesHigh = new ArrayList<>();
		List<PriceInfo> pricesLow = new ArrayList<>();
		
		// iterate through all markets
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			// trade validity/distance check
			if (!ExerelinUtilsMarket.canTradeWithMarket(market))
				continue;
			float distance = Misc.getDistanceToPlayerLY(market.getContainingLocation().getLocation());
			if (distance > searchRadius)
				continue;
			
			CommodityOnMarketAPI data = market.getCommodityData(commodityId);

			// enough to buy/sell?
			float demand = data.getDemand().getDemandValue();
			float forSale = data.getAboveDemandStockpile();
			if (demand < minAmount && forSale < minAmount)
				continue;
			
			// price and tariff
			float demandPrice = market.getDemandPrice(commodityId, 100, true)/100;
			float supplyPrice = market.getSupplyPrice(commodityId, 100, true)/100;
			float tariff = market.getTariff().getModifiedValue();
			
			if (demand >= minAmount)
			{
				PriceInfo demandInfo = new PriceInfo(market, demandPrice, tariff, demand, distance);
				pricesHigh.add(demandInfo);
			}
			if (forSale >= minAmount)
			{
				PriceInfo supplyInfo = new PriceInfo(market, supplyPrice, tariff, forSale, distance);
				pricesLow.add(supplyInfo);
			}
		}
		
		// sort lists
		Collections.sort(pricesHigh, new PriceComparator());
		Collections.sort(pricesLow, new PriceComparator());
		Collections.reverse(pricesLow);
		
		printPriceInfo(commodityId, pricesHigh, true);
		printPriceInfo(commodityId, pricesLow, false);
		
		// update price event
		/*
		NexTradeInfoUpdateEvent event = NexTradeInfoUpdateEvent.getEvent();
		if (event != null)
		{
			List<MarketAPI> markets = new ArrayList<>();
			getFirstNMarkets(pricesHigh, markets, 3);
			getFirstNMarkets(pricesLow, markets, 3);
			event.reportEvent(dialog.getInteractionTarget().getMarket(), commodityId, markets);
		}
		*/
	}
	
	protected void getFirstNMarkets(List<PriceInfo> from, List<MarketAPI> to, int count)
	{
		int cap = Math.min(count, from.size());
		for (int i=0; i<cap; i++)
			to.add(from.get(i).market);
	}
	
	protected void printPriceInfo(String commodityId, List<PriceInfo> prices, boolean isPlayerSelling)
	{
		TextPanelAPI text = dialog.getTextPanel();
		text.addParagraph(isPlayerSelling ? getString("highPrices", true) : getString("lowPrices", true),
				Misc.getHighlightColor());
		text.setFontSmallInsignia();
        text.addParagraph(StringHelper.HR);
		int count = 0;
		Color hlColor = Misc.getHighlightColor();
		
		for (PriceInfo info : prices)
		{
			MarketAPI market = info.market;
			LocationAPI loc = market.getContainingLocation();
			String locName = loc.getName();
			if (loc instanceof StarSystemAPI)
					locName = ((StarSystemAPI)loc).getBaseName();
			int price = (int)Math.round(info.basePrice);
			int priceWithTariff = (int)Math.round(isPlayerSelling ? price * (1 - info.tariff) : price * (1 + info.tariff));
			String distStr = (int)Math.round(info.distance) + "";
			
			// market name and location
			String l1 = getString("marketDirectoryEntryForTrade", false);
			l1 = StringHelper.substituteToken(l1, "$market", market.getName());
			l1 = StringHelper.substituteToken(l1, "$location", locName);
			l1 = StringHelper.substituteFactionTokens(l1, market.getFaction());
			l1 = StringHelper.substituteToken(l1, "$distance", distStr);
			text.addParagraph(l1);
			text.highlightInLastPara(market.getName(), distStr);
			text.setHighlightColorsInLastPara(market.getFaction().getColor(), hlColor);
			
			// price
			String priceStr = Misc.getDGSCredits(price);
			String priceWithTariffStr = Misc.getDGSCredits(priceWithTariff);
			String l2 = "  " + getString("priceStr", true);
			l2 = StringHelper.substituteToken(l2, "$basePrice", priceStr);
			l2 = StringHelper.substituteToken(l2, "$priceWithTariff", priceWithTariffStr);
			//String l2 = "  Price: " + priceStr + " (" + priceWithTariffStr + " with tariff)";
			text.addParagraph(l2);
			text.highlightInLastPara(hlColor, priceStr, priceWithTariffStr);
			
			// amount
			String amount = Misc.getWithDGS((int)Misc.getRounded(info.amount));
			String l3 = "  " + (isPlayerSelling ? getString("demand", true) : getString("forSale", true)) 
					+ ": ~";
			l3 += amount;
			text.addParagraph(l3);
			text.highlightFirstInLastPara(amount + "", hlColor);
			
			count++;
			if (count >= 3) break;
		}
		
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
	}
	
	protected String getString(String id, boolean ucFirst)
	{
		String str = StringHelper.getString("exerelin_markets", id);
		if (ucFirst) str = Misc.ucFirst(str);
		return str;
	}
	
	
	@Override
	public String getPreviousPageText() {  
		return Misc.ucFirst(StringHelper.getString("previousPage"));  
	}
	
	@Override
	public String getNextPageText() {  
		return Misc.ucFirst(StringHelper.getString("nextPage"));  
	}
	
	public class PriceComparator implements Comparator<PriceInfo>
	{
		@Override
		public int compare(PriceInfo price1, PriceInfo price2) {

			float p1 = price1.basePrice;
			float p2 = price2.basePrice;

			if (p1 > p2) return -1;
			else if (p2 > p1) return 1;
			else return 0;
		}
	}
	
	public static class PriceInfo 
	{
		MarketAPI market;
		float basePrice;
		float tariff;
		float amount;
		float distance;
		
		public PriceInfo(MarketAPI market, float basePrice, float tariff, float amount, float distance)
		{
			this.market = market;
			this.basePrice = basePrice;
			this.tariff = tariff;
			this.amount = amount;
			this.distance = distance;
		}
	}
}