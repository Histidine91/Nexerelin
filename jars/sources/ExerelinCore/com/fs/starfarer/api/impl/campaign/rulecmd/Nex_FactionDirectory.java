package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper.FactionListGrouping;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.input.Keyboard;

public class Nex_FactionDirectory extends BaseCommandPlugin {
	
	public static final String FACTION_GROUPS_KEY = "$nex_factionDirectoryGroups";
	public static final float GROUPS_CACHE_TIME = 0f;
	public static final String PRINT_FACTION_OPTION_PREFIX = "nex_printFactionMarkets_";
	static final int PREFIX_LENGTH = PRINT_FACTION_OPTION_PREFIX.length();
	
	static final HashMap<Integer, Color> colorByMarketSize = new HashMap<>();
	static {
		colorByMarketSize.put(2, Color.BLUE);
		colorByMarketSize.put(3, Color.CYAN);
		colorByMarketSize.put(4, Color.GREEN);
		colorByMarketSize.put(5, Color.YELLOW);
		colorByMarketSize.put(6, Color.ORANGE);
		colorByMarketSize.put(7, Color.PINK);
		colorByMarketSize.put(8, Color.RED);
		colorByMarketSize.put(9, Color.MAGENTA);
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
				memoryMap.get(MemKeys.LOCAL).set("$nex_dirFactionGroup", num);
				List<FactionListGrouping> groups = (List<FactionListGrouping>)(memoryMap.get(MemKeys.LOCAL).get(FACTION_GROUPS_KEY));
				FactionListGrouping group = groups.get(num - 1);
				for (FactionAPI faction : group.factions)
				{
					opts.addOption(Nex_FactionDirectoryHelper.getFactionDisplayName(faction), 
							PRINT_FACTION_OPTION_PREFIX + faction.getId());
				}
				
				opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_factionDirectoryMain");
				opts.setShortcut("nex_factionDirectoryMain", Keyboard.KEY_ESCAPE, false, false, false, false);
				
				ExerelinUtils.addDevModeDialogOptions(dialog);
				
				return true;
				
			case "print":
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				//if (option == null) throw new IllegalStateException("No $option set");
				String factionId = option.substring(PREFIX_LENGTH);
				printFactionMarkets(dialog.getTextPanel(), factionId);
		}
		
		return false;
	}
	
	/**
	 * Creates dialog options for the faction list subgroups
	 * @param dialog
	 */
	protected void listGroups(InteractionDialogAPI dialog, MemoryAPI memory)
	{
		boolean special = memory.getBoolean("$specialDialog");
		
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		List<FactionListGrouping> groups;
		
		if (memory.contains(FACTION_GROUPS_KEY))
		{
			groups = (List<FactionListGrouping>)memory.get(FACTION_GROUPS_KEY);
		}
		else
		{
			List<String> factionsForDirectory = getFactionsForDirectory(true);
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
		if (SectorManager.isFactionAlive(ExerelinConstants.PLAYER_NPC_ID))
			opts.addOption(Misc.ucFirst(Global.getSector().getFaction(ExerelinConstants.PLAYER_NPC_ID).getDisplayName()), 
					PRINT_FACTION_OPTION_PREFIX + ExerelinConstants.PLAYER_NPC_ID);
						
		if (!special)
			opts.addOption(Misc.ucFirst(StringHelper.getString("exerelin_alliances", "allianceListOption")), 
					"exerelinAllianceReport");
		
		String exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = "continueCutComm";		
		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), exitOpt);
		opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
		
		ExerelinUtils.addDevModeDialogOptions(dialog);
	}
	
	/**
	 * Gets the factions that should appear in the directory
	 * @param excludeFollowers
	 * @return 
	 */
	protected List<String> getFactionsForDirectory(boolean excludeFollowers)
	{
		Set<String> liveFactions = new HashSet<>(SectorManager.getLiveFactionIdsCopy());
		List<FactionAPI> allFactions = Global.getSector().getAllFactions();
		List<String> result = new ArrayList<>();
		
		if (ExerelinUtilsFaction.isExiInCorvus("exigency"))
			liveFactions.add("exigency");
		for (FactionAPI faction : allFactions)
		{
			String factionId = faction.getId();
			if (liveFactions.contains(factionId) || ExerelinUtilsFaction.hasAnyMarkets(factionId) || ExerelinUtilsFaction.isExiInCorvus(factionId))
				result.add(factionId);
		}
		if (excludeFollowers)
			result.remove(ExerelinConstants.PLAYER_NPC_ID);
		
		return result;
	}
	    
	/**
	 * Prints a formatted list of the specified faction's markets 
	 * @param text
	 * @param factionId
	 */
	public void printFactionMarkets(TextPanelAPI text, String factionId) 
	{
		boolean isExiInCorvus = ExerelinUtilsFaction.isExiInCorvus(factionId);
		List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(factionId);
		if (markets.isEmpty())
		{
			if (!isExiInCorvus) return;
		}

		Collections.sort(markets,new MarketComparator());
		//Collections.reverse(markets);
		FactionAPI faction = Global.getSector().getFaction(factionId);

		Color hl = Misc.getHighlightColor();

		int numMarkets = markets.size();
		if (isExiInCorvus) numMarkets++;

		String str = StringHelper.getString("exerelin_factions", "numMarkets");
		str = StringHelper.substituteFactionTokens(str, faction);
		str = StringHelper.substituteToken(str, "$numMarkets", numMarkets + "");
		
		// print total number of markets
		text.addParagraph(str);
		text.highlightInLastPara(hl, "" + numMarkets);
		text.setFontSmallInsignia();
		text.addParagraph("-----------------------------------------------------------------------------");

		boolean anyBase = false;
		
		// Tasserus
		if (isExiInCorvus)
		{
			String entry = StringHelper.getString("exerelin_markets", "marketDirectoryEntryNoLocation");
			entry = StringHelper.substituteToken(entry, "$market", "Tasserus");
			entry = StringHelper.substituteToken(entry, "$size", "??");
			text.addParagraph(entry);
			text.highlightInLastPara(hl, "Tasserus");
			text.highlightInLastPara(hl, "??");
		}

		for (MarketAPI market: markets)
		{
			String marketName = market.getName();
			LocationAPI loc = market.getContainingLocation();
			String locName = loc.getName();
			if (loc instanceof StarSystemAPI)
					locName = ((StarSystemAPI)loc).getBaseName();
			int size = market.getSize();
			Color sizeColor = Color.WHITE;
			if (colorByMarketSize.containsKey(size))
					sizeColor = colorByMarketSize.get(size);

			String entry = StringHelper.getString("exerelin_markets", "marketDirectoryEntry");
			entry = StringHelper.substituteToken(entry, "$market", marketName);
			entry = StringHelper.substituteToken(entry, "$location", locName);

			String sizeStr = size + "";
			
			// Has military base
			if (market.hasCondition(Conditions.MILITARY_BASE))
			{
				anyBase = true;
				sizeStr += ", " + StringHelper.getString("base");
			}
			
			// Cabal
			if (market.hasCondition("cabal_influence") 
					&& (market.getMemoryWithoutUpdate().getBoolean(ExerelinConstants.MEMORY_KEY_VISITED_BEFORE) || Global.getSettings().isDevMode()))
				sizeStr += ", " + StringHelper.getString("cabal");
			entry = StringHelper.substituteToken(entry, "$size", sizeStr);

			text.addParagraph(entry);
			//text.highlightInLastPara(hl, marketName);
			text.highlightInLastPara(sizeColor, "" + size);
		}
		if (anyBase)
		{
			//text.addParagraph("*" + StringHelper.getString("exerelin_markets", "hasBaseTip"));
		}
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();
	}
		
	/**
	 * Sorts markets by name of their star system, then by size
	 */
	public class MarketComparator implements Comparator<MarketAPI>
	{
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
	}
}