package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.StartFleetType;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;


public class NGCAddStartingShipsByFleetType extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		Nex_VisualCustomPanel.clearPanel(dialog, memoryMap);
		
		String fleetTypeStr = params.get(0).getString(memoryMap);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		NexFactionConfig factionConf = NexConfig.getFactionConfig(PlayerFactionStore.getPlayerFactionIdNGC());
		List<String> startingVariants = (List<String>)memoryMap.get(MemKeys.LOCAL).get("$startShips_" + fleetTypeStr);
		if (startingVariants == null || startingVariants.isEmpty())
			startingVariants = factionConf.getStartFleetForType(fleetTypeStr, true, 0);
		
		generateFleetFromVariantIds(dialog, data, fleetTypeStr, startingVariants);
		addStartingDModScript(memoryMap.get(MemKeys.LOCAL));
		
		ExerelinSetupData.getInstance().startFleetType = StartFleetType.getType(fleetTypeStr);
		
		memoryMap.get(MemKeys.LOCAL).set("$nex_lastSelectedFleetType", fleetTypeStr);
		
		return true;
	}
	
	public static void generateFleetFromVariantIds(InteractionDialogAPI dialog, 
			CharacterCreationData data, String fleetTypeStr, List<String> startingVariants) {
		int crew = 0;
		int supplies = 0;
		int machinery = 0;
		int fuel = 0;
		
		CampaignFleetAPI tempFleet = FleetFactoryV3.createEmptyFleet(
				PlayerFactionStore.getPlayerFactionIdNGC(), FleetTypes.PATROL_SMALL, null);
		boolean first = true;
		
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
				
				if ("super".equalsIgnoreCase(fleetTypeStr))
				{
					supplies += (int)temp.getCargoCapacity() * 3 / 4;
					machinery += (int)temp.getCargoCapacity() / 4;
				}
				else
				{
					supplies += (int)temp.getCargoCapacity()/2;
					machinery += (int)temp.getCargoCapacity()/8;
				}
				fuel += (int)Math.min(temp.getFuelUse() * 20, temp.getFuelCapacity());
				
				temp.getRepairTracker().setCR(0.7f);
				
				tempFleet.getFleetData().addFleetMember(temp);
				if (first) {
					tempFleet.getFleetData().setFlagship(temp);
					temp.setCaptain(data.getPerson());
					first = false;
				}

				AddRemoveCommodity.addFleetMemberGainText(temp.getVariant(), dialog.getTextPanel());
			} catch (RuntimeException rex) {	// probably variant not found
				Global.getLogger(NGCAddStartingShipsByFleetType.class).error(rex.getMessage());
				dialog.getTextPanel().addParagraph(rex.getMessage());
			}	
		}
		tempFleet.forceSync();
		
		TextPanelAPI text = dialog.getTextPanel();
		addCargo(data, Commodities.CREW, crew, text);
		addCargo(data, Commodities.SUPPLIES, supplies, text);
		addCargo(data, Commodities.HEAVY_MACHINERY, machinery, text);
		addCargo(data, Commodities.FUEL, fuel, text);
		
		dialog.getVisualPanel().showFleetInfo(StringHelper.getString("exerelin_ngc", "playerFleet", true), 
				tempFleet, null, null);
	}
	
	public static void addStartingDModScript(MemoryAPI localMem) {
		CharacterCreationData data = (CharacterCreationData)localMem.get("$characterData");
		data.addScript(new Script() {
			public void run() {
				CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
				if (fleet.getMemoryWithoutUpdate().contains("$nex_addedStartingDMods"))
					return;
				NexUtilsFleet.addDMods(fleet, ExerelinSetupData.getInstance().dModLevel);
				fleet.getFleetData().syncIfNeeded();
				fleet.getMemoryWithoutUpdate().set("$nex_addedStartingDMods", true, 5);
			}
		});
	}
	
	protected static void addCargo(CharacterCreationData data, String commodity, int amount, TextPanelAPI text)
	{
		if (amount <= 0) return;
		data.getStartingCargo().addItems(CargoItemType.RESOURCES, commodity, amount);
		AddRemoveCommodity.addCommodityGainText(commodity, amount, text);
	}
}






