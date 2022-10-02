package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import lombok.extern.log4j.Log4j;

import java.util.Set;

@Log4j
public class DevelopRelationsConcern extends DiplomacyConcern {
    @Override
    public boolean generate() {
        FactionAPI us = ai.getFaction();
        Set alreadyConcerned = getExistingConcernItems();

        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>();

        float ourStrength = getFactionStrength(us);

        for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (alreadyConcerned.contains(faction)) continue;
            float theirStrength = getFactionStrength(faction);
            if (!wantToBefriend(faction)) continue;

            float weight = theirStrength;
            if (weight <= 0) continue;

            picker.add(faction, weight);
        }
        faction = picker.pick();
        priority.modifyFlat("power", picker.getWeight(faction)/3, StrategicAI.getString("statFactionPower", true));

        return faction != null;
    }

    @Override
    public void update() {
        float ourStrength = getFactionStrength(ai.getFaction());
        float theirStrength = getFactionStrength(faction);
        if (!wantToBefriend(faction)) {
            end();
            return;
        }

        float weight = theirStrength;
        if (weight <= 0) {
            end();
            return;
        }
        priority.modifyFlat("power", theirStrength/3, StrategicAI.getString("statFactionPower", true));
    }

    protected boolean wantToBefriend(FactionAPI faction) {
        FactionAPI us = ai.getFaction();
        if (faction.isAtBest(us, RepLevel.NEUTRAL)) return false;
        if (faction.isAtWorst(us, RepLevel.FRIENDLY)) return false;

        return true;
    }
}
