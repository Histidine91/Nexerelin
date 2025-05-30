package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.NexUtilsFleet;
import exerelin.world.scenarios.ScenarioManager;
import java.util.List;
import java.util.Map;

public class Nex_NGCFinalize extends BaseCommandPlugin {
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		if (data.startScenario != null) {
			ScenarioManager.prepScenario(data.startScenario);
		}
		if ("tutorialStart".equals(memoryMap.get(MemKeys.LOCAL).getString("$nex_customStart"))) 
		{
			CharacterCreationData ccd = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
			if (ccd.getPerson().getStats().getLevel() == 1) {
				ccd.getPerson().getStats().addPoints(-1);
			}
		}

		addStartingDModScript(memoryMap.get(MemKeys.LOCAL));
		
		return true;
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
}
