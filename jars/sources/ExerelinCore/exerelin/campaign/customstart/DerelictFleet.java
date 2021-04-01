package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.newgame.NGCAddStartingShipsByFleetType;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexUtilsFleet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Deprecated
public class DerelictFleet extends CustomStart {
	
	protected List<String> ships = new ArrayList<>(Arrays.asList(new String[]{
		"apogee_Balanced",
		"crig_Standard"
	}));
	
	@Override
	public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData.getInstance().freeStart = true;
		PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER);
		addDerelicts();
		
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		
		NGCAddStartingShipsByFleetType.generateFleetFromVariantIds(dialog, data, null, ships);
		
		data.addScript(new Script() {
			public void run() {
				CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
				
				NexUtilsFleet.addDMods(fleet, ExerelinSetupData.getInstance().dModLevel);
				
				fleet.getFleetData().ensureHasFlagship();
				
				for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
					float max = member.getRepairTracker().getMaxCR();
					member.getRepairTracker().setCR(max);
				}
				fleet.getFleetData().setSyncNeeded();
				fleet.getFleetData().syncIfNeeded();
			}
		});
		
		FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep4");
	}
	
	protected void addDerelicts() {
		FactionAPI faction = Global.getSector().getFaction(Factions.DERELICT);
		ships.add(getShip(faction, ShipRoles.COMBAT_MEDIUM));
		//ships.add(getShip(faction, ShipRoles.COMBAT_MEDIUM));
		ships.add(getShip(faction, ShipRoles.COMBAT_SMALL));
		ships.add(getShip(faction, ShipRoles.COMBAT_SMALL));
	}
}
