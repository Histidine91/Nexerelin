package exerelin.campaign.ai.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
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
        String factionId = faction != null ? faction.getId() : null;
        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());

        delegate = brain.checkWar(factionId);
        if (delegate == null) return false;
        end(StrategicActionDelegate.ActionStatus.SUCCESS);
        return true;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        if (faction != null) {
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
            float decisionRating = brain.getWarDecisionRating(faction.getId());
            if (decisionRating < DiplomacyBrain.DECISION_RATING_FOR_WAR) {
                priority.modifyMult("targetWarDecisionRating", 0, StrategicAI.getString("statWarDecisionRating", true));
            } else {
                priority.modifyFlat("targetWarDecisionRating", decisionRating, StrategicAI.getString("statWarDecisionRating", true));
            }

        }
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        // we have the military tag for alignment purposes, but don't use this action for actual military actions
        if (concern.getDef().module == StrategicDefManager.ModuleType.MILITARY) return false;
        if (!concern.getDef().hasTag("canDeclareWar")) return false;

        if (faction != null) {
            // already at war
            if (faction.isHostileTo(ai.getFaction())) return false;

            // check validity
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
            if (brain.canWarWithFaction(faction.getId())) return false;
        }

        if ((ai.getFaction().isPlayerFaction() || faction == Global.getSector().getPlayerFaction())) {
            if (!NexConfig.followersDiplomacy) return false;
            if (Misc.getCommissionFaction() != null) return false;
        }

        return !NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy
                && !DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PACIFIST)
                && DiplomacyManager.getWarWeariness(ai.getFactionId(), true) <= DiplomacyBrain.MAX_WEARINESS_FOR_WAR;
    }
}
