package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Nex_FactionDirectoryHelper {

	public static final int MAX_ENTRIES_PER_GROUPING = 8;
	public static final int PREFERED_ENTRIES_PER_GROUPING = 6;
	public static final int MAX_GROUPINGS = 7;
	
	protected static List<FactionListGrouping> ngcFactions = new ArrayList<>();
	protected static List<FactionListGrouping> ngcFactionsModOnly = new ArrayList<>();
	
	/**
	 * Groups the specified factions into alphabetically sorted lists.
	 * e.g. group 1 = Blackrock, Diable, Hegemony; group 2 = Persean League, pirates, Sindrian Diktat; group 3 = Tyrador
	 * Preferred group size is {@value PREFERED_ENTRIES_PER_GROUPING}, 
	 * will go up to {@value MAX_ENTRIES_PER_GROUPING} if groups of six would require more than {@value MAX_GROUPING} groups
	 * @param factionIds
	 * @return
	 */
	public static List<FactionListGrouping> getFactionGroupings(List<String> factionIds)
	{
		List<FactionAPI> factions = new ArrayList<>();
		for (String factionId : factionIds)
		{
			FactionAPI faction = Global.getSector().getFaction(factionId);
			if (faction != null)
				factions.add(faction);
		}
		
		// order by name
		Collections.sort(factions, new Comparator<FactionAPI>()
		{
			@Override
			public int compare(FactionAPI f1, FactionAPI f2)
			{
				String n1 = getFactionDisplayName(f1);
				String n2 = getFactionDisplayName(f2);
				return n1.compareTo(n2);
			}
		});
		
		// count the number of groupings we'll have
		int numGroupings = (int)Math.ceil(factions.size()/(double)PREFERED_ENTRIES_PER_GROUPING);
		if (numGroupings > MAX_GROUPINGS)
		{
			numGroupings = (int)Math.ceil(factions.size()/(double)MAX_ENTRIES_PER_GROUPING);
		}
		int factionsPerGrouping = (int)Math.ceil(factions.size()/(float)numGroupings);
		
		List<FactionListGrouping> list = new ArrayList<>();
		
		// populate groupings
		for (int groupingNum=0; groupingNum < numGroupings; groupingNum++)
		{
			if (factions.isEmpty())	break;
			
			List<FactionAPI> groupingFactions = new ArrayList<>();
			
			// add factions to grouping, up to allowed number
			int factionCount = 0;
			while (true)
			{
				if (factions.isEmpty())	break;
				FactionAPI faction = factions.remove(0);
				if (groupingFactions.contains(faction)) continue;
				
				groupingFactions.add(faction);
				factionCount++;
				
				if (factionCount >= factionsPerGrouping) break;
			}
			FactionListGrouping grouping = new FactionListGrouping(groupingFactions);
			list.add(grouping);
		}
		
		return list;
	}
	
	/**
	 * Gets alphabetically sorted groups of factions
	 * @param modOnly Mod-only factions
	 * @param excludeFollowers Exclude the followers faction ({@code player_npc})?
	 * @return
	 */
	public static List<FactionListGrouping> getNGCFactionGroupings(boolean modOnly, boolean excludeFollowers)
	{
		List<FactionListGrouping> list = ngcFactions;
		if (modOnly)
		{
			list = ngcFactionsModOnly;
		}
		
		if (list.isEmpty())
		{
			List<String> factions = ExerelinConfig.getModdedFactionsList(true);
			if (!modOnly) factions.addAll(ExerelinConfig.getBuiltInFactionsList(true));
			if (excludeFollowers)
				factions.remove(ExerelinConstants.PLAYER_NPC_ID);
			list = getFactionGroupings(factions);
		}
		
		return list;
	}
	
	public static String getFactionDisplayName(FactionAPI faction)
	{
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(faction.getId());
		if (conf != null && conf.directoryUseShortName)
			return Misc.ucFirst(faction.getDisplayName());
		return Misc.ucFirst(faction.getDisplayNameLong());
	}
	
	/**
	 * Gets the first three letters of the faction's long name
	 * @param faction
	 * @return
	 */
	protected static String getFactionInitial(FactionAPI faction)
	{
		String name = getFactionDisplayName(faction);
		int endIndex = 3;
		if (endIndex > name.length())
			endIndex = name.length();
		return name.substring(0, endIndex);
	}
	
	public static class FactionListGrouping
	{
		String first = "";	// initial of the first faction
		String last = "";	// initial of the last faction 
		public List<FactionAPI> factions;
		//public List<String> factionNames = new ArrayList<>();
		public String tooltip = "";
		
		public FactionListGrouping(List<FactionAPI> factions)
		{
			this.factions = factions;
			first = getFactionInitial(factions.get(0));
			if (factions.size() > 1)
				last = getFactionInitial(factions.get(factions.size() - 1));
			
			// generate tooltip
			String tooltip = "";
			for (int i = 0; i< factions.size(); i++)
			{
				tooltip = tooltip + "– " + getFactionDisplayName(factions.get(i));
				if (i < factions.size() - 1)
					tooltip += "\n";
			}
			this.tooltip = tooltip;
		}
		
		public String getGroupingRangeString()
		{
			if (last.isEmpty()) return first;
			return first + " – " + last;
		}
		
		public List<Color> getTooltipColors()
		{
			List<Color> list = new ArrayList<>();
			for (FactionAPI faction : factions)
			{
				list.add(faction.getBaseUIColor());
			}
			return list;
		}
		
		public List<String> getFactionNames()
		{
			List<String> list = new ArrayList<>();
			for (FactionAPI faction : factions)
			{
				list.add(getFactionDisplayName(faction));
			}
			return list;
		}
	}
}
