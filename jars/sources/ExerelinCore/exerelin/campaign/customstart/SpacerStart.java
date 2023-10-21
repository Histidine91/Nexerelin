package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.NGCAddStandardStartingScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.StringHelper;
import java.util.Map;

public class SpacerStart extends CustomStart {
	
	@Override
	public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		data.addScriptBeforeTimePass(new Script() {
			public void run() {
				Global.getSector().getMemoryWithoutUpdate().set("$spacerStart", true);
				//Global.getSector().getMemoryWithoutUpdate().set("$nex_startLocation", "nomios");
			}
		});
		String vid = "kite_original_Stock";
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
		
		data.getStartingCargo().getCredits().add(2000);
		AddRemoveCommodity.addCreditsGainText(2000, dialog.getTextPanel());
		MutableCharacterStatsAPI stats = data.getPerson().getStats();
		stats.addPoints(1);
		
		CampaignFleetAPI tempFleet = FleetFactoryV3.createEmptyFleet(
				PlayerFactionStore.getPlayerFactionIdNGC(), FleetTypes.PATROL_SMALL, null);
		tempFleet.getFleetData().addFleetMember(temp);
		tempFleet.getFleetData().setFlagship(temp);
		temp.setCaptain(data.getPerson());
		temp.getRepairTracker().setCR(0.7f);
		tempFleet.getFleetData().setSyncNeeded();
		tempFleet.getFleetData().syncIfNeeded();
		tempFleet.forceSync();
		
		// enforce normal difficulty
		data.setDifficulty("normal");
		ExerelinSetupData.getInstance().easyMode = false;
		PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER);
		ExerelinSetupData.getInstance().freeStart = true;
		
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
				
				// add spacer obligation if not already set in 
				if (!ExerelinSetupData.getInstance().spacerObligation) {
					new Nex_SpacerObligation();
				}
			}
		});
		
		dialog.getVisualPanel().showFleetInfo(StringHelper.getString("exerelin_ngc", "playerFleet", true), 
				tempFleet, null, null);
		
		
		dialog.getOptionPanel().addOption(StringHelper.getString("done", true), "nex_NGCDone");
		dialog.getOptionPanel().addOption(StringHelper.getString("back", true), "nex_NGCStartBack");
	}
}
