package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexUtilsFaction;
import lombok.extern.log4j.Log4j;

import java.util.Set;

@Log4j
public class PowerfulUnfriendlyConcern extends DiplomacyConcern {
    @Override
    public boolean generate() {
        FactionAPI us = ai.getFaction();
        Set alreadyConcerned = getExistingConcernItems();

        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>();

        float ourStrength = getFactionStrength(us);

        for (String factionId : getRelevantLiveFactionIds()) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (alreadyConcerned.contains(faction)) continue;
            float theirStrength = getFactionStrength(faction);
            if (!shouldBeConcernedAbout(faction, ourStrength, theirStrength)) continue;

            float weight = theirStrength * 2 - ourStrength;
            if (weight <= SAIConstants.MIN_FACTION_PRIORITY_TO_CARE) continue;
            weight *= getPriorityMult(us.getRelationshipLevel(faction));

            picker.add(faction, weight);
        }
        faction = picker.pick();
        priority.modifyFlat("power", picker.getWeight(faction), StrategicAI.getString("statFactionPower", true));

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
        if (!shouldBeConcernedAbout(faction, ourStrength, theirStrength)) {
            end();
            return;
        }

        float weight = theirStrength * 2 - ourStrength;
        if (weight <= SAIConstants.MIN_FACTION_PRIORITY_TO_CARE) {
            end();
            return;
        }
        weight *= getPriorityMult(ai.getFaction().getRelationshipLevel(faction));
        priority.modifyFlat("power", weight, StrategicAI.getString("statFactionPower", true));
        super.update();
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
        if (NexUtilsFaction.isPirateFaction(faction.getId())) return false; // no need to befriend pirates

        RepLevel disregardAtRep = RepLevel.NEUTRAL;
        if (DiplomacyTraits.hasTrait(us.getId(), DiplomacyTraits.TraitIds.PARANOID)) {
            disregardAtRep = disregardAtRep.getOneBetter();
        }
        if (faction.isAtWorst(us, disregardAtRep)) return false;    // safe for now

        if (theirStrength * SAIConstants.STRENGTH_MULT_FOR_CONCERN < ourStrength) return false;   // we're bigger than them

        return true;
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        LabelAPI label = super.createTooltipDesc(tooltip, holder, pad);
        if (DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PARANOID)) {
            DiplomacyTraits.TraitDef def = DiplomacyTraits.getTrait(DiplomacyTraits.TraitIds.PARANOID);
            tooltip.addPara(StrategicAI.getString("concernDesc_paranoidHigherRepLevel"), pad, def.color, def.name);
        }

        return label;
    }
}
