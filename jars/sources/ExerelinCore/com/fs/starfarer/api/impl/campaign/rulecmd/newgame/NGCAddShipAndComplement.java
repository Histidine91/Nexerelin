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
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;


public class NGCAddShipAndComplement extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String vid = params.get(0).getString(memoryMap);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");

		try {
			FleetMemberType type = FleetMemberType.SHIP;
			if (vid.endsWith("_wing")) {
				type = FleetMemberType.FIGHTER_WING; 
			}
			data.addStartingFleetMember(vid, type);

			FleetMemberAPI temp = Global.getFactory().createFleetMember(type, vid);
			int crew = (int)Math.min(temp.getNeededCrew() * 1.5f, temp.getMaxCrew());
			int supplies = (int)temp.getCargoCapacity()/2;
			int machinery = (int)temp.getCargoCapacity()/8;
			int fuel = (int)Math.min(temp.getFuelUse() * 20, temp.getFuelCapacity());

			data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.CREW, crew);
			data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, supplies);
			data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.HEAVY_MACHINERY, machinery);
			data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.FUEL, fuel);

			AddRemoveCommodity.addFleetMemberGainText(temp.getVariant(), dialog.getTextPanel());
			AddRemoveCommodity.addCommodityGainText(Commodities.CREW, crew, dialog.getTextPanel());
			AddRemoveCommodity.addCommodityGainText(Commodities.SUPPLIES, supplies, dialog.getTextPanel());
			AddRemoveCommodity.addCommodityGainText(Commodities.HEAVY_MACHINERY, machinery, dialog.getTextPanel());
			AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, fuel, dialog.getTextPanel());
		} catch (RuntimeException rex) {	// probably variant not found
			Global.getLogger(this.getClass()).error(rex.getMessage());
			dialog.getTextPanel().addParagraph(rex.getMessage());
		}	
                
		return true;
	}
}