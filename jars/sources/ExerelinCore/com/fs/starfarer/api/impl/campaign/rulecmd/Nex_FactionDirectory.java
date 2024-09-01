package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper.FactionListGrouping;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ui.CustomPanelPluginWithInput;
import exerelin.campaign.ui.FieldOptionsScreenScript.FactionDirectoryDialog;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.*;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

public class Nex_FactionDirectory extends BaseCommandPlugin {
	
	public static final String FACTION_GROUPS_KEY = "$nex_factionDirectoryGroups";
	public static final float GROUPS_CACHE_TIME = 0f;
	public static final String LIST_FACTIONS_OPTION_PREFIX = "nex_factionDirectoryList_";
	protected static final int FACTIONS_PREFIX_LENGTH = LIST_FACTIONS_OPTION_PREFIX.length();
	public static final String PRINT_FACTION_OPTION_PREFIX = "nex_printFactionMarkets_";
	protected static final int FACTION_PREFIX_LENGTH = PRINT_FACTION_OPTION_PREFIX.length();
	public static final String PRINT_INDUSTRY_OPTION_PREFIX = "nex_printMarketsWithIndustries_";
	protected static final int INDUSTRY_PREFIX_LENGTH = PRINT_INDUSTRY_OPTION_PREFIX.length();
	
	public static final List<String> ARRAYLIST_PLAYERFACTION = Arrays.asList(new String[]{Factions.PLAYER});
	
	public static final HashMap<Integer, Color> colorByMarketSize = new HashMap<>();
	static {
		colorByMarketSize.put(1, Color.WHITE);
		colorByMarketSize.put(2, Color.BLUE);
		colorByMarketSize.put(3, Color.CYAN);
		colorByMarketSize.put(4, Color.GREEN);
		colorByMarketSize.put(5, Color.YELLOW);
		colorByMarketSize.put(6, Color.ORANGE);
		colorByMarketSize.put(7, Color.PINK);
		colorByMarketSize.put(8, Color.RED);
		colorByMarketSize.put(9, Color.MAGENTA);
		colorByMarketSize.put(10, Color.MAGENTA);
	}
	
	public static Color getSizeColor(int size) {
		Color color = Color.GRAY;
		if (colorByMarketSize.containsKey(size))
			color = colorByMarketSize.get(size);
		return color;
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg)
		{
			case "listGroups":
				listGroups(dialog, memoryMap.get(MemKeys.LOCAL));
				return true;
				
			case "listFactions":
				OptionPanelAPI opts = dialog.getOptionPanel();
				opts.clearOptions();
				int num = Integer.parseInt(memoryMap.get(MemKeys.LOCAL).getString("$option").substring(FACTIONS_PREFIX_LENGTH));
				//memoryMap.get(MemKeys.LOCAL).set("$nex_dirFactionGroup", num);
				List<FactionListGrouping> groups = (List<FactionListGrouping>)(memoryMap.get(MemKeys.LOCAL).get(FACTION_GROUPS_KEY));
				FactionListGrouping group = groups.get(num - 1);
				for (FactionAPI faction : group.factions)
				{
					opts.addOption(Nex_FactionDirectoryHelper.getFactionDisplayName(faction), 
							PRINT_FACTION_OPTION_PREFIX + faction.getId(), faction.getBaseUIColor(), null);
				}
				
				opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_factionDirectoryMain");
				opts.setShortcut("nex_factionDirectoryMain", Keyboard.KEY_ESCAPE, false, false, false, false);
				
				NexUtils.addDevModeDialogOptions(dialog);
				return true;
				
			case "print":
				{
					String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
					String factionId = option.substring(FACTION_PREFIX_LENGTH);
					printFactionMarkets(dialog, factionId);
					return true;
				}
				
			case "printIndustries":
				{
					String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
					String industryId = option.substring(INDUSTRY_PREFIX_LENGTH);
					printMarketsWithIndustry(dialog, industryId);
					return true;
				}

			case "printDisputed":
				{
					String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
					String factionId = option.substring(FACTION_PREFIX_LENGTH);
					printDisputedMarkets(dialog);
					return true;
				}
		}
		
		return false;
	}
	
	/**
	 * Creates dialog options for the faction list subgroups
	 * @param dialog
	 * @param memory
	 */
	public static void listGroups(InteractionDialogAPI dialog, MemoryAPI memory)
	{
		boolean special = memory.getBoolean("$nex_specialDialog");
		
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		List<FactionListGrouping> groups;
		
		if (memory.contains(FACTION_GROUPS_KEY))
		{
			groups = (List<FactionListGrouping>)memory.get(FACTION_GROUPS_KEY);
		}
		else
		{
			List<String> factionsForDirectory = Nex_FactionDirectoryHelper.getFactionsForDirectory(ARRAYLIST_PLAYERFACTION);
			groups = Nex_FactionDirectoryHelper.getFactionGroupings(factionsForDirectory);
			memory.set(FACTION_GROUPS_KEY, groups, GROUPS_CACHE_TIME);
		}

		int groupNum = 0;
		for (FactionListGrouping group : groups)
		{
			groupNum++;
			String optionId = "nex_factionDirectoryList_" + groupNum;
			opts.addOption(group.getGroupingRangeString(),
					optionId, group.tooltip);
			opts.setTooltipHighlights(optionId, group.getFactionNames().toArray(new String[0]));
			opts.setTooltipHighlightColors(optionId, group.getTooltipColors().toArray(new Color[0]));
		}
		if (SectorManager.isFactionAlive(Factions.PLAYER))
			opts.addOption(Misc.ucFirst(Global.getSector().getPlayerFaction().getDisplayName()), 
					PRINT_FACTION_OPTION_PREFIX + Factions.PLAYER);
		
		opts.addOption(StringHelper.getString("exerelin_markets", "marketDirectoryOptionIndustrySearch"), 
				"nex_factionDirectoryList_indSearch");
		opts.addOption(StringHelper.getString("exerelin_markets", "marketDirectoryOptionDisputedSearch"),
				"nex_printDisputedMarkets");
		
		Object exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = FactionDirectoryDialog.Menu.INIT;
		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), exitOpt);
		opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
		
		NexUtils.addDevModeDialogOptions(dialog);
	}
	
	protected void addIconWithTooltip(List<String> images, List<String> tooltipTexts, String image, String tooltip) 
	{
		images.add(image);
		tooltipTexts.add(tooltip);
	}
	
	/**
	 * Prints a formatted list of the specified faction's markets.
	 * @param dialog
	 * @param factionId
	 */
	public void printFactionMarkets(InteractionDialogAPI dialog, String factionId) 
	{		
		float pad = 3;
		
		boolean isExiInCorvus = NexUtilsFaction.isExiInCorvus(factionId);
		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(factionId);
		if (markets.isEmpty())
		{
			if (!isExiInCorvus) return;
		}

		Collections.sort(markets, MARKET_COMPARATOR);
		//Collections.reverse(markets);
		FactionAPI faction = Global.getSector().getFaction(factionId);

		Color hl = Misc.getHighlightColor();

		int numMarkets = markets.size();
		int totalSize = 0;
		for (MarketAPI market : markets) totalSize += market.getSize();
		if (isExiInCorvus) numMarkets++;
		
		//String factionName = NexUtilsFaction.getFactionShortName(faction);
		String str = StringHelper.getString("exerelin_factions", "numMarkets");
		str = StringHelper.substituteToken(str, "$Faction", Misc.ucFirst(faction.getDisplayName()));
		str = StringHelper.substituteToken(str, "$numMarkets", numMarkets + "");
		str = StringHelper.substituteToken(str, "$size", totalSize + "");
		
		float width = Nex_VisualCustomPanel.PANEL_WIDTH * 4/5;
		Nex_VisualCustomPanel.createPanel(dialog, true, width, Nex_VisualCustomPanel.PANEL_HEIGHT);
		
		TooltipMakerAPI tt = Nex_VisualCustomPanel.getTooltip();
		
		// header, total number of markets
		TooltipMakerAPI img = tt.beginImageWithText(faction.getLogo(), 40);
		img.setParaSmallInsignia();
		LabelAPI label = img.addPara(str, pad);
		label.setHighlight(Misc.ucFirst(faction.getDisplayName()), numMarkets + "", totalSize + "");
		label.setHighlightColors(faction.getBaseUIColor(), hl, hl);
		tt.addImageWithText(pad);
		
		//tt.addPara(StringHelper.HR, opad);
		
		// Tasserus
		/*
		if (isExiInCorvus)
		{
			String entry = StringHelper.getString("exerelin_markets", "marketDirectoryEntryNoLocation");
			entry = StringHelper.substituteToken(entry, "$market", "Tasserus");
			entry = StringHelper.substituteToken(entry, "$size", "??");
			tt.addParagraph(entry);
			tt.highlightInLastPara(hl, "Tasserus");
			tt.highlightInLastPara(hl, "??");
		}
		*/
		printMarkets(dialog, markets, width, true);
	}
	
	public boolean marketHasIndustry(MarketAPI market, String industryId) {
		if ("heavyIndustry".equals(industryId))
			return NexUtilsMarket.hasHeavyIndustry(market);
		else if ("military".equals(industryId))
			return Misc.isMilitary(market);
		return market.hasIndustry(industryId);
	}
	
	/**
	 * Prints a formatted list of the markets with the specified industry ID.
	 * @param dialog
	 * @param industryId
	 */
	public void printMarketsWithIndustry(InteractionDialogAPI dialog, String industryId) 
	{		
		float pad = 3;

		// get markets and sort them by distance from player
		List<Pair<MarketAPI, Float>> entries = new ArrayList<>();

		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (marketHasIndustry(market, industryId)) {
				float dist = Misc.getDistanceToPlayerLY(market.getContainingLocation().getLocation());
				Pair<MarketAPI, Float> entry = new Pair<>(market, dist);
				entries.add(entry);
			}
		}
		Collections.sort(entries, MARKET_COMPARATOR_DISTANCE);

		// convert to arraylist of markets
		List<MarketAPI> markets = new ArrayList<>();
		for (Pair<MarketAPI, Float> entry : entries) {
			markets.add(entry.one);
		}
				
		float width = Nex_VisualCustomPanel.PANEL_WIDTH * 4/5;
		Nex_VisualCustomPanel.createPanel(dialog, true, width, Nex_VisualCustomPanel.PANEL_HEIGHT);
		
		TooltipMakerAPI tt = Nex_VisualCustomPanel.getTooltip();
		
		// header, total number of markets
		String str = StringHelper.getString("exerelin_markets", "marketDirectoryHeaderIndustrySearch");
		tt.setParaFontVictor14();
		tt.addPara(str, pad, Misc.getGrayColor(), Misc.getTextColor(), industryId);
		
		tt.setParaSmallInsignia();
		str = StringHelper.getString("exerelin_markets", "marketDirectoryHeaderIndustrySearch2");
		tt.addPara(str, pad, Misc.getHighlightColor(), markets.size() + "");
		
		tt.setParaFontDefault();
		
		printMarkets(dialog, markets, width, false);
	}

	/**
	 * Prints a formatted list of the markets not under their original owners.
	 * @param dialog
	 */
	public void printDisputedMarkets(InteractionDialogAPI dialog)
	{
		float pad = 3;

		// get markets and sort them by distance from player
		Map<String, List<MarketAPI>> entries = new HashMap<>();
		List<FactionAPI> affectedFactions = new ArrayList<>();

		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			String owner = market.getFactionId();
			String orig = NexUtilsMarket.getOriginalOwner(market);
			if (orig == null) continue;
			if (!owner.equals(orig)) {
				List<MarketAPI> list = entries.get(orig);
				if (list == null) {
					list = new ArrayList<>();
					entries.put(orig, list);
					affectedFactions.add(Global.getSector().getFaction(orig));
				}
				list.add(market);
			}
		}
		Collections.sort(affectedFactions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);

		float width = Nex_VisualCustomPanel.PANEL_WIDTH * 4/5;
		Nex_VisualCustomPanel.createPanel(dialog, true, width, Nex_VisualCustomPanel.PANEL_HEIGHT);

		TooltipMakerAPI tt = Nex_VisualCustomPanel.getTooltip();

		// print each faction and its disputed markets
		Color hl = Misc.getHighlightColor();

		tt.setParaFontOrbitron();
		tt.addPara("%s faction(s) have revanchist claims", pad, hl, affectedFactions.size() + "");
		tt.setParaFontDefault();

		for (FactionAPI faction : affectedFactions) {
			String str = StringHelper.getString("exerelin_factions", "numMarkets");
			List<MarketAPI> markets = entries.get(faction.getId());
			int totalSize = 0;
			for (MarketAPI market : markets) totalSize += market.getSize();
			str = StringHelper.substituteToken(str, "$Faction", Misc.ucFirst(faction.getDisplayName()));
			str = StringHelper.substituteToken(str, "$numMarkets", markets.size() + "");
			str = StringHelper.substituteToken(str, "$size", totalSize + "");

			TooltipMakerAPI img = tt.beginImageWithText(faction.getLogo(), 40);
			img.setParaSmallInsignia();
			LabelAPI label = img.addPara(str, pad);
			label.setHighlight(Misc.ucFirst(faction.getDisplayName()), markets.size() + "", totalSize + "");
			label.setHighlightColors(faction.getBaseUIColor(), hl, hl);
			tt.addImageWithText(pad);
			printMarkets(dialog, markets, width, false);
		}
		if (affectedFactions.isEmpty())	Nex_VisualCustomPanel.addTooltipToPanel();	// if not, printMarkets will do it
	}
	
	/**
	 *Prints a formatted list of the provided markets.
	 * @param dialog
	 * @param markets
	 * @param width
	 * @param useSystemOwnerColor If true, market names use the system owner's 
	 * faction color, else they use their own faction color.
	 */
	public void printMarkets(final InteractionDialogAPI dialog, Collection<MarketAPI> markets, 
			float width, boolean useSystemOwnerColor) 
	{
		try {
		dialog.getVisualPanel().removeMapMarkerFromPersonInfo();
		
		float pad = 3;
		float imgSize = 36;
		float textWidth = 180;
		float buttonWidth = 48;
		
		LabelAPI label;
		String str;
		Color hl = Misc.getHighlightColor();
		TooltipMakerAPI tt = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		int hidden = 0;
		for (final MarketAPI market: markets)
		{
			if (market.isHidden() || market.getContainingLocation() == null) {
				//dialog.getTextPanel().addPara("Found hidden market " + market.getId());
				if (market.getFaction().isPlayerFaction() || market.getFaction() == Misc.getCommissionFaction()) {
					// do nothing
				} else if (market.getId().equals("ga_market") || market.getId().equals("IndEvo_academyMarket")) {
					// also do nothing
				} else {
					hidden++;
					continue;
				}
			}
			
			String marketName = market.getName();
			LocationAPI loc = market.getContainingLocation();
			String locName = loc.getNameWithNoType();
			int size = market.getSize();
			
			Color marketColor = market.getFaction().getBaseUIColor();
			Color locColor = marketColor;
			Color ownerColor = marketColor;
			
			FactionAPI owner = NexUtilsFaction.getSystemOwner(loc);
			String ownerName = "";
			if (owner != null) {
				ownerColor = owner.getBaseUIColor();
				ownerName = owner.getDisplayName();
			}
			if (useSystemOwnerColor) marketColor = ownerColor;
			
			if (loc instanceof StarSystemAPI) {
				PlanetAPI star = ((StarSystemAPI)loc).getStar();
				if (star != null) locColor = star.getSpec().getIconColor();
			}
			float dist = Misc.getDistanceToPlayerLY(loc.getLocation());
			String distStr = String.format("%.1f", dist);
			String locText = StringHelper.getStringAndSubstituteToken("exerelin_markets", "marketDirectoryEntryForPickerNoMarket", "$target", loc.getNameWithNoType());
			locText = StringHelper.substituteToken(locText, "$distance", distStr);
			
			String sizeImg = Global.getSettings().getMarketConditionSpec("population_" + size).getIcon();
			
			CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(
					Nex_VisualCustomPanel.getPanel(), 
					null, width, 40, 
					null, // add text ourselves later
					textWidth, pad, 
					sizeImg, imgSize, pad, null, false, null);
			CustomPanelAPI panel = gen.panel;
			
			// market name and its location
			TooltipMakerAPI text = (TooltipMakerAPI)gen.elements.get(1);
			text.addPara(marketName, marketColor, pad);
			label = text.addPara(locText, pad);
			label.setHighlight(locName, distStr);
			label.setHighlightColors(locColor, hl);
			
			// show button			
			TooltipMakerAPI buttonHolder = panel.createUIElement(buttonWidth, imgSize, false);
			ButtonAPI button = buttonHolder.addButton(StringHelper.getString("exerelin_markets", "marketDirectoryButtonShow", true), 
					"nex_showMarket_" + market.getId(), buttonWidth, 24, 3);

			plugin.addButton(new CustomPanelPluginWithInput.ButtonEntry(button, "nex_showMarket_" + market.getId()) {
				@Override
				public void onToggle() {
					dialog.getVisualPanel().removeMapMarkerFromPersonInfo();
					dialog.getVisualPanel().showMapMarker(market.getPrimaryEntity(), market.getName(), 
							market.getTextColorForFactionOrPlanet(), false, null, null, null);
					//dialog.getVisualPanel().showCore(CoreUITabId.MAP, market.getPrimaryEntity(), 
					//		new NexUtilsGUI.NullCoreInteractionListener());
				}
			});
			panel.addUIElement(buttonHolder).rightOfTop(text, pad);
						
			// icons showing stuff
			float imagesWidth = width - textWidth - imgSize - buttonWidth - 4 - 4;
			CustomPanelAPI featureImages = panel.createCustomPanel(imagesWidth, 40, null);
			
			List<String> images = new ArrayList<>();
			List<String> tooltipTexts = new ArrayList<>();
			
			// Has military base
			if (Misc.isMilitary(market))
			{
				addIconWithTooltip(images, tooltipTexts, 
						Global.getSettings().getSpriteName("marketConditions", "military_base"), 
						Global.getSettings().getIndustrySpec(Industries.MILITARYBASE).getName());
			}
			
			// Has heavy industry
			if (NexUtilsMarket.hasHeavyIndustry(market)) {
				addIconWithTooltip(images, tooltipTexts, 
						Global.getSettings().getSpriteName("marketConditions", "heavy_industry"), 
						Global.getSettings().getIndustrySpec(Industries.HEAVYINDUSTRY).getName());
			}
			
			if (market.hasIndustry(Industries.FUELPROD)) {
				addIconWithTooltip(images, tooltipTexts, 
						Global.getSettings().getSpriteName("marketConditions", "fuel_production"), 
						Global.getSettings().getIndustrySpec(Industries.FUELPROD).getName());
			}
			
			if (market.hasIndustry("IndEvo_dryDock")) {
				addIconWithTooltip(images, tooltipTexts, 
						Global.getSettings().getSpriteName("marketConditions", "repairdocks"), 
						Global.getSettings().getIndustrySpec("IndEvo_dryDock").getName());
			}
			
			if (market.hasIndustry("IndEvo_Academy")) {
				addIconWithTooltip(images, tooltipTexts, 
						Global.getSettings().getSpriteName("marketConditions", "academy"), 
						Global.getSettings().getIndustrySpec("IndEvo_Academy").getName());
			}
			
			if (market.isFreePort()) {
				addIconWithTooltip(images, tooltipTexts, "graphics/icons/markets/free_port.png", 
						StringHelper.getString("freePort"));
			}
			
			// Cabal
			if (market.hasCondition("cabal_influence") 
					&& (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_VISITED_BEFORE) || Global.getSettings().isDevMode())) {
				images.add("graphics/uw/icons/markets/uw_cabal_influence.png");
				tooltipTexts.add(StringHelper.getString("cabal"));
			}
			
			TooltipMakerAPI lastImageHolder = null;
			int index = 0;
			for (String image : images) {
				TooltipMakerAPI imageHolder = featureImages.createUIElement(imgSize, imgSize, false);
				imageHolder.addImage(image, imgSize, pad);
				PositionAPI pos = featureImages.addUIElement(imageHolder);
				if (lastImageHolder == null) pos.inTL(pad, pad);
				else pos.rightOfTop(lastImageHolder, pad);
				
				String tooltip = Misc.ucFirst(tooltipTexts.get(index));
				imageHolder.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(tooltip, 120), 
						TooltipMakerAPI.TooltipLocation.BELOW);
				
				index++;
				lastImageHolder = imageHolder;
			}
			
			panel.addComponent(featureImages).rightOfTop(buttonHolder, pad);			
			
			tt.addCustom(panel, pad);
		}
		if (hidden > 0) {
			str = StringHelper.getStringAndSubstituteToken("exerelin_markets", "marketDirectoryHidden", "$num", hidden + "");
			tt.addPara(str, pad, hl, hidden + "");
		}
		tt.addSpacer(pad);
		
		Nex_VisualCustomPanel.addTooltipToPanel();
		
		
		} catch (Exception ex) {
			dialog.getTextPanel().addPara(ex.toString());
			Global.getLogger(this.getClass()).error("Failed to display faction directory", ex);
		}
	}
		
	/**
	 * Sorts markets by name of their star system, then by size.
	 */
	public static final Comparator<MarketAPI> MARKET_COMPARATOR = new Comparator<MarketAPI>() {
		@Override
		public int compare(MarketAPI market1, MarketAPI market2) {

			String loc1 = market1.getContainingLocation().getName();
			String loc2 = market2.getContainingLocation().getName();

			if (loc1.compareToIgnoreCase(loc2) > 0) return 1;
			else if (loc2.compareToIgnoreCase(loc1) > 0) return -1;

			int size1 = market1.getSize();
			int size2 = market2.getSize();

			if (size1 > size2) return -1;
			else if (size2 > size1) return 1;
			else return 0;
		}
	};
	
	public static final Comparator<MarketAPI> MARKET_COMPARATOR_SIZE = new Comparator<MarketAPI>() {
		@Override
		public int compare(MarketAPI m1, MarketAPI m2) {
			if (m1.getSize() != m2.getSize())
				return Integer.compare(m2.getSize(), m1.getSize());
			return m1.getName().compareTo(m2.getName());
		}};

	public static final Comparator<Pair<MarketAPI, Float>> MARKET_COMPARATOR_DISTANCE = new Comparator<Pair<MarketAPI, Float>>() {
		@Override
		public int compare(Pair<MarketAPI, Float> e1, Pair<MarketAPI, Float> e2) {
			MarketAPI m1 = e1.one;
			MarketAPI m2 = e2.one;
			float d1 = e1.two;
			float d2 = e2.two;

			if (d1 != d2) {
				return Float.compare(d1, d2);
			}
			if (m1.getSize() != m2.getSize())
				return Integer.compare(m2.getSize(), m1.getSize());
			return m1.getName().compareTo(m2.getName());
		}};
}