package exerelin.campaign.ai.action;

import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;

public class DiplomacyAction extends BaseStrategicAction {

    public DiplomacyIntel getIntel() {
        return (DiplomacyIntel)delegate;
    }

    @Override
    public boolean generate() {
        boolean canPositive = concern.getDef().hasTag("diplomacy_positive");
        boolean canNegative = concern.getDef().hasTag("diplomacy_negative");

        if (!canNegative && !canPositive) return false;

        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
        float disp = brain.getDisposition(concern.getFaction().getId()).disposition.getModifiedValue();
        float ourStrength = DiplomacyBrain.getFactionStrength(ai.getFactionId());
        float theirStrength = DiplomacyBrain.getFactionStrength(concern.getFaction().getId());

        if (ourStrength*1.5f < theirStrength)
        {
            canNegative = false;
        }
        if (disp <= DiplomacyBrain.DISLIKE_THRESHOLD)
        {
            canPositive = false;
        }
        else if (disp >= DiplomacyBrain.LIKE_THRESHOLD)
        {
            canNegative = false;
        }

        if (!canNegative && !canPositive) return false;

        DiplomacyManager.DiplomacyEventParams params = new DiplomacyManager.DiplomacyEventParams();
        params.random = false;
        params.onlyPositive = !canNegative;
        params.onlyNegative = canPositive;

        delegate = DiplomacyManager.createDiplomacyEvent(ai.getFaction(), concern.getFaction(), null, params);
        if (delegate == null) return false;
        end(StrategicActionDelegate.ActionStatus.SUCCESS);
        return true;
    }

    @Override
    public String getName() {
        if (getIntel() != null) return getIntel().getSmallDescriptionTitle();
        return getDef().id;
    }

    @Override
    public String getIcon() {
        if (getIntel() != null)  return getIntel().getIcon();
        return null;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        return concern.getDef().hasTag("diplomacy_positive") || concern.getDef().hasTag("diplomacy_negative");
    }
}
