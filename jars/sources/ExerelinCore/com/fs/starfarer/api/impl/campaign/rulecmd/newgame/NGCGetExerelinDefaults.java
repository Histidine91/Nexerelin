package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.questskip.QuestChainSkipEntry;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;

import java.util.List;
import java.util.Map;


public class NGCGetExerelinDefaults extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		//ExerelinSetupData.resetInstance();
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		MemoryAPI map = memoryMap.get(MemKeys.LOCAL);
		map.set("$numSystems", setupData.numSystems, 0);
		map.set("$numPlanets", setupData.numPlanets, 0);
		map.set("$numStations", setupData.numStations, 0);
		map.set("$startRelationsMode", setupData.startRelationsMode, 0);
		map.set("$startRelationsApplyToPirates", setupData.applyStartRelationsModeToPirates, 0);
		map.set("$prismMarketPresent", setupData.prismMarketPresent, 0);
		map.set("$respawnFactions", setupData.respawnFactions, 0);
		map.set("$onlyRespawnStartingFactions", setupData.onlyRespawnStartingFactions, 0);
		map.set("$useFactionWeights", setupData.useFactionWeights, 0);
		map.set("$randomFactionWeights", setupData.randomFactionWeights, 0);
		
		map.set("$corvusMode", setupData.corvusMode, 0);
		map.set("$hardMode", setupData.hardMode, 0);
		map.set("$randomStartLocation", setupData.randomStartLocation, 0);
		map.set("$nex_customScenarioName", StringHelper.getString("none"));
		map.set("$nex_antiochInRandom", setupData.randomAntiochEnabled, 0);
		
		map.set("$easyMode", setupData.easyMode, 0);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		if (setupData.easyMode)  data.setDifficulty("easy");
		else data.setDifficulty("normal");
		
		map.set("$nex_legacyInsurance", NexConfig.legacyInsurance);
		
		String str = ExerelinSetupData.getDModCountText(setupData.dModLevel);
		map.set("$nex_ngcDModsString", str);

		if (QuestChainSkipEntry.getEntries() == null)
			QuestChainSkipEntry.initEntries();
		
		return true;
	}
}