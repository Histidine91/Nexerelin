package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.colony.DecivRevivalIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
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
	
		Form new autonomous colony
	*/
	
	public static final boolean DEBUG_MODE = false;
	public static final float EVENT_CHANCE = 0.4f;
	public static final float EVENT_TIME = 60;
	public static final float EVENT_TIME_LONG = 365;
	public static final int SUPPLIES_TO_COLONIZE = 100;
	public static final int MACHINERY_TO_COLONIZE = 50;
	public static final float COLONY_REP_VALUE = 0.07f;
	public static final String EVENT_TYPE_BARTER = "barter";
	public static final String EVENT_TYPE_REFUGEES = "refugees";
	public static final String EVENT_TYPE_BOMB = "bomb";
	public static final String EVENT_TYPE_FOUNDCOLONY = "foundColony";
	public static final String EVENT_TYPE_RAID = "raid";
	public static final String MEM_KEY_PREFIX = "$nex_decivEvent_";
	public static final String MEM_KEY_HAS_EVENT = MEM_KEY_PREFIX + "hasEvent";
	public static final String MEM_KEY_TYPE = MEM_KEY_PREFIX + "type";
	public static final String MEM_KEY_TAKE_ID = MEM_KEY_PREFIX + "takeItem";
	public static final String MEM_KEY_TAKE_NAME = MEM_KEY_PREFIX + "takeName";
	public static final String MEM_KEY_TAKE_COUNT = MEM_KEY_PREFIX + "takeCount";
	public static final String MEM_KEY_GIVE_ID = MEM_KEY_PREFIX + "giveItem";
	public static final String MEM_KEY_GIVE_COUNT = MEM_KEY_PREFIX + "giveCount";
	public static final String MEM_KEY_GIVE_NAME = MEM_KEY_PREFIX + "giveName";
	public static final String MEM_KEY_RAID_ITEM = MEM_KEY_PREFIX + "raidItem";
	public static final String MEM_KEY_FREE_SPACE = MEM_KEY_PREFIX + "freeSpace";
	public static final String MEM_KEY_AMOUNT_HAVE = MEM_KEY_PREFIX + "amountHave";
	public static final String MEM_KEY_VISITED_BEFORE = MEM_KEY_PREFIX + "visitedBefore";
	public static final String MEM_KEY_PERSON = MEM_KEY_PREFIX + "person";
	public static final String MEM_KEY_EVENT_SEEN_BEFORE = MEM_KEY_PREFIX + "seenBefore";

	public static final List<String> RAID_ITEMS = new ArrayList<>();
	public static final int RAID_ITEM_COUNT = 9;

	static {
		for (int i=0; i<RAID_ITEM_COUNT; i++) {
			RAID_ITEMS.add(StringHelper.getString("nex_decivEvent", "raidItem" + (i + 1)));
		}
	}
	
	public static final WeightedRandomPicker<String> givePicker = new WeightedRandomPicker<>();
	public static final WeightedRandomPicker<String> takePicker = new WeightedRandomPicker<>();
	public static final WeightedRandomPicker<String> givePickerHigh = new WeightedRandomPicker<>();
	
	public static Logger log = Global.getLogger(Nex_DecivEvent.class);

	protected SectorEntityToken entity;
	protected MemoryAPI memory;
	
	public static void reloadPickers() {
		givePicker.clear();
		givePicker.add(Commodities.SUPPLIES, 1f);
		givePicker.add(Commodities.FUEL, 1f);
		givePicker.add(Commodities.FOOD, 0.5f);
		givePicker.add(Commodities.RARE_ORE, 0.4f);
		givePicker.add(Commodities.RARE_METALS, 0.2f);
		givePicker.add(Commodities.CREW, 0.5f);
		
		takePicker.clear();
		takePicker.add(Commodities.SUPPLIES, 2f);
		takePicker.add(Commodities.METALS, 1f);
		takePicker.add(Commodities.HAND_WEAPONS, 1.25f);
		takePicker.add(Commodities.HEAVY_MACHINERY, 1.5f);
		takePicker.add(Commodities.FOOD, 1.25f);
		takePicker.add(Commodities.DOMESTIC_GOODS, 1.25f);

		givePickerHigh.clear();
		givePickerHigh.add(Commodities.SUPPLIES, 1f);
		givePickerHigh.add(Commodities.FUEL, 1f);
		givePickerHigh.add(Commodities.RARE_ORE, 0.5f);
		givePickerHigh.add(Commodities.RARE_METALS, 0.3f);
		givePickerHigh.add(Commodities.HAND_WEAPONS, 0.15f);
		givePickerHigh.add(Commodities.HEAVY_MACHINERY, 0.5f);
		givePickerHigh.add(Commodities.LUXURY_GOODS, 0.4f);
	}
	
	static {
		reloadPickers();
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		entity = dialog.getInteractionTarget();
		MarketAPI market = entity.getMarket();
		memory = market.getMemory();
		
		switch(arg)
		{
			case "hasEvent":
				return hasEvent(market);
			case "wasCivilized":
				return wasCivilized(market, memoryMap);
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
			case "end":
				end(dialog);
				break;
			case "addFoundColonyIntel":
				addFoundColonyIntel(dialog, memoryMap);
				break;
				
		}
		return true;
	}
	
	protected boolean wasCivilized(MarketAPI market, Map<String, MemoryAPI> memoryMap) {
		if (memoryMap.get(MemKeys.MARKET).getBoolean("$wasCivilized"))
			return true;
		if (market.getStarSystem() != null && NexUtilsAstro.isCoreSystem(market.getStarSystem()))
			return true;
		
		return false;
	}
	
	protected void greet(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		PersonAPI person = getPerson(market);
		dialog.getInteractionTarget().setActivePerson(person);
		dialog.getVisualPanel().showPersonInfo(person, true);
		
		// copy some variables to local memory (for text substitution)
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		if (memory.contains(MEM_KEY_GIVE_ID)) {
			String giveId = memory.getString(MEM_KEY_GIVE_ID);
			int giveNum = (int)memory.getLong(MEM_KEY_GIVE_COUNT);
			local.set(MEM_KEY_GIVE_NAME, StringHelper.getCommodityName(giveId), 0);
			local.set(MEM_KEY_GIVE_COUNT, giveNum, 0);
		}
		
		if (memory.contains(MEM_KEY_TAKE_ID)) {
			String takeId = memory.getString(MEM_KEY_TAKE_ID);
			int takeNum = (int)memory.getLong(MEM_KEY_TAKE_COUNT);
			local.set(MEM_KEY_TAKE_NAME, StringHelper.getCommodityName(takeId), 0);
			local.set(MEM_KEY_TAKE_COUNT, takeNum, 0);
		}
		
		FireBest.fire(null, dialog, memoryMap, "Nex_DecivEvent_Greeting");
		memory.set(MEM_KEY_VISITED_BEFORE, true);
	}
	
	protected void trade(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		FireBest.fire(null, dialog, memoryMap, "Nex_DecivEvent_OfferText");
		
		String type = memory.getString(MEM_KEY_TYPE);
		
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		opts.addOption(StringHelper.getString("accept", true), "nex_decivEvent_accept");
		opts.addOption(StringHelper.getString("decline", true), "nex_decivEvent_decline");
		NexUtils.addDevModeDialogOptions(dialog);
		if (type.equals(EVENT_TYPE_RAID)) return;	// don't display extra info

		boolean enough = makeCostPanel(dialog.getTextPanel());
		
		// require player to survey planet and explore any ruins first?
		if (type.equals(EVENT_TYPE_FOUNDCOLONY) && Misc.hasUnexploredRuins(dialog.getInteractionTarget().getMarket())) 
		{
			opts.setEnabled("nex_decivEvent_accept", false);
			opts.setTooltip("nex_decivEvent_accept", StringHelper.getString("nex_decivEvent", "exploreRuins"));
		}
		else if (!enough) {
			opts.setEnabled("nex_decivEvent_accept", false);
		}
	}
	
	protected void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		String giveId = memory.getString(MEM_KEY_GIVE_ID);
		int giveNum = (int)memory.getLong(MEM_KEY_GIVE_COUNT);
		String takeId = memory.getString(MEM_KEY_TAKE_ID);
		int takeNum = (int)memory.getLong(MEM_KEY_TAKE_COUNT);
		
		String type = memory.getString(MEM_KEY_TYPE);
		if (type.equals(EVENT_TYPE_FOUNDCOLONY)) {
			foundColony(dialog);
			return;
		}

		else if (type.equals(EVENT_TYPE_BOMB)) {
			Global.getSoundPlayer().playUISound("nex_sfx_deciv_bomb", 1, 1);
		}
		
		CargoAPI cargo = getPlayerCargo();
		cargo.addCommodity(giveId, giveNum);
		AddRemoveCommodity.addCommodityGainText(giveId, giveNum, dialog.getTextPanel());
		if (memory.contains(MEM_KEY_TAKE_ID)) {
			cargo.removeCommodity(takeId, takeNum);
			AddRemoveCommodity.addCommodityLossText(takeId, takeNum, dialog.getTextPanel());
		}
		dialog.getInteractionTarget().setActivePerson(null);
		
		setMem(MEM_KEY_HAS_EVENT, false);
	}
	
	protected void decline(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		dialog.getInteractionTarget().setActivePerson(null);
	}

	protected void end(InteractionDialogAPI dialog) {
		dialog.getInteractionTarget().setActivePerson(null);
		setMem(MEM_KEY_HAS_EVENT, false);
	}
	
	protected void foundColony(InteractionDialogAPI dialog) {
		CargoAPI cargo = getPlayerCargo();
		cargo.removeCommodity(Commodities.SUPPLIES, SUPPLIES_TO_COLONIZE);
		AddRemoveCommodity.addCommodityLossText(Commodities.SUPPLIES, SUPPLIES_TO_COLONIZE, dialog.getTextPanel());
		cargo.removeCommodity(Commodities.HEAVY_MACHINERY, MACHINERY_TO_COLONIZE);
		AddRemoveCommodity.addCommodityLossText(Commodities.HEAVY_MACHINERY, MACHINERY_TO_COLONIZE, dialog.getTextPanel());
		
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		PlanetAPI planet = market.getPlanetEntity();
		FactionAPI faction = Global.getSector().getFaction(Factions.INDEPENDENT);
		
		ColonyExpeditionIntel.createColonyStatic(market, planet, faction, true, false);
		
		PersonAPI person = getPerson(market);
		
		NexUtilsReputation.adjustPlayerReputation(faction, getPerson(market), COLONY_REP_VALUE,
						COLONY_REP_VALUE * 1.5f, null, dialog.getTextPanel());
		ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
		
		// set the person you talked to as the admin
		person.setPostId(Ranks.POST_ADMINISTRATOR);
		market.getCommDirectory().addPerson(person);
		market.addPerson(person);
		ip.addPerson(person);
		ip.getData(person).getLocation().setMarket(market);
		ip.checkOutPerson(person, "permanent_staff");
		market.setAdmin(person);
		
		DecivRevivalIntel intel = DecivRevivalIntel.getActiveIntel(planet);
		if (intel != null) intel.endImmediately();
		
		dialog.getInteractionTarget().setActivePerson(null);
		setMem(MEM_KEY_HAS_EVENT, false);
		((RuleBasedDialog)dialog.getPlugin()).updateMemory();
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
				header = StringHelper.substituteToken(header, "$commodity", StringHelper.getCommodityName(giveId));
				break;
			case EVENT_TYPE_FOUNDCOLONY:
				header = StringHelper.getString("nex_decivEvent", "costPanelHeaderColony");
				break;
			case EVENT_TYPE_BARTER:
			default:
				header = StringHelper.getString("nex_decivEvent", "costPanelHeader");
				header = StringHelper.substituteToken(header, "$commodity1", StringHelper.getCommodityName(giveId));
				header = StringHelper.substituteToken(header, "$commodity2", StringHelper.getCommodityName(takeId));
				break;
		}
		
		ResourceCostPanelAPI cost = text.addCostPanel(header, 72, color, color2);
		cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		
		if (type.equals(EVENT_TYPE_FOUNDCOLONY)) {
			int haveSupplies = (int)cargo.getSupplies();
			int haveMachinery = (int)cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
			boolean enoughS = haveSupplies >  SUPPLIES_TO_COLONIZE;
			cost.addCost(Commodities.SUPPLIES, SUPPLIES_TO_COLONIZE + " (" + haveSupplies + ")",
				enoughS ? color : Misc.getNegativeHighlightColor());
			boolean enoughM = haveMachinery > MACHINERY_TO_COLONIZE;
			cost.addCost(Commodities.HEAVY_MACHINERY, MACHINERY_TO_COLONIZE + " (" + haveMachinery + ")",
				enoughM ? color : Misc.getNegativeHighlightColor());
			
			cost.update();
			return enoughS && enoughM;
		}
		
		cost.addCost(giveId, "" + giveNum, color);
		if (type.equals(EVENT_TYPE_BARTER)) {
			int have = (int)cargo.getCommodityQuantity(takeId);
			enough = have >= takeNum;
			cost.addCost(takeId, takeNum + " (" + have + ")",
				enough ? color : Misc.getNegativeHighlightColor());
		} else if (isBomb) {
			enough = takeNum <= cargo.getFuel();
		}
		cost.update();
		
		// decide which string to use
		int freeSpace;
		String key;
		
		switch (giveId) {
			case Commodities.FUEL:
				freeSpace = cargo.getFreeFuelSpace();
				key = "freeSpaceFuel";
				break;
			case Commodities.CREW:
				freeSpace = cargo.getFreeCrewSpace();
				key = "freeSpaceCrew";
				break;
			default:
				freeSpace = (int)cargo.getSpaceLeft();
				key = "freeSpace";
				break;
		}
		
		String fuelStr = "" + (int)cargo.getFuel();
		String str = StringHelper.getString("nex_decivEvent", key);
		List<String> highlights = new ArrayList<>();
		List<Color> highlightColors = new ArrayList<>();
		highlights.add(freeSpace + "");
		highlightColors.add(hl);
		LabelAPI para;
		
		if (isBomb) {
			str += " " + StringHelper.getString("nex_decivEvent", "fuelAmount");
			highlights.add(fuelStr);
			highlightColors.add(enough ? hl : Misc.getNegativeHighlightColor());
			para = text.addPara(str, hl, freeSpace + "", fuelStr);
		}
		else
			para = text.addPara(str, hl, freeSpace + "");
		
		para.setHighlight(highlights.toArray(new String[]{}));
		para.setHighlightColors(highlightColors.toArray(new Color[]{}));
		
		return enough;
	}
	
	protected DecivRevivalIntel addFoundColonyIntel(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		SectorEntityToken planet = dialog.getInteractionTarget().getMarket().getPrimaryEntity();
		DecivRevivalIntel existing = DecivRevivalIntel.getActiveIntel(planet);
		if (existing != null) return null;
		
		DecivRevivalIntel intel = new DecivRevivalIntel(planet);
		Global.getSector().getIntelManager().addIntel(intel, false, dialog.getTextPanel());
		// Global.getSector().getIntelManager().addIntel(intel, true);
		// Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
		return intel;
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
		// existing deciv revival intel, set up found colony event		
		if (DecivRevivalIntel.getActiveIntel(market.getPrimaryEntity()) != null) {
			getPerson(market);
			setMem(MEM_KEY_TYPE, EVENT_TYPE_FOUNDCOLONY);
			setMem(MEM_KEY_HAS_EVENT, true);
			return;
		}		
		
		if (!DEBUG_MODE && Math.random() > EVENT_CHANCE) {
			setMem(MEM_KEY_HAS_EVENT, false);
			return;
		}
		WeightedRandomPicker<String> eventTypePicker = new WeightedRandomPicker<>();
		eventTypePicker.add(EVENT_TYPE_BARTER, 10);
		eventTypePicker.add(EVENT_TYPE_REFUGEES, 2.5f * market.getHazardValue());
		eventTypePicker.add(EVENT_TYPE_BOMB, 3f);
		eventTypePicker.add(EVENT_TYPE_RAID, 4f);
		if (market.getPlanetEntity() != null)
			eventTypePicker.add(EVENT_TYPE_FOUNDCOLONY, 1.25f/market.getHazardValue());
		
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
			case EVENT_TYPE_RAID:
				setupRaidEvent();
				break;
			case EVENT_TYPE_FOUNDCOLONY:
				setupColonyEvent();
				break;
		}
		getPerson(market);	// generate and save the person
		setMem(MEM_KEY_TYPE, type);
		setMem(MEM_KEY_HAS_EVENT, true);
		
		// for found colony event, make all memory keys last for 365 days
		// could be forever, but if the player never comes back, it'll be a waste of memory
		// if player comes back after 365 days, just regenerate the data
		if (type.equals(EVENT_TYPE_FOUNDCOLONY)) {
			setMem(MEM_KEY_TYPE, type, EVENT_TIME_LONG);
			setMem(MEM_KEY_HAS_EVENT, true, EVENT_TIME_LONG);
			setMem(MEM_KEY_PERSON, getPerson(market), EVENT_TIME_LONG);
		}
		
		memory.unset(MEM_KEY_EVENT_SEEN_BEFORE);
	}
	
	protected PersonAPI getPerson(MarketAPI market) {
		if (memory.contains(MEM_KEY_PERSON)) {
			return (PersonAPI)memory.get(MEM_KEY_PERSON);
		}
		
		String factionId = Factions.INDEPENDENT;
		if (memory.contains(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION))
			factionId = memory.getString(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION);
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
		
		/*
		log.info("Giving " + giveAmount + " " + commodityGive + " worth " + giveAmount * giveBasePrice 
				+ " credits (price mult " + (suppliesBasePrice/giveBasePrice) + ")");
		log.info("Taking " + takeAmount + " " + commodityTake + " worth " + takeAmount * takeBasePrice 
				+ " credits (price mult " + (suppliesBasePrice/takeBasePrice) + ")");
		*/
		
		setMem(MEM_KEY_GIVE_ID, commodityGive);
		setMem(MEM_KEY_GIVE_COUNT, giveAmount);
		setMem(MEM_KEY_TAKE_ID, commodityTake);
		setMem(MEM_KEY_TAKE_COUNT, takeAmount);
	}
	
	protected void setupBarterEvent() {
		int tries = 0;
		boolean lastTry = false;
		reloadPickers();
		while (!lastTry) {
			tries++;
			if (tries >= 10) lastTry = true;
			
			String commodityGive = givePicker.pick();
			String commodityTake = null;
			do {
				if (takePicker.isEmpty()) reloadPickers();
				commodityTake = takePicker.pickAndRemove();
				if (lastTry) break;
			} while (commodityTake == null || commodityTake.equals(commodityGive));

			//log.info("Picked commodities: " + commodityGive + ", " + commodityTake);
			
			CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
			if (cargo.getCommodityQuantity(commodityTake) <= 0 && !lastTry) 
			{
				// we don't have this commodity, try again
				continue;
			}
			float baseAmount = cargo.getMaxCapacity();
			baseAmount = Math.min(baseAmount, 1000);
			if (baseAmount < 100) baseAmount = 100;
			baseAmount = (int)(baseAmount/100) * 10;
			baseAmount *= 0.25f * MathUtils.getRandomNumberInRange(2, 8);

			setupCommodities(commodityGive, commodityTake, baseAmount, 1.25f, 1);
			if (cargo.getCommodityQuantity(commodityTake) < (int)memory.getLong(MEM_KEY_TAKE_COUNT) && !lastTry)
			{
				// we don't have enough of this commodity, try again
				continue;
			}
			log.info("Picked barter commodity after " + tries + " tries");
			break;
		}
	}
	
	protected void setupBombEvent() {
		String commodityTake = Commodities.FUEL;
		String commodityGive = null;
		do {
			if (givePickerHigh.isEmpty()) reloadPickers();
			commodityGive = givePickerHigh.pick();
		} while (commodityGive == null || commodityGive.equals(commodityTake)
				|| commodityGive.equals(Commodities.CREW));
		
		// we want 15-25 fuel, supplies are worth 4x as much as fuel
		float baseAmount = 10 + MathUtils.getRandomNumberInRange(1, 3) * 5;
		baseAmount /= 4;
		
		setupCommodities(commodityGive, commodityTake, baseAmount, 4f, 1);
	}

	protected void setupRaidEvent() {
		String commodityTake = Commodities.MARINES;
		String commodityGive = null;
		do {
			if (givePickerHigh.isEmpty()) reloadPickers();
			commodityGive = givePickerHigh.pick();
		} while (commodityGive == null || commodityGive.equals(Commodities.CREW));

		// 15-25 marines, more or less
		float baseAmount = 10 + MathUtils.getRandomNumberInRange(1, 3) * 5;

		MemoryAPI em = entity.getMemoryWithoutUpdate();
		em.set("$raidDifficulty", baseAmount * 2, EVENT_TIME);
		em.set("$raidGoBackTrigger", "Nex_DecivEvent_RaidCancelled", EVENT_TIME);
		em.set("$raidContinueTrigger", "Nex_DecivEvent_RaidFinishedB", EVENT_TIME);

		setupCommodities(commodityGive, commodityTake, baseAmount, 2f, 1);

		setMem(MEM_KEY_RAID_ITEM, NexUtils.getRandomListElement(RAID_ITEMS));
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
	
	protected void setupColonyEvent() {
		// no action needed?
	}
	
	protected void setMem(String key, Object value) {
		if (DEBUG_MODE)
			setMem(key, value, 0.1f);
		else	
			setMem(key, value, EVENT_TIME);
	}
	
	protected void setMem(String key, Object value, float time) {
		memory.set(key, value, time);
	}
}
