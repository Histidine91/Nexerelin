package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;


public class NGCSetStartingFleetTooltipsAndState extends BaseCommandPlugin {
	
	protected static String[] FLEET_TYPES = {"SOLO", "COMBAT_SMALL", "TRADE_SMALL", "COMBAT_LARGE", "TRADE_LARGE"};
	protected static String[] DIALOG_ENTRIES = {"Solo", "CombatSmall", "TradeSmall", "CombatLarge", "TradeLarge"};
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(PlayerFactionStore.getPlayerFactionIdNGC());
		
		for (int i=0; i<FLEET_TYPES.length; i++)
		{
			String fleetTypeStr = FLEET_TYPES[i];
			String option = "exerelinNGCFleet" + DIALOG_ENTRIES[i];
			List<String> startingVariants = factionConf.getStartShipsForType(fleetTypeStr, false);
			if (startingVariants == null) {
				dialog.getOptionPanel().setEnabled(option, false);
				continue;
			}
			List<String> highlights = new ArrayList<>();
			List<Color> colors = new ArrayList<>();
			
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
				String designation = temp.getVariant().getDesignation().toLowerCase();
				String tooltipLine;
				if (type == FleetMemberType.FIGHTER_WING)
					tooltipLine = StringHelper.getString("exerelin_ngc", "fighterWingString");
				else
					tooltipLine = StringHelper.getString("exerelin_ngc", "shipString");
				tooltipLine = StringHelper.substituteToken(tooltipLine, "$shipClass", className);
				tooltipLine = StringHelper.substituteToken(tooltipLine, "$designation", designation);
				
				tooltip += tooltipLine;
				if (j < startingVariants.size() - 1) tooltip += "\n";
				highlights.add(className);
				colors.add(Misc.getHighlightColor());
			}
			dialog.getOptionPanel().setEnabled(option, true);
			dialog.getOptionPanel().setTooltip(option, tooltip);
			dialog.getOptionPanel().setTooltipHighlights(option, highlights.toArray(new String[0]));
			dialog.getOptionPanel().setTooltipHighlightColors(option, colors.toArray(new Color[0]));
			
			memoryMap.get(MemKeys.LOCAL).set("$startShips_" + fleetTypeStr, startingVariants);
		}
		
		return true;
	}
}