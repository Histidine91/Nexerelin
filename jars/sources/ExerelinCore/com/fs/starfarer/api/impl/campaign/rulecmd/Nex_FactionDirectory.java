package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper.FactionListGrouping;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ui.FieldOptionsScreenScript.FactionDirectoryDialog;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class Nex_FactionDirectory extends BaseCommandPlugin {
	
	public static final String FACTION_GROUPS_KEY = "$nex_factionDirectoryGroups";
	public static final float GROUPS_CACHE_TIME = 0f;
	public static final String PRINT_FACTION_OPTION_PREFIX = "nex_printFactionMarkets_";
	static final int PREFIX_LENGTH = PRINT_FACTION_OPTION_PREFIX.length();
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
	
	public static boolean hasHeavyIndustry(MarketAPI market) {
		for (Industry ind : market.getIndustries()) {
			if (ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY))
				return true;
		}
		return false;
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
				int num = (int)params.get(1).getFloat(memoryMap);
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
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				//if (option == null) throw new IllegalStateException("No $option set");
				String factionId = option.substring(PREFIX_LENGTH);
				printFactionMarkets(dialog, factionId);
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
			String optionId = "nex_factionDirectoryList" + groupNum;
			opts.addOption(group.getGroupingRangeString(),
					optionId, group.tooltip);
			opts.setTooltipHighlights(optionId, group.getFactionNames().toArray(new String[0]));
			opts.setTooltipHighlightColors(optionId, group.getTooltipColors().toArray(new Color[0]));
		}
		if (SectorManager.isFactionAlive(Factions.PLAYER))
			opts.addOption(Misc.ucFirst(Global.getSector().getPlayerFaction().getDisplayName()), 
					PRINT_FACTION_OPTION_PREFIX + Factions.PLAYER);
		
		Object exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = FactionDirectoryDialog.Menu.INIT;
		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), exitOpt);
		opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
		
		NexUtils.addDevModeDialogOptions(dialog);
	}
	
	    
	/**
	 * Prints a formatted list of the specified faction's markets
	 * @param dialog
	 * @param factionId
	 */
	public void printFactionMarkets(InteractionDialogAPI dialog, String factionId) 
	{
		try {
		float pad = 3, opad = 10;
		float imgSize = 36;
		float textWidth = 200;
		
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
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		
		TooltipMakerAPI tt = Nex_VisualCustomPanel.getTooltip();
		
		// header, total number of markets
		TooltipMakerAPI img = tt.beginImageWithText(faction.getLogo(), 40);
		img.setParaSmallInsignia();
		LabelAPI label = img.addPara(str, pad);
		label.setHighlight(Misc.ucFirst(faction.getDisplayName()), numMarkets + "", totalSize + "");
		label.setHighlightColors(faction.getBaseUIColor(), hl, hl);
		tt.addImageWithText(pad);
		
		//tt.addPara(StringHelper.HR, opad);

		boolean anyBase = false;
		
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
		
		int hidden = 0;
		for (MarketAPI market: markets)
		{
			if (market.isHidden() || market.getContainingLocation() == null) {
				if (market.getFaction().isPlayerFaction() || market.getFaction() == Misc.getCommissionFaction()) {
					
				} else {
					hidden++;
					continue;
				}
			}
			
			String marketName = market.getName();
			LocationAPI loc = market.getContainingLocation();
			String locName = NexUtilsAstro.getLocationName(loc, true);
			int size = market.getSize();
			Color sizeColor = getSizeColor(size);
			Color locColor = Misc.getTextColor();
			if (loc instanceof StarSystemAPI) {
				locColor = ((StarSystemAPI)loc).getStar().getSpec().getIconColor();
			}
			
			String sizeImg = Global.getSettings().getMarketConditionSpec("population_" + size).getIcon();
			
			CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(
					Nex_VisualCustomPanel.getPanel(), 
					null, Nex_VisualCustomPanel.PANEL_WIDTH, 40, 
					null, // add text ourselves later
					textWidth, pad, 
					sizeImg, imgSize, pad, null, false, null);
			CustomPanelAPI panel = gen.panel;
			
			// market name and its location
			TooltipMakerAPI text = (TooltipMakerAPI)gen.elements.get(1);
			text.addPara(marketName, sizeColor, pad);
			text.addPara(locName, locColor, pad);
			
			// icons showing stuff
			float imagesWidth = Nex_VisualCustomPanel.PANEL_WIDTH - textWidth - imgSize - 4 - 4;
			CustomPanelAPI featureImages = panel.createCustomPanel(imagesWidth, 40, null);
			
			List<String> images = new ArrayList<>();
			List<String> tooltipTexts = new ArrayList<>();
			
			// Has military base
			if (market.hasSubmarket(Submarkets.GENERIC_MILITARY))
			{
				images.add("graphics/icons/markets/military_base.png");
				tooltipTexts.add(Global.getSettings().getIndustrySpec(Industries.MILITARYBASE).getName());
				anyBase = true;
			}
			
			// Has heavy industry
			if (hasHeavyIndustry(market)) {
				images.add("graphics/icons/markets/autofactory.png");
				tooltipTexts.add(StringHelper.getString("heavyIndustry"));
			}
			
			if (market.hasIndustry(Industries.FUELPROD)) {
				images.add("graphics/icons/markets/am_fuel_facility.png");
				tooltipTexts.add(StringHelper.getString("fuelProduction"));
			}
			
			if (market.isFreePort()) {
				images.add("graphics/icons/markets/free_port.png");
				tooltipTexts.add(StringHelper.getString("freePort"));
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
			
			panel.addComponent(featureImages).rightOfTop(text, pad);
			
			tt.addCustom(panel, pad);
		}
		if (anyBase) {
			//text.addParagraph("*" + StringHelper.getString("exerelin_markets", "hasBaseTip"));
		}
		if (hidden > 0) {
			str = StringHelper.getStringAndSubstituteToken("exerelin_markets", "marketDirectoryHidden", "$num", hidden + "");
			tt.addPara(str, pad, hl, hidden + "");
		}
		
		Nex_VisualCustomPanel.addTooltipToPanel();
		
		
		} catch (Exception ex) {
			dialog.getTextPanel().addPara(ex.toString());
			Global.getLogger(this.getClass()).error("Failed to display faction directory", ex);
		}
	}
		
	/**
	 * Sorts markets by name of their star system, then by size
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
}