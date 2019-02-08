package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;


public class Nex_DecivEvent extends BaseCommandPlugin {
	
	/*
	Event types:
		Barter
			Can offer supplies, fuel, food, transplutonics, transplutonic ore and crew
			Will take supplies, metals, heavy machinery, heavy weapons and food
	
		Crew ask to be taken away
	
		Exchange goods for bombing a target
	*/
	
	public static final float EVENT_CHANCE = 0.35f;
	public static final float EVENT_TIME = 60;
	public static final String EVENT_TYPE_BARTER = "barter";
	public static final String EVENT_TYPE_REFUGEES = "refugees";
	public static final String EVENT_TYPE_BOMB = "bomb";
	public static final String MEM_KEY_PREFIX = "$nex_decivEvent_";
	public static final String MEM_KEY_HAS_EVENT = MEM_KEY_PREFIX + "hasEvent";
	public static final String MEM_KEY_TYPE = MEM_KEY_PREFIX + "type";
	public static final String MEM_KEY_TAKE_ID = MEM_KEY_PREFIX + "takeItem";
	public static final String MEM_KEY_TAKE_NAME = MEM_KEY_PREFIX + "takeName";
	public static final String MEM_KEY_TAKE_COUNT = MEM_KEY_PREFIX + "takeCount";
	public static final String MEM_KEY_GIVE_ID = MEM_KEY_PREFIX + "giveItem";
	public static final String MEM_KEY_GIVE_COUNT = MEM_KEY_PREFIX + "giveCount";
	public static final String MEM_KEY_GIVE_NAME = MEM_KEY_PREFIX + "giveName";
	public static final String MEM_KEY_FREE_SPACE = MEM_KEY_PREFIX + "freeSpace";
	public static final String MEM_KEY_AMOUNT_HAVE = MEM_KEY_PREFIX + "amountHave";
	public static final String MEM_KEY_VISITED_BEFORE = MEM_KEY_PREFIX + "visitedBefore";
	public static final String MEM_KEY_PERSON = MEM_KEY_PREFIX + "person";
	public static final String MEM_KEY_EVENT_SEEN_BEFORE = MEM_KEY_PREFIX + "seenBefore";
	
	public static final WeightedRandomPicker<String> eventTypePicker = new WeightedRandomPicker<>();
	public static final WeightedRandomPicker<String> givePicker = new WeightedRandomPicker<>();
	public static final WeightedRandomPicker<String> takePicker = new WeightedRandomPicker<>();
	
	public static Logger log = Global.getLogger(Nex_DecivEvent.class);
	
	protected MemoryAPI memory;
	
	static {
		eventTypePicker.add(EVENT_TYPE_BARTER, 10);
		eventTypePicker.add(EVENT_TYPE_REFUGEES, 2.5f);
		eventTypePicker.add(EVENT_TYPE_BOMB, 2.5f);
		
		givePicker.add(Commodities.SUPPLIES, 1f);
		givePicker.add(Commodities.FUEL, 1f);
		givePicker.add(Commodities.FOOD, 0.5f);
		givePicker.add(Commodities.RARE_ORE, 0.4f);
		givePicker.add(Commodities.RARE_METALS, 0.2f);
		givePicker.add(Commodities.CREW, 0.5f);
		
		takePicker.add(Commodities.SUPPLIES, 2f);
		takePicker.add(Commodities.METALS, 1f);
		takePicker.add(Commodities.HAND_WEAPONS, 1.25f);
		takePicker.add(Commodities.HEAVY_MACHINERY, 1.5f);
		takePicker.add(Commodities.FOOD, 1.25f);
		takePicker.add(Commodities.DOMESTIC_GOODS, 1.25f);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		memory = market.getMemory();
		
		switch(arg)
		{
			case "hasEvent":
				return hasEvent(market);
			case "greet":
				greet(dialog, memoryMap);
				break;
			case "trade":
				trade(dialog, memoryMap);
				break;
			case "accept":
				accept(dialog, memoryMap);
				break;
			case "decline":
				decline(dialog, memoryMap);
				break;
		}
		return true;
	}
	
	protected void greet(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		PersonAPI person = getPerson(market);
		dialog.getInteractionTarget().setActivePerson(person);
		dialog.getVisualPanel().showPersonInfo(person, true);
		
		// copy some variables to local memory (for text substitution)
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		String giveId = memory.getString(MEM_KEY_GIVE_ID);
		int giveNum = (int)memory.getLong(MEM_KEY_GIVE_COUNT);
		local.set(MEM_KEY_GIVE_NAME, getCommodityName(giveId), 0);
		local.set(MEM_KEY_GIVE_COUNT, giveNum, 0);
		
		if (memory.contains(MEM_KEY_TAKE_ID)) {
			String takeId = memory.getString(MEM_KEY_TAKE_ID);
			int takeNum = (int)memory.getLong(MEM_KEY_TAKE_COUNT);
			local.set(MEM_KEY_TAKE_NAME, getCommodityName(takeId), 0);
			local.set(MEM_KEY_TAKE_COUNT, takeNum, 0);
		}
		
		FireBest.fire(null, dialog, memoryMap, "Nex_DecivEvent_Greeting");
		memory.set(MEM_KEY_VISITED_BEFORE, true);
	}
	
	protected void trade(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		FireBest.fire(null, dialog, memoryMap, "Nex_DecivEvent_OfferText");
		
		String type = memory.getString(MEM_KEY_TYPE);
		
		boolean enough = makeCostPanel(dialog.getTextPanel());			
		
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		opts.addOption(StringHelper.getString("accept", true), "nex_decivEvent_accept");
		opts.addOption(StringHelper.getString("decline", true), "nex_decivEvent_decline");
		if (!enough) {
			opts.setEnabled("nex_decivEvent_accept", false);
		}
		ExerelinUtils.addDevModeDialogOptions(dialog);
	}
	
	protected void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		String giveId = memory.getString(MEM_KEY_GIVE_ID);
		int giveNum = (int)memory.getLong(MEM_KEY_GIVE_COUNT);
		String takeId = memory.getString(MEM_KEY_TAKE_ID);
		int takeNum = (int)memory.getLong(MEM_KEY_TAKE_COUNT);
		
		String type = memory.getString(MEM_KEY_TYPE);
		if (type.equals(EVENT_TYPE_BOMB)) {
			Global.getSoundPlayer().playUISound("nex_sfx_deciv_bomb", 1, 1);
		}
			
		
		CargoAPI cargo = getPlayerCargo();
		cargo.addCommodity(giveId, giveNum);
		AddRemoveCommodity.addCommodityGainText(giveId, giveNum, dialog.getTextPanel());
		if (memory.contains(MEM_KEY_TAKE_ID)) {
			cargo.removeCommodity(takeId, takeNum);
			AddRemoveCommodity.addCommodityLossText(takeId, takeNum, dialog.getTextPanel());
		}
		
		setMem(MEM_KEY_HAS_EVENT, false);
	}
	
	protected void decline(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		// do nothing?
	}
	
	/**
	 * Makes the cost panel showing what the player will gain and lose.
	 * @param text
	 * @return
	 */
	protected boolean makeCostPanel(TextPanelAPI text) {
		String giveId = memory.getString(MEM_KEY_GIVE_ID);
		int giveNum = (int)memory.getLong(MEM_KEY_GIVE_COUNT);
		String takeId = memory.getString(MEM_KEY_TAKE_ID);
		int takeNum = (int)memory.getLong(MEM_KEY_TAKE_COUNT);
		
		CargoAPI cargo = getPlayerCargo();
		String type = memory.getString(MEM_KEY_TYPE);
		Color hl = Misc.getHighlightColor();
		
		if (type.equals(EVENT_TYPE_REFUGEES)) {
			int freeSpace = (int)cargo.getFreeCrewSpace();
			String freeSpaceStr = StringHelper.getString("nex_decivEvent", "freeSpaceCrew");
			text.addPara(freeSpaceStr, hl, freeSpace + "");
			return true;
		}
		
		//log.info("Preparing commodities: " + giveId + ", " + takeId);
		
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Color color = playerFaction.getColor();
		Color color2 = playerFaction.getDarkUIColor();
		
		boolean enough = true;
		boolean isBomb = type.equals(EVENT_TYPE_BOMB);
		
		String header;
		
		switch (type) {
			case EVENT_TYPE_BOMB:
				header = StringHelper.getString("nex_decivEvent", "costPanelHeaderBomb");
				header = StringHelper.substituteToken(header, "$commodity", getCommodityName(giveId));
				break;
			case EVENT_TYPE_BARTER:
			default:
				header = StringHelper.getString("nex_decivEvent", "costPanelHeader");
				header = StringHelper.substituteToken(header, "$commodity1", getCommodityName(giveId));
				header = StringHelper.substituteToken(header, "$commodity2", getCommodityName(takeId));
				break;
		}
		
		ResourceCostPanelAPI cost = text.addCostPanel(header, 72, color, color2);
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		
		cost.addCost(giveId, "" + giveNum, color);
		if (type.equals(EVENT_TYPE_BARTER)) {
			int have = (int)cargo.getCommodityQuantity(takeId);
			enough = have >= takeNum;
			cost.addCost(takeId, "" + takeNum + " (" + have + ")",
				enough ? color : Misc.getNegativeHighlightColor());
		} else if (isBomb) {
			enough = takeNum <= cargo.getFuel();
		}
		cost.update();
		
		int freeSpace = (int)cargo.getSpaceLeft();
		String fuel = "" + (int)cargo.getFuel();
		
		String key = isBomb ? "freeSpaceWithFuel" : "freeSpace";
		String freeSpaceStr = StringHelper.getString("nex_decivEvent", key);
		if (isBomb) {
			text.addPara(freeSpaceStr, hl, freeSpace + "", fuel);
			Highlights h = new Highlights();
			h.setColors(hl, enough? hl : Misc.getNegativeHighlightColor());
			h.setText(freeSpace + "", fuel);
			text.setHighlightsInLastPara(h);
		}
		else
			text.addPara(freeSpaceStr, hl, freeSpace + "");
			
		return enough;
	}
	
	protected CargoAPI getPlayerCargo() {
		return Global.getSector().getPlayerFleet().getCargo();
	}
	
	protected boolean hasEvent(MarketAPI market) {
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (!mem.contains(MEM_KEY_HAS_EVENT))
			generateEvent(market);
		return mem.getBoolean(MEM_KEY_HAS_EVENT);
	}
	
	protected void generateEvent(MarketAPI market) {
		if (Math.random() > EVENT_CHANCE) {
			setMem(MEM_KEY_HAS_EVENT, false);
			return;
		}
		
		String type = eventTypePicker.pick();
		switch (type) {
			case EVENT_TYPE_BARTER:
				setupBarterEvent();
				break;
			case EVENT_TYPE_REFUGEES:
				setupRefugeeEvent();
				break;
			case EVENT_TYPE_BOMB:
				setupBombEvent();
				break;
		}
		setMem(MEM_KEY_TYPE, type);
		getPerson(market);	// generate and save the person
		setMem(MEM_KEY_HAS_EVENT, true);
		memory.unset(MEM_KEY_EVENT_SEEN_BEFORE);
	}
	
	protected PersonAPI getPerson(MarketAPI market) {
		if (memory.contains(MEM_KEY_PERSON)) {
			return (PersonAPI)memory.get(MEM_KEY_PERSON);
		}
		
		String factionId = Factions.INDEPENDENT;
		if (memory.contains("$startingFactionId"))
			factionId = memory.getString("$startingFactionId");
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		PersonAPI person = faction.createRandomPerson();
		person.setFaction(Factions.INDEPENDENT);
		person.setRankId(Ranks.CITIZEN);
		person.setPostId(Ranks.POST_SHADY);
		
		setMem(MEM_KEY_PERSON, person);
		return person;
	}
	
	protected void setupCommodities(String commodityGive, String commodityTake, 
			float baseAmount, float giveMult, float takeMult) {
		// calculate commodity amounts
		// amounts are scaled to player cargo and base price of that commodity vs. base price of supplies
		// offered trade always has equal base cost on each side before mults
		float takeBasePrice = Global.getSettings().getCommoditySpec(commodityTake).getBasePrice();
		float giveBasePrice = Global.getSettings().getCommoditySpec(commodityGive).getBasePrice();
		float suppliesBasePrice = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getBasePrice();
		
		int giveAmount = Math.round(baseAmount * giveMult * suppliesBasePrice/giveBasePrice);
		int takeAmount = Math.round(baseAmount * takeMult * suppliesBasePrice/takeBasePrice);
		
		log.info("Giving " + giveAmount + " " + commodityGive + " worth " + giveAmount * giveBasePrice 
				+ " credits (price mult " + (suppliesBasePrice/giveBasePrice) + ")");
		log.info("Taking " + takeAmount + " " + commodityTake + " worth " + takeAmount * takeBasePrice 
				+ " credits (price mult " + (suppliesBasePrice/takeBasePrice) + ")");
		
		setMem(MEM_KEY_GIVE_ID, commodityGive);
		setMem(MEM_KEY_GIVE_COUNT, giveAmount);
		setMem(MEM_KEY_TAKE_ID, commodityTake);
		setMem(MEM_KEY_TAKE_COUNT, takeAmount);
	}
	
	protected void setupBarterEvent() {
		String commodityGive = givePicker.pick();
		String commodityTake = null;
		do {
			commodityTake = takePicker.pick();
		} while (commodityTake == null || commodityTake.equals(commodityGive));
		
		//log.info("Picked commodities: " + commodityGive + ", " + commodityTake);
		
		float baseAmount = Global.getSector().getPlayerFleet().getCargo().getMaxCapacity();
		baseAmount = Math.min(baseAmount, 1000);
		if (baseAmount < 100) baseAmount = 100;
		baseAmount = (int)(baseAmount/100) * 10;
		baseAmount *= 0.25f * MathUtils.getRandomNumberInRange(2, 8);
		
		setupCommodities(commodityGive, commodityTake, baseAmount, 1.25f, 1);
	}
	
	protected void setupBombEvent() {
		String commodityTake = Commodities.FUEL;
		String commodityGive = null;
		do {
			commodityGive = takePicker.pick();
		} while (commodityGive == null || commodityGive.equals(commodityTake)
				|| commodityGive.equals(Commodities.CREW));
		
		// we want 15-25 fuel, supplies are worth 4x as much as fuel
		float baseAmount = 10 + MathUtils.getRandomNumberInRange(1, 3) * 5;
		baseAmount /= 4;
		
		setupCommodities(commodityGive, commodityTake, baseAmount, 4f, 1);
	}
	
	protected void setupRefugeeEvent() {
		int num = Global.getSector().getPlayerFleet().getCargo().getFreeCrewSpace();
		num = Math.min(num, 200);
		if (num <= 10)
			num = 10;
		//num /= 5;
		num = MathUtils.getRandomNumberInRange(5, num);
		//num *= 5;
		
		setMem(MEM_KEY_GIVE_ID, Commodities.CREW);
		setMem(MEM_KEY_GIVE_COUNT, num);
	}
	
	protected String getCommodityName(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId).getName().toLowerCase(Locale.ROOT);
	}
	
	protected void setMem(String key, Object value) {
		setMem(key, value, EVENT_TIME);
	}
	
	protected void setMem(String key, Object value, float time) {
		memory.set(key, value, time);
	}
}
