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
import com.fs.starfarer.api.impl.campaign.rulecmd.NGCAddStandardStartingScript;
import com.fs.starfarer.api.impl.campaign.rulecmd.newgame.NGCAddStartingShipsByFleetType;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CabalPirateStart extends CustomStart {
	
	protected List<String> ships = new ArrayList<>(Arrays.asList(new String[]{
		"uw_tempest_cabal_cus",
		"uw_scarab_cabal_cus",
		"uw_wolf_cabal_cus"
	}));
	
	@Override
	public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		PlayerFactionStore.setPlayerFactionIdNGC(Factions.PIRATES);
		
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		
		NGCAddStartingShipsByFleetType.generateFleetFromVariantIds(dialog, data, null, ships);
		
		data.addScript(new Script() {
			public void run() {
				
			}
		});
		
		FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep4");
	}
}
