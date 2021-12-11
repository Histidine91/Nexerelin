package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.tutorial.CampaignTutorialScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import java.util.Map;

public class Tutorial extends CustomStart {
		
	@Override
	public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData.getInstance().freeStart = true;
		PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER);
		
		final CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		
		data.addScript(new Script() {
			@Override
			public void run() {
				Global.getLogger(Tutorial.class).info("Running custom start script");
				
				CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
				
				fleet.clearAbilities();
					PersistentUIDataAPI.AbilitySlotsAPI slots = Global.getSector().getUIData().getAbilitySlotsAPI();
					for (int i = 0; i < 5; i++) {
						slots.setCurrBarIndex(i);
						for (int j = 0; j < 10; j++) {
							PersistentUIDataAPI.AbilitySlotAPI slot = slots.getCurrSlotsCopy().get(j);
							slot.setAbilityId(null);
						}
					}					
					
					fleet.clearFloatingText();
					fleet.setTransponderOn(false);
					
					
					StarSystemAPI system = Global.getSector().getStarSystem("galatia");
					system.addScript(new CampaignTutorialScript(system));
				}
			}
		);
		
		data.addScriptBeforeTimePass(new Script() {
			public void run() {
				Global.getSector().getMemoryWithoutUpdate().set(CampaignTutorialScript.USE_TUTORIAL_RESPAWN, true);
			}
		});
		
		data.setWithTimePass(false);
		
		FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep3");
	}
}
