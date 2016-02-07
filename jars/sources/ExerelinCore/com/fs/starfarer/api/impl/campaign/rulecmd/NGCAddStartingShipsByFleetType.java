package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;


public class NGCAddStartingShipsByFleetType extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String fleetTypeStr = params.get(0).getString(memoryMap);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		ExerelinFactionConfig factionConf = ExerelinConfig.getExerelinFactionConfig(PlayerFactionStore.getPlayerFactionIdNGC());
		List<String> startingVariants = factionConf.getStartShipsForType(fleetTypeStr, true);
		
		int crew = 0;
		int supplies = 0;
		int fuel = 0;
		
		for (String variantId : startingVariants)
		{
			FleetMemberType type = FleetMemberType.SHIP;
			if (variantId.endsWith("_wing")) {
				type = FleetMemberType.FIGHTER_WING; 
			}
			data.addStartingFleetMember(variantId, type);

			FleetMemberAPI temp = Global.getFactory().createFleetMember(type, variantId);
			crew += (int)Math.min(temp.getNeededCrew() * 1.2f, temp.getMaxCrew());
			supplies += (int)temp.getCargoCapacity()/2;
			fuel += (int)Math.min(temp.getFuelUse() * 20, temp.getFuelCapacity());

			data.getStartingCargo().addItems(CargoItemType.RESOURCES, "regular_crew", crew);
			data.getStartingCargo().addItems(CargoItemType.RESOURCES, "supplies", supplies);
			data.getStartingCargo().addItems(CargoItemType.RESOURCES, "fuel", fuel);
			
			String className = temp.getHullSpec().getHullName();
			String designation = temp.getVariant().getDesignation().toLowerCase();
			String printed = Misc.ucFirst(StringHelper.getString("exerelin_ngc", "added"));
			if (type == FleetMemberType.FIGHTER_WING)
				printed += " " + StringHelper.getString("exerelin_ngc", "fighterWingString");
			else
				printed += " " + StringHelper.getString("exerelin_ngc", "shipString");
			printed = StringHelper.substituteToken(printed, "$shipClass", className);
			printed = StringHelper.substituteToken(printed, "$designation", designation);
			
			dialog.getTextPanel().addParagraph(printed, Misc.getPositiveHighlightColor());
		}
		
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$crewAdded", crew, 0);
		memory.set("$suppliesAdded", supplies, 0);
		memory.set("$fuelAdded", fuel, 0);
		
		return true;
	}
}






