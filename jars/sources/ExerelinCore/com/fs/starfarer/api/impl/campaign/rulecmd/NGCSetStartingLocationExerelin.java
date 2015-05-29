package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationPlugin.CharacterCreationData;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinConfig;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class NGCSetStartingLocationExerelin extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
        if (ExerelinConfig.corvusMode)
        {
            data.setStartingLocationName("Corvus");
            data.getStartingCoordinates().set(600, -600);
            return true;
        }
        
        String homestar = "Exerelin";
        try {
                JSONObject planetConfig = Global.getSettings().loadJSON("data/config/planetNames.json");
                JSONArray systemNames = planetConfig.getJSONArray("stars");
                homestar = systemNames.getString(0);

        } catch (JSONException | IOException ex) {
                Global.getLogger(NGCSetStartingLocationExerelin.class).log(Level.ERROR, ex);
        }
        data.setStartingLocationName(homestar);
        data.getStartingCoordinates().set(1200, -1200);
        
        return true;
    }
}
