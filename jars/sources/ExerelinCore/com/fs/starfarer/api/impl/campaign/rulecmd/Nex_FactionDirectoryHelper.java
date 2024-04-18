package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Nex_FactionDirectoryHelper {

	public static final int MAX_ENTRIES_PER_GROUPING = 8;
	public static final int PREFERED_ENTRIES_PER_GROUPING = 7;
	public static final int MAX_GROUPINGS = 7;
	
	public static final Comparator<FactionAPI> NAME_COMPARATOR = new Comparator<FactionAPI>()
	{
		@Override
		public int compare(FactionAPI f1, FactionAPI f2)
		{
			String n1 = Nex_FactionDirectoryHelper.getFactionDisplayName(f1);
			String n2 = Nex_FactionDirectoryHelper.getFactionDisplayName(f2);
			return n1.compareTo(n2);
		}
	};
	
	public static final Comparator<FactionAPI> NAME_COMPARATOR_PLAYER_FIRST = new Comparator<FactionAPI>()
	{
		@Override
		public int compare(FactionAPI f1, FactionAPI f2)
		{
			if (f1.isPlayerFaction()) return -1;
			if (f2.isPlayerFaction()) return 1;
			String n1 = Nex_FactionDirectoryHelper.getFactionDisplayName(f1);
			String n2 = Nex_FactionDirectoryHelper.getFactionDisplayName(f2);
			return n1.compareTo(n2);
		}
	};
	
	protected static List<FactionListGrouping> ngcFactions = new ArrayList<>();
	
	protected static Map<String, String> nameCache = new HashMap<>();
	
	/**
	 * Groups the specified factions into alphabetically sorted lists.
	 * e.g. group 1 = Blackrock, Diable, Hegemony; group 2 = Persean League, pirates, Sindrian Diktat; group 3 = Tyrador
	 * Preferred group size is {@value PREFERED_ENTRIES_PER_GROUPING}, 
	 * will go up to {@value MAX_ENTRIES_PER_GROUPING} if groups of 
	 * {@value PREFERED_ENTRIES_PER_GROUPING} would require more than 
	 * {@value MAX_GROUPING} groups
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
		Collections.sort(factions, NAME_COMPARATOR);
		
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
	 * @param excludePlayer Exclude the player faction?
	 * @return
	 */
	public static List<FactionListGrouping> getNGCFactionGroupings(boolean excludePlayer)
	{
		List<FactionListGrouping> list = ngcFactions;
		
		if (list.isEmpty())
		{
			List<String> factions = NexConfig.getFactions(false, true);
			if (excludePlayer)
				factions.remove(Factions.PLAYER);
			list.addAll(getFactionGroupings(factions));
			
			// cache results
			ngcFactions = list;
		}
		
		return list;
	}
	
	/**
	 * Gets the factions that should appear in the directory
	 * @param exclusion Factions to exclude from the result
	 * @param anyPlayable Include non-live factions
	 * @return 
	 */
	public static List<String> getFactionsForDirectory(Collection<String> exclusion, boolean anyPlayable)
	{
		Set<String> liveFactions = new HashSet<>(SectorManager.getLiveFactionIdsCopy());
		List<FactionAPI> allFactions = Global.getSector().getAllFactions();
		List<String> result = new ArrayList<>();
		
		if (NexUtilsFaction.isExiInCorvus("exigency"))
			liveFactions.add("exigency");
		liveFactions.add(Factions.INDEPENDENT);

		for (FactionAPI faction : allFactions)
		{
			String factionId = faction.getId();
			boolean allowed = anyPlayable && NexConfig.getFactionConfig(factionId).playableFaction;
			allowed = allowed || liveFactions.contains(factionId) || NexUtilsFaction.hasAnyMarkets(factionId, true) || NexUtilsFaction.isExiInCorvus(factionId);
			if (allowed)
				result.add(factionId);
		}
		if (exclusion != null)
		{
			for (String toExclude : exclusion)
				result.remove(toExclude);
		}
		
		return result;
	}
	
	public static List<String> getFactionsForDirectory(Collection<String> exclusion) {
		return getFactionsForDirectory(exclusion, false);
	}
	
	public static String getFactionDisplayName(String factionId)
	{
		return getFactionDisplayName(Global.getSector().getFaction(factionId));
	}
	
	public static String getFactionDisplayName(FactionAPI faction)
	{
		if (faction.isPlayerFaction())
			return faction.getDisplayName();
		
		String factionId = faction.getId();
		if (nameCache.containsKey(factionId))
			return nameCache.get(factionId);
		
		String name;
		NexFactionConfig conf = NexConfig.getFactionConfig(faction.getId());
		if (conf != null && conf.directoryUseShortName)
			name = Misc.ucFirst(faction.getDisplayName());
		else name = Misc.ucFirst(faction.getDisplayNameLong());
		
		nameCache.put(factionId, name);
		return name;
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
				tooltip = tooltip + "- " + getFactionDisplayName(factions.get(i));
				if (i < factions.size() - 1)
					tooltip += "\n";
			}
			this.tooltip = tooltip;
		}
		
		public String getGroupingRangeString()
		{
			if (last.isEmpty()) return first;
			return first + " - " + last;
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
