package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import lombok.extern.log4j.Log4j;

import java.util.Set;

@Log4j
public class DevelopRelationsConcern extends DiplomacyConcern {
    @Override
    public boolean generate() {
        FactionAPI us = ai.getFaction();
        Set alreadyConcerned = getExistingConcernItems();
        //log.info("Generating develop relations concern for " + us.getDisplayName());
        //log.info("Develop relations existing concerns: " + alreadyConcerned + ", size " + alreadyConcerned.size());

        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>();

        float ourStrength = getFactionStrength(us);

        for (String factionId : getRelevantLiveFactionIds()) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == us) continue;
            if (alreadyConcerned.contains(faction)) continue;
            float theirStrength = getFactionStrength(faction);
            if (!wantToBefriend(faction)) continue;

            float weight = theirStrength;
            if (weight <= SAIConstants.MIN_FACTION_PRIORITY_TO_CARE) continue;

            picker.add(faction, weight);
        }
        faction = picker.pick();
        priority.modifyFlat("power", picker.getWeight(faction), StrategicAI.getString("statFactionPower", true));
        reapplyPriorityModifiers();

        return faction != null;
    }

    @Override
    public void update() {
        if (isFactionCommissionedPlayer(faction)) {
            end();
            return;
        }
        float ourStrength = getFactionStrength(ai.getFaction());
        float theirStrength = getFactionStrength(faction);
        if (!wantToBefriend(faction)) {
            end();
            return;
        }

        float weight = theirStrength;
        if (weight <= SAIConstants.MIN_FACTION_PRIORITY_TO_CARE) {
            end();
            return;
        }
        priority.modifyFlat("power", weight, StrategicAI.getString("statFactionPower", true));
        super.update();
    }

    protected boolean wantToBefriend(FactionAPI faction) {
        FactionAPI us = ai.getFaction();
        if (faction.isAtBest(us, RepLevel.NEUTRAL)) return false;
        if (faction.isAtWorst(us, RepLevel.FRIENDLY)) return false;

        return true;
    }
}
