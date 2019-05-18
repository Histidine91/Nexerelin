package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.NGCAddStandardStartingScript;
import com.fs.starfarer.api.impl.campaign.tutorial.SpacerObligation;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;

public class Nex_NGCSpacerStart extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		data.addScriptBeforeTimePass(new Script() {
			public void run() {
				Global.getSector().getMemoryWithoutUpdate().set("$spacerStart", true);
			}
		});
		String vid = params.get(0).getString(memoryMap);
		data.addStartingFleetMember(vid, FleetMemberType.SHIP);
		FleetMemberAPI temp = Global.getFactory().createFleetMember(FleetMemberType.SHIP, vid);
		
		int fuel = (int)temp.getFuelCapacity();
		
		data.getStartingCargo().addItems(CargoAPI.CargoItemType.RESOURCES, Commodities.CREW, 2);
		data.getStartingCargo().addItems(CargoAPI.CargoItemType.RESOURCES, Commodities.SUPPLIES, 15);
		data.getStartingCargo().addItems(CargoAPI.CargoItemType.RESOURCES, Commodities.FUEL, fuel);

		AddRemoveCommodity.addFleetMemberGainText(temp.getVariant(), dialog.getTextPanel());
		AddRemoveCommodity.addCommodityGainText(Commodities.CREW, 2, dialog.getTextPanel());
		AddRemoveCommodity.addCommodityGainText(Commodities.SUPPLIES, 15, dialog.getTextPanel());
		AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, fuel, dialog.getTextPanel());
		
		data.addScript(new Script() {
			public void run() {
				CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
				
				NGCAddStandardStartingScript.adjustStartingHulls(fleet);

				fleet.getFleetData().ensureHasFlagship();
				
				for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
					float max = member.getRepairTracker().getMaxCR();
					member.getRepairTracker().setCR(max);
				}
				fleet.getFleetData().setSyncNeeded();
				
				new SpacerObligation();
			}
		});
		return true;
	}
}
