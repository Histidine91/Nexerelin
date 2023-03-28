package exerelin.campaign.ai.action;

import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.NexConfig;
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
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canMakePeace")) return false;
        if (concern.getFaction() != null && !concern.getFaction().isHostileTo(ai.getFaction())) {
            return false;
        }
        return !NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy;
    }
}
