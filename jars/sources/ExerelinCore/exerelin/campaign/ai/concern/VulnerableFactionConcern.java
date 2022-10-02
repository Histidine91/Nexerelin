package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import lombok.extern.log4j.Log4j;

import java.util.Set;

@Log4j
public class VulnerableFactionConcern extends DiplomacyConcern {
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
            if (!shouldBeConcernedAbout(faction, ourStrength, theirStrength)) continue;

            float weight = ourStrength/2 - theirStrength;
            if (weight <= 0) continue;
            weight *= getPriorityMult(us.getRelationshipLevel(faction));

            picker.add(faction, weight);
        }
        faction = picker.pick();
        priority.modifyFlat("power", picker.getWeight(faction), StrategicAI.getString("statFactionPower", true));

        return faction != null;
    }

    @Override
    public void update() {
        float ourStrength = getFactionStrength(ai.getFaction());
        float theirStrength = getFactionStrength(faction);
        if (!shouldBeConcernedAbout(faction, ourStrength, theirStrength)) {
            end();
            return;
        }

        float weight = ourStrength/2 - theirStrength;
        if (weight <= 0) {
            end();
            return;
        }
        weight *= getPriorityMult(ai.getFaction().getRelationshipLevel(faction));
        priority.modifyFlat("power", weight, StrategicAI.getString("statFactionPower", true));
    }

    protected float getPriorityMult(RepLevel level) {
        switch (level) {
            case FAVORABLE:
                return 0.75f;
            case SUSPICIOUS:
                return 1.25f;
            case INHOSPITABLE:
                return 1.5f;
            default:
                return 1;
        }
    }

    protected boolean shouldBeConcernedAbout(FactionAPI faction, float ourStrength, float theirStrength) {
        FactionAPI us = ai.getFaction();
        if (faction.isHostileTo(us)) return false;  // already at war anyway
        if (faction.isAtWorst(us, RepLevel.WELCOMING)) return false;    // safe for now
        if (theirStrength * SAIConstants.STRENGTH_MULT_FOR_CONCERN > ourStrength/2) return false;   // they're too big to easily overpower

        return true;
    }
}
