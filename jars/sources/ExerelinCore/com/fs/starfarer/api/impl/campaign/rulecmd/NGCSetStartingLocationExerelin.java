package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationPlugin.CharacterCreationData;
import com.fs.starfarer.api.util.Misc;
import data.scripts.world.ExerelinCorvusSpawnPoints;
import data.scripts.world.ExerelinCorvusSpawnPoints.SpawnPointEntry;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import java.util.List;
import java.util.Map;


public class NGCSetStartingLocationExerelin extends BaseCommandPlugin {
            
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
        if (ExerelinConfig.corvusMode)
        {
            String factionId = PlayerFactionStore.getPlayerFactionIdNGC();
            SpawnPointEntry spawnPoint = ExerelinCorvusSpawnPoints.getFactionSpawnPoint(factionId);
            if (spawnPoint != null)
            {
                data.setStartingLocationName(spawnPoint.systemName);
                data.getStartingCoordinates().set(600, -600);
            }
            else data.setStartingLocationName("hyperspace");
        }
        else
        {
            String homeStar = SectorManager.getFirstStarName();

            data.setStartingLocationName(homeStar);
            data.getStartingCoordinates().set(1200, -1200);
        }
        
        return true;
    }
}
