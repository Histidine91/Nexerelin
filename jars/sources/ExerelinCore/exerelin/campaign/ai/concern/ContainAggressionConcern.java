package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import lombok.extern.log4j.Log4j;

import java.util.Set;

@Log4j
public class ContainAggressionConcern extends DiplomacyConcern {

    public static final float MIN_INFAMY_TO_START = 100;
    public static final float MAX_INFAMY_TO_END = 75;

    @Override
    public boolean generate() {
        FactionAPI us = ai.getFaction();
        Set alreadyConcerned = getExistingConcernItems();

        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>();

        for (String factionId : getRelevantLiveFactionIds()) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (!shouldBeConcernedAbout(faction)) continue;

            if (alreadyConcerned.contains(faction)) continue;
            float infamy = DiplomacyManager.getBadboy(faction);
            if (infamy < MIN_INFAMY_TO_START) continue;

            float weight = infamy * 2;
            weight *= getPriorityMult(us.getRelationshipLevel(faction));

            picker.add(faction, weight);
        }
        faction = picker.pick();
        priority.modifyFlat("infamy", picker.getWeight(faction), StrategicAI.getString("statFactionInfamy", true));

        return faction != null;
    }

    @Override
    public void update() {
        if (isFactionCommissionedPlayer(faction)) {
            end();
            return;
        }
        float infamy = DiplomacyManager.getBadboy(faction);
        if (infamy < MAX_INFAMY_TO_END) {
            end();
            return;
        }
        if (!shouldBeConcernedAbout(faction)) {
            end();
            return;
        }

        float weight = infamy * getPriorityMult(ai.getFaction().getRelationshipLevel(faction));
        priority.modifyFlat("infamy", infamy, StrategicAI.getString("statFactionInfamy", true));
        priority.modifyFlat("power", weight, StrategicAI.getString("statFactionPower", true));
        super.update();
    }

    protected float getPriorityMult(RepLevel level) {
        return 2 - 0.25f * level.ordinal();
    }

    protected boolean shouldBeConcernedAbout(FactionAPI faction) {
        FactionAPI us = ai.getFaction();
        if (faction.isHostileTo(us)) return false;  // already at war anyway

        RepLevel disregardAtRep = RepLevel.NEUTRAL;
        if (DiplomacyTraits.hasTrait(us.getId(), DiplomacyTraits.TraitIds.PARANOID)) {
            disregardAtRep = disregardAtRep.getOneBetter();
        }
        if (faction.isAtWorst(us, disregardAtRep)) return false;    // safe for now

        return true;
    }

    @Override
    public void modifyActionPriority(StrategicAction action) {
        float infamy = DiplomacyManager.getBadboy(faction);
        action.getPriority().modifyFlat("infamy", infamy/5,  StrategicAI.getString("statFactionInfamy", true));
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
