package exerelin.campaign.ai.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.ai.concern.special.InterventionConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;

public class InterventionAction extends DeclareWarAction {

    @Override
    public boolean generate() {
        // TODO: create an intervention intel
        return super.generate();
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!(concern instanceof InterventionConcern)) return false;
        InterventionConcern ic = (InterventionConcern)concern;

        float ourWeariness = DiplomacyManager.getWarWeariness(ai.getFactionId(), true);
        if (ourWeariness > DiplomacyBrain.MAX_WEARINESS_FOR_WAR) {
            return false;
        }

        if ((ai.getFaction().isPlayerFaction() || faction == Global.getSector().getPlayerFaction())) {
            if (!NexConfig.followersDiplomacy) return false;
            if (Misc.getCommissionFaction() != null) return false;
        }

        // can't intervene if ceasefired
        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
        if (brain != null && brain.getCeasefires().containsKey(ic.getOtherFaction().getId())) {
            return false;
        }

        return !NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy
                && !DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PACIFIST)
                && DiplomacyManager.getWarWeariness(ai.getFactionId(), true) <= DiplomacyBrain.MAX_WEARINESS_FOR_WAR;
    }
}
