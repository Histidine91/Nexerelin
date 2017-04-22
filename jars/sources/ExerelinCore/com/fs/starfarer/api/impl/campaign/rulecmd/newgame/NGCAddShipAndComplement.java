package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

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
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;


public class NGCAddShipAndComplement extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String vid = params.get(0).getString(memoryMap);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");

		FleetMemberType type = FleetMemberType.SHIP;
		if (vid.endsWith("_wing")) {
			type = FleetMemberType.FIGHTER_WING; 
		}
		data.addStartingFleetMember(vid, type);
		
		FleetMemberAPI temp = Global.getFactory().createFleetMember(type, vid);
		int crew = (int)Math.min(temp.getNeededCrew() * 1.5f, temp.getMaxCrew());
		int supplies = (int)temp.getCargoCapacity()/2;
		int fuel = (int)Math.min(temp.getFuelUse() * 20, temp.getFuelCapacity());
					
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, "crew", crew);
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, "supplies", supplies);
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, "fuel", fuel);
		
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$crewAdded", crew, 0);
		memory.set("$suppliesAdded", supplies, 0);
		memory.set("$fuelAdded", fuel, 0);
                
		return true;
	}
}