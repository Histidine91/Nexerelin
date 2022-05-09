package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper.FactionListGrouping;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class Nex_NGCListFactions extends PaginatedOptions {
	
	public static final String JOIN_FACTION_OPTION_PREFIX = "nex_NGCJoinFaction_";
	public static final String LIST_FACTIONS_IN_GROUP_OPTION_PREFIX = "nex_NGCListFactionsGroup_";
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg)
		{
			case "listGroups":
				optionsPerPage = 6;
				super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
				listGroups(dialog);
				return true;
				
			case "listFactions":
				OptionPanelAPI opts = dialog.getOptionPanel();
				opts.clearOptions();
				int num = 0;
				if (params.size() > 1) {
					num = (int)params.get(1).getFloat(memoryMap);
				}
				else {
					String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
					num = Integer.parseInt(option.substring(LIST_FACTIONS_IN_GROUP_OPTION_PREFIX.length()));
				}
				
				if (num == 0)
				{
					super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
					listGroups(dialog);
					return true;
				}
				memoryMap.get(MemKeys.LOCAL).set("$factionGroup", num);
				FactionListGrouping group = Nex_FactionDirectoryHelper.getNGCFactionGroupings(true).get(num - 1);
				for (FactionAPI faction : group.factions)
				{
					String factionId = faction.getId();
					NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
					String optId = JOIN_FACTION_OPTION_PREFIX + factionId;
					String text = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
					if (!conf.difficultyString.isEmpty())
					{
						text = text + " (" + conf.difficultyString + ")";
					}
					
					opts.addOption(text, optId, faction.getBaseUIColor(), null);
					if (conf.ngcTooltip != null)
					{
						opts.setTooltip(optId, conf.ngcTooltip);
					}
					//opts.setTooltip(optId, Global.getSettings().getDescription(
					//			faction.getId(), Description.Type.FACTION).getText1FirstPara());
				}
				opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_NGCFactionsBack");
				opts.setShortcut("nex_NGCFactionsBack", Keyboard.KEY_ESCAPE, false, false, false, false);
				
				NexUtils.addDevModeDialogOptions(dialog);
				
				return true;
		}
		
		return false;
	}
	
	/**
	 * Creates dialog options for the faction list subgroups
	 * @param dialog
	 */
	protected void listGroups(InteractionDialogAPI dialog)
	{
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		List<FactionListGrouping> groups = Nex_FactionDirectoryHelper.getNGCFactionGroupings(true);

		int groupNum = 0;
		for (FactionListGrouping group : groups)
		{
			groupNum++;
			String optionId = LIST_FACTIONS_IN_GROUP_OPTION_PREFIX + groupNum;
			addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc", "factions")) + ": " + group.getGroupingRangeString(),
					optionId);
		}
		addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc", "ownFaction")), "nex_NGCJoinOwnFaction");
		addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc", "freeStartHard")), "nex_NGCFreeStart");
		
		addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc", "randomFaction")), "nex_NGCJoinRandomFaction");
		addOption(Misc.ucFirst(StringHelper.getString("exerelin_ngc", "customStart")), "nex_NGCCustomStart");

		if (Global.getSettings().isDevMode())
		{
			addOption(StringHelper.getString("exerelin_ngc", "devStart"), "nex_NGCDevStart");
			addOption(StringHelper.getString("exerelin_ngc", "devStartFast"), "nex_NGCDevStartFast");
			//ExerelinUtils.addDevModeDialogOptions(dialog);
		}
		addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "exerelinNGCOptionsBack");
				
		showOptions();
	}
	
	@Override
	public void showOptions() {
		super.showOptions();
		
		OptionPanelAPI opts = dialog.getOptionPanel();
		if (ExerelinSetupData.getInstance().corvusMode)
		{
			//opts.setTooltip("nex_NGCJoinOwnFaction", StringHelper.getString("exerelin_ngc", "ownFactionDisabledTooltip"));
			//opts.setEnabled("nex_NGCJoinOwnFaction", false);
		}
		
		List<FactionListGrouping> groups = Nex_FactionDirectoryHelper.getNGCFactionGroupings(true);
		int groupNum = 0;
		for (FactionListGrouping group : groups)
		{
			groupNum++;
			String optionId = LIST_FACTIONS_IN_GROUP_OPTION_PREFIX + groupNum;
			opts.setTooltip(optionId, group.tooltip);
			opts.setTooltipHighlights(optionId, group.getFactionNames().toArray(new String[0]));
			opts.setTooltipHighlightColors(optionId, group.getTooltipColors().toArray(new Color[0]));
		}
		
		opts.setTooltip("nex_NGCFreeStart", StringHelper.getString("exerelin_ngc", "freeStartTooltip"));
		opts.setShortcut("exerelinNGCOptionsBack", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
}