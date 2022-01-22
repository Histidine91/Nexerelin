package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import java.util.Map;

public class TemplarApostate extends CustomStart {
		
	@Override
	public void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData.getInstance().freeStart = true;
		PlayerFactionStore.setPlayerFactionIdNGC("templars");
		
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		
		data.addScript(new Script() {
			@Override
			public void run() {
				Global.getLogger(TemplarApostate.class).info("Running custom start script");
				//NexUtilsReputation.syncPlayerRelationshipsToFaction("templars");
				Global.getSector().getPlayerFaction().setRelationship("templars", -0.8f);
				Global.getSector().getMemoryWithoutUpdate().set("$nex_spawnAsFaction", Factions.PLAYER, 15);
				// force random spawn location
				Global.getSector().getMemoryWithoutUpdate().set("$nex_startLocation", null, 15);
				
				new TemplarApostateVictoryScript().init();
			}
		});
		
		data.addScriptBeforeTimePass(new Script() {
			public void run() {
				//PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER);
			}
		});
		
		FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep3");
	}
}
