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
		List<String> startingVariants = (List<String>)memoryMap.get(MemKeys.LOCAL).get("$startShips_" + fleetTypeStr);
		if (startingVariants == null || startingVariants.isEmpty())
			startingVariants = factionConf.getStartShipsForType(fleetTypeStr, true);
		
		int crew = 0;
		int supplies = 0;
		int machinery = 0;
		int fuel = 0;
		
		for (String variantId : startingVariants)
		{
			try {
				FleetMemberType type = FleetMemberType.SHIP;
				if (variantId.endsWith("_wing")) {
					type = FleetMemberType.FIGHTER_WING; 
				}
				data.addStartingFleetMember(variantId, type);

				FleetMemberAPI temp = Global.getFactory().createFleetMember(type, variantId);
				crew += (int)Math.min(temp.getNeededCrew() * 1.2f, temp.getMaxCrew());
				supplies += (int)temp.getCargoCapacity()/2;
				machinery += (int)temp.getCargoCapacity()/8;
				fuel += (int)Math.min(temp.getFuelUse() * 20, temp.getFuelCapacity());

				AddRemoveCommodity.addFleetMemberGainText(Global.getSettings().getVariant(variantId), dialog.getTextPanel());
			} catch (RuntimeException rex) {	// probably variant not found
				Global.getLogger(this.getClass()).error(rex.getMessage());
				dialog.getTextPanel().addParagraph(rex.getMessage());
			}	
		}
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.CREW, crew);
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, supplies);
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.HEAVY_MACHINERY, machinery);
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.FUEL, fuel);
		
		AddRemoveCommodity.addCommodityGainText(Commodities.CREW, crew, dialog.getTextPanel());
		AddRemoveCommodity.addCommodityGainText(Commodities.SUPPLIES, supplies, dialog.getTextPanel());
		AddRemoveCommodity.addCommodityGainText(Commodities.HEAVY_MACHINERY, machinery, dialog.getTextPanel());
		AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, fuel, dialog.getTextPanel());
		
		return true;
	}
}






