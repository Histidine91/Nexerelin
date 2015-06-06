package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationPlugin.CharacterCreationData;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NGCSetStartingLocationExerelin extends BaseCommandPlugin {
    
    public static final Map<String, String> FACTION_HOME_SYSTEMS = new HashMap<>();
    
    static {
        FACTION_HOME_SYSTEMS.put("hegemony", "Corvus");
        FACTION_HOME_SYSTEMS.put("tritachyon", "Magec");
        FACTION_HOME_SYSTEMS.put("sindrian_diktat", "Askonia");
        FACTION_HOME_SYSTEMS.put("luddic_church", "Eos");
        FACTION_HOME_SYSTEMS.put("pirates", "Askonia");
        FACTION_HOME_SYSTEMS.put("blackrock_driveyards", "Gneiss");
        FACTION_HOME_SYSTEMS.put("citadel", "Citadel");
        FACTION_HOME_SYSTEMS.put("exigency", "Tasserus");
        FACTION_HOME_SYSTEMS.put("interstellarimperium", "Thracia");
        FACTION_HOME_SYSTEMS.put("junk_pirates", "Breh'Inni");
        FACTION_HOME_SYSTEMS.put("mayorate", "Rasht");
        FACTION_HOME_SYSTEMS.put("neutrinocorp", "Corona Australis");
        FACTION_HOME_SYSTEMS.put("pack", "Canis");
        FACTION_HOME_SYSTEMS.put("patnavy", "Patria");
        FACTION_HOME_SYSTEMS.put("pn_colony", "Tolp");
        FACTION_HOME_SYSTEMS.put("SCY", "Tartarus");
        FACTION_HOME_SYSTEMS.put("shadow_industry", "Anar");
        FACTION_HOME_SYSTEMS.put("syndicate_asp", "Ursulo");
        FACTION_HOME_SYSTEMS.put("spire", "Gemstone");
        FACTION_HOME_SYSTEMS.put("templars", "Antioch");
        FACTION_HOME_SYSTEMS.put("valkyrian", "Enigma");
    }
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
        if (ExerelinConfig.corvusMode)
        {
            String factionId = PlayerFactionStore.getPlayerFactionId();
            if (FACTION_HOME_SYSTEMS.containsKey(factionId))
            {
                data.setStartingLocationName(FACTION_HOME_SYSTEMS.get(factionId));
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
