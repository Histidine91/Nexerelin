package exerelin.campaign.ai.action;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;
import lombok.extern.log4j.Log4j;

@Log4j
public class DeclareWarAction extends DiplomacyAction {

    @Override
    public boolean generate() {
        String factionId = concern.getFaction() != null ? concern.getFaction().getId() : null;
        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());

        delegate = brain.checkWar(factionId);
        if (delegate == null) return false;
        end(StrategicActionDelegate.ActionStatus.SUCCESS);
        return true;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        FactionAPI faction = concern.getFaction();
        if (faction != null) {
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
            float decisionRating = brain.getWarDecisionRating(faction.getId());
            priority.modifyFlat("targetWarDecisionRating", decisionRating, StrategicAI.getString("statWarDecisionRating", true));
        }
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        // we have the military tag for alignment purposes, but don't use this action for actual military actions
        if (concern.getDef().module == StrategicDefManager.ModuleType.MILITARY) return false;
        if (concern.getFaction() != null && concern.getFaction().isHostileTo(ai.getFaction())) {
            return false;
        }
        return !NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy
                && !DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PACIFIST)
                && DiplomacyManager.getWarWeariness(ai.getFactionId(), true) <= DiplomacyBrain.MAX_WEARINESS_FOR_WAR;
    }
}
