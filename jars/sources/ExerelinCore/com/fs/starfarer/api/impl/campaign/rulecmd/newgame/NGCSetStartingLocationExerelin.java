package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.ExerelinCorvusLocations;
import exerelin.world.ExerelinCorvusLocations.SpawnPointEntry;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;
import java.util.Map;


public class NGCSetStartingLocationExerelin extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory.getBoolean("$corvusMode"))
        {
            String factionId = PlayerFactionStore.getPlayerFactionIdNGC();
            SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
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
            //data.setStartingLocationName(homeStar);
            //data.setStartingLocationName("hyperspace");
			data.getStartingCoordinates().set(1200, -1200);
        }
        
        return true;
    }
}