package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper.FactionListGrouping;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class Nex_GetDisposition extends BaseCommandPlugin {
	
	public static final String FACTION_GROUPS_KEY = "$nex_factionDirectoryGroups";
	public static final float GROUPS_CACHE_TIME = 0f;
	public static final String SELECT_FACTION_PREFIX = "nex_getDisposition_";
	static final int PREFIX_LENGTH = SELECT_FACTION_PREFIX.length();
		
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg)
		{
			// list faction groupings
			case "listGroups":
				listGroups(dialog, memoryMap.get(MemKeys.LOCAL));
				return true;
				
			// list factions within a faction grouping
			case "listFactions":
				OptionPanelAPI opts = dialog.getOptionPanel();
				opts.clearOptions();
				int num = (int)params.get(1).getFloat(memoryMap);
				//memoryMap.get(MemKeys.LOCAL).set("$nex_dirFactionGroup", num);
				List<FactionListGrouping> groups = (List<FactionListGrouping>)(memoryMap.get(MemKeys.LOCAL).get(FACTION_GROUPS_KEY));
				FactionListGrouping group = groups.get(num - 1);
				for (FactionAPI faction : group.factions)
				{
					String optKey = SELECT_FACTION_PREFIX + faction.getId();
					opts.addOption(Nex_FactionDirectoryHelper.getFactionDisplayName(faction), optKey);
				}
				
				opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_getDispositionMain");
				opts.setShortcut("nex_getDispositionMain", Keyboard.KEY_ESCAPE, false, false, false, false);
				
				ExerelinUtils.addDevModeDialogOptions(dialog, false);
				
				return true;
			
			case "print":
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				//if (option == null) throw new IllegalStateException("No $option set");
				String factionId = option.substring(PREFIX_LENGTH);
				printDisposition(dialog, factionId);
				return true;
		}
		return false;
	}
	
	protected void printDispositionEntry(TextPanelAPI text, String title, float value)
	{
		String valueStr = String.format("%.2f", value);
		text.addParagraph(title + ": " + valueStr);
		Color color = Misc.getHighlightColor();
		if (value < 0) color = Misc.getNegativeHighlightColor();
		else if (value > 0) color = Misc.getPositiveHighlightColor();
		
		text.highlightLastInLastPara(valueStr, color);
	}
		
	public void printDisposition(InteractionDialogAPI dialog, String otherFactionId)
	{
		String factionId = dialog.getInteractionTarget().getFaction().getId();
		FactionAPI target = Global.getSector().getFaction(otherFactionId);
		FactionAPI faction = Global.getSector().getFaction(factionId);
		TextPanelAPI text = dialog.getTextPanel();
		
		DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(factionId);
		if (brain == null)
		{
			text.addParagraph("Error: Diplomacy brain not loaded", Misc.getNegativeHighlightColor());
			return;
		}
		brain.updateAllDispositions(0);
		MutableStat disp = brain.getDisposition(otherFactionId).disposition;
		printDispositionEntry(text, "Overall disposition", disp.modified);
		
		text.setFontSmallInsignia();
        text.addParagraph(StringHelper.HR);
		
		Iterator<String> iterModifiers = disp.getFlatMods().keySet().iterator();
        while (iterModifiers.hasNext())
        {
            String id = iterModifiers.next();
			StatMod mod = disp.getFlatStatMod(id);
			printDispositionEntry(text, mod.desc, mod.value);
        }
        text.addParagraph(StringHelper.HR);
        text.setFontInsignia();
	}
	
	/**
	 * Creates dialog options for the faction list subgroups
	 * @param dialog
	 */
	public static void listGroups(InteractionDialogAPI dialog, MemoryAPI memory)
	{		
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		List<FactionListGrouping> groups;
		
		if (memory.contains(FACTION_GROUPS_KEY))
		{
			groups = (List<FactionListGrouping>)memory.get(FACTION_GROUPS_KEY);
		}
		else
		{
			List<String> exclude = Arrays.asList(new String[]{dialog.getInteractionTarget().getFaction().getId()});
			List<String> factionsForDirectory = Nex_FactionDirectoryHelper.getFactionsForDirectory(exclude);
			groups = Nex_FactionDirectoryHelper.getFactionGroupings(factionsForDirectory);
			memory.set(FACTION_GROUPS_KEY, groups, GROUPS_CACHE_TIME);
		}

		int groupNum = 0;
		for (FactionListGrouping group : groups)
		{
			groupNum++;
			String optionId = "nex_getDispositionList" + groupNum;
			opts.addOption(group.getGroupingRangeString(),
					optionId, group.tooltip);
			opts.setTooltipHighlights(optionId, group.getFactionNames().toArray(new String[0]));
			opts.setTooltipHighlightColors(optionId, group.getTooltipColors().toArray(new Color[0]));
		}
		
		String exitOpt = "exerelinBaseCommanderMenuRepeat";
		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), exitOpt);
		opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
		
		ExerelinUtils.addDevModeDialogOptions(dialog, false);
	}
}