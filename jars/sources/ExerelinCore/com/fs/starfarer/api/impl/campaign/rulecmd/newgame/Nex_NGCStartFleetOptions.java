package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.PaginatedOptions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import org.lwjgl.input.Keyboard;

@Deprecated
public class Nex_NGCStartFleetOptions extends PaginatedOptions {
	
	protected static final String[] FLEET_TYPES = {"SOLO", "COMBAT_SMALL", "COMBAT_LARGE", "TRADE_SMALL", 
		"TRADE_LARGE", "CARRIER_SMALL", "CARRIER_LARGE", "EXPLORER_SMALL", "EXPLORER_LARGE",
		"SUPER", "GRAND_FLEET"};
	protected static final String[] DIALOG_ENTRIES = {"Solo", "CombatSmall", "CombatLarge", "TradeSmall", 
		"TradeLarge", "CarrierSmall", "CarrierLarge", "ExplorerSmall", "ExplorerLarge", "Super", "GrandFleet"};
	protected static final Map<String, String> OPTION_TEXTS = new HashMap<>();
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	
	static {
		for (int i=0; i<DIALOG_ENTRIES.length; i++)
		{
			OPTION_TEXTS.put(FLEET_TYPES[i], StringHelper.getString("exerelin_ngc", 
					"fleet" + DIALOG_ENTRIES[i]));
		}
	}
	protected Map<String, String> tooltips = new HashMap<>();
	protected Map<String, List<String>> allHighlights = new HashMap<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		optionsPerPage = 4;
		super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
		
		populate(true);
		return true;
	}
	
	@Override
	public void showOptions()
	{
		super.showOptions();
		
		// add the tooltips
		for (Map.Entry<String, String> tmp : tooltips.entrySet())
		{
			String option = tmp.getKey();
			String tooltip = tmp.getValue();
			List<String> highlights = allHighlights.get(option);
			
			dialog.getOptionPanel().setTooltip(option, tooltip);
			List<Color> colors = new ArrayList<>();
			for (String highlight : highlights) {
				colors.add(Misc.getHighlightColor());
			}
			dialog.getOptionPanel().setTooltipHighlights(option, highlights.toArray(new String[0]));
			dialog.getOptionPanel().setTooltipHighlightColors(option, colors.toArray(new Color[0]));
		}
		
		dialog.getOptionPanel().setShortcut("nex_NGCFleetBack", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		if ("nex_NGCFleetReroll".equals(optionData))
		{
			populate(false);
			return;
		}
		else if ("nex_NGCFleetCycle".equals(optionData))
		{
			NexFactionConfig factionConf = NexConfig.getFactionConfig(
					PlayerFactionStore.getPlayerFactionIdNGC());
			//factionConf.cycleStartFleets();
			populate(false);
			return;
		}
		super.optionSelected(optionText, optionData);
	}
	
	protected void populate(boolean firstTime)
	{
		boolean useCycleButton = addShipOptions();
		
		if (firstTime)
		{
			if (memoryMap.get(MemKeys.LOCAL).getBoolean("$randomStartShips"))
				addOptionAllPages(Misc.ucFirst(StringHelper.getString("exerelin_ngc",
						"fleetRandomReroll")), "nex_NGCFleetReroll");
			else if (useCycleButton)
				addOptionAllPages(Misc.ucFirst(StringHelper.getString("exerelin_ngc",
						"fleetCycle")), "nex_NGCFleetCycle");
			addOptionAllPages(Misc.ucFirst(StringHelper.getString("back")), "nex_NGCFleetBack");
		}
		
		showOptions();
	}
	
	/**
	 * Generate the dialog options for each starting fleet type.
	 * @return True if any of the starting fleet types have multiple fleets, false otherwise.
	 */
	protected boolean addShipOptions()
	{
		options.clear();
		tooltips.clear();
		allHighlights.clear();
		
		boolean anyMultiple = false;
		
		NexFactionConfig factionConf = NexConfig.getFactionConfig(
				PlayerFactionStore.getPlayerFactionIdNGC());
		for (int i=0; i<FLEET_TYPES.length; i++)
		{
			String fleetTypeStr = FLEET_TYPES[i];
			String option = "nex_NGCFleet" + DIALOG_ENTRIES[i];
			List<String> startingVariants = factionConf.getStartFleetForType(fleetTypeStr, false, 0);
			if (startingVariants == null) {
				//dialog.getOptionPanel().setEnabled(option, false);
				continue;
			}
			
			String optionName = OPTION_TEXTS.get(fleetTypeStr);
			
			// check if we have multiple fleets allowed for this fleet type
			if (!memoryMap.get(MemKeys.LOCAL).getBoolean("$randomStartShips"))
			{
				int numFleets = factionConf.getNumStartFleetsForType(fleetTypeStr);
				int index = 0;
				if (numFleets > 1) {
					anyMultiple = true;
					optionName += " (" + index + "/" + numFleets + ")";
				}
			}
			
			// do tooltip and highlights
			List<String> highlights = new ArrayList<>();
			String tooltip = "";
			for (int j=0; j < startingVariants.size(); j++)
			{
				String variantId = startingVariants.get(j);
				FleetMemberType type = FleetMemberType.SHIP;
				if (variantId.endsWith("_wing")) {
					type = FleetMemberType.FIGHTER_WING; 
				}

				FleetMemberAPI temp = Global.getFactory().createFleetMember(type, variantId);

				String className = temp.getHullSpec().getHullName();
				String variantName = temp.getVariant().getDisplayName().toLowerCase();
				String designation = temp.getVariant().getDesignation().toLowerCase();
				String tooltipLine;
				if (type == FleetMemberType.FIGHTER_WING)
					tooltipLine = StringHelper.getString("exerelin_ngc", "fighterWingString");
				else
					tooltipLine = StringHelper.getString("exerelin_ngc", "shipString");
				tooltipLine = StringHelper.substituteToken(tooltipLine, "$shipClass", className);
				tooltipLine = StringHelper.substituteToken(tooltipLine, "$variantName", variantName);
				tooltipLine = StringHelper.substituteToken(tooltipLine, "$designation", designation);
				
				tooltip += tooltipLine;
				if (j < startingVariants.size() - 1) tooltip += "\n";
				highlights.add(className);
			}
			addOption(optionName, option);
			tooltips.put(option, tooltip);
			allHighlights.put(option, highlights);
			
			memoryMap.get(MemKeys.LOCAL).set("$startShips_" + fleetTypeStr, startingVariants);
		}
		
		return anyMultiple;
	}
}