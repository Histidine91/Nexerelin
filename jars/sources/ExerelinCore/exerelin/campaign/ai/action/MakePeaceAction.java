package exerelin.campaign.ai.action;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import lombok.extern.log4j.Log4j;

@Log4j
public class MakePeaceAction extends DiplomacyAction {

    @Override
    public boolean generate() {
        String factionId = concern.getFaction() != null ? concern.getFaction().getId() : null;
        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());

        delegate = (StrategicActionDelegate)brain.checkPeace(factionId);
        if (delegate == null) return false;

        // may be a ceasefire offer to player, in which case action may not be completed yet
        if (delegate.getStrategicActionStatus() != StrategicActionDelegate.ActionStatus.IN_PROGRESS) {
            end(StrategicActionDelegate.ActionStatus.SUCCESS);
        }
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
    public boolean canUseForConcern(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canDeclareWar")) return false;
        if (DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PACIFIST)) return false;
        if (concern.getFaction() != null && concern.getFaction().isHostileTo(ai.getFaction())) {
            return false;
        }
        return true;
    }
}
