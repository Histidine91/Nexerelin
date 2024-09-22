package exerelin.campaign.ai.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.ai.concern.InterventionConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.intel.diplomacy.InterventionIntel;
import exerelin.utilities.NexConfig;

public class InterventionAction extends DeclareWarAction {

    @Override
    public boolean generate() {
        faction = concern.getFaction();
        InterventionIntel intel = new InterventionIntel(this, ai.getFactionId(), faction.getId(), ((InterventionConcern)concern).getFriendFaction().getId());
        delegate = intel;
        if (delegate == null) return false;
        intel.init();

        // if sent to player, may not have received response yet
        if (delegate.getStrategicActionStatus() != StrategicActionDelegate.ActionStatus.IN_PROGRESS) {
            end(StrategicActionDelegate.ActionStatus.SUCCESS);
        }
        return true;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        priority.unmodifyMult("targetWarDecisionRating");   // don't allow zeroing out decision rating
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
        if (brain != null && brain.getCeasefires().containsKey(ic.getFaction().getId())) {
            return false;
        }

        return !NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy
                && !DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PACIFIST)
                && DiplomacyManager.getWarWeariness(ai.getFactionId(), true) <= DiplomacyBrain.MAX_WEARINESS_FOR_WAR;
    }
}
