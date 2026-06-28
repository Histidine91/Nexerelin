package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NexReputationPlugin extends CoreReputationPlugin {

    public static final Set<RepActions> COVERED_ACTIONS = new HashSet<>(Arrays.asList(
            RepActions.TRANSPONDER_OFF, RepActions.COMBAT_NORMAL_TOFF, RepActions.COMBAT_NORMAL
    ));

    // only handles the specific case of negating rep loss if caught with transponder off by commissioning faction
    // previously it could also change the rep limit to inhospitable instead of hostile if I wanted, but I ended up not doing this anyway
    public ReputationAdjustmentResult handlePlayerReputationActionInner(Object actionObject,
                                                                        String factionId,
                                                                        PersonAPI person,
                                                                        RelationshipAPI delegate) {
        RepActions action = null;
        if (actionObject instanceof RepActions) {
            action = (RepActions) actionObject;
        } else if (actionObject instanceof RepActionEnvelope) {
            RepActionEnvelope envelope = (RepActionEnvelope) actionObject;
            action = envelope.action;
        }

        if (!COVERED_ACTIONS.contains(action)) {
            return super.handlePlayerReputationActionInner(actionObject, factionId, person, delegate);
        }

        FactionAPI faction = Global.getSector().getFaction(factionId);
        String reason = null;
        Object param = null;
        CommMessageAPI message = null;
        TextPanelAPI panel = null;
        boolean withMessage = true;
        boolean addMessageOnNoChange = true;
        if (actionObject instanceof RepActions) {
            action = (RepActions) actionObject;
            param = null;
        } else if (actionObject instanceof RepActionEnvelope) {
            RepActionEnvelope envelope = (RepActionEnvelope) actionObject;
            action = envelope.action;
            param = envelope.param;
            message = envelope.message;
            panel = envelope.textPanel;
            addMessageOnNoChange = envelope.addMessageOnNoChange;
            withMessage = envelope.withMessage;
            reason = envelope.reason;
        }

        float delta = 0;
        RepLevel limit = null;
        RepLevel ensureAtBest = null;

        switch (action) {
            case TRANSPONDER_OFF:
                if (factionId.equals(Misc.getCommissionFactionId())) {
                    return new ReputationAdjustmentResult(0);
                }
                break;
            case COMBAT_NORMAL:
            case COMBAT_NORMAL_TOFF:
                if (PlayerFactionStore.getPlayerFaction().isHostileTo(factionId)) {
                    return new ReputationAdjustmentResult(0);
                }
                break;
            /*
            case COMBAT_AGGRESSIVE:
                delta = -RepRewards.HIGH;
                limit = RepLevel.HOSTILE;
                ensureAtBest = RepLevel.HOSTILE;
                break;
            case COMBAT_AGGRESSIVE_TOFF:
                delta = -RepRewards.HIGH;
                limit = RepLevel.HOSTILE;
                break;
            */
        }


        //if (delta == 0) {
            return super.handlePlayerReputationActionInner(actionObject, factionId, person, delegate);
        //}

        // fine, I guess we'll have to copy the entire rep adjustment code
        /*
        if (delta < 0 && delta > -0.01f) delta = -0.01f;
        if (delta > 0 && delta < 0.01f) delta = 0.01f;
        delta = Math.round(delta * 100f) / 100f;

        if (delegate.getTarget() == Global.getSector().getPlayerFaction()) {
            delta = 0;
        }
        if (delegate.getTarget() == Global.getSector().getPlayerPerson()) {
            delta = 0;
        }

        float deltaSign = Math.signum(delta);
        //float before = player.getRelationship(faction.getId());
        float before = delegate.getRel();

        if (ensureAtBest != null) {
            delegate.ensureAtBest(ensureAtBest);
        }

        delegate.adjustRelationship(delta, limit);

        //float after = player.getRelationship(faction.getId());
        float after = delegate.getRel();
        delta = after - before;

        //if (delta != 0) {
        if (withMessage) {
            if (Math.abs(delta) >= 0.005f) {
                addAdjustmentMessage(delta, faction, person, message, panel, null, null, true, 0f, reason);
            } else if (deltaSign != 0 && addMessageOnNoChange) {
                addNoChangeMessage(deltaSign, faction, person, message, panel, null, null, true, 0f, reason);
            }
        }

        if (delta != 0) {
            if (person == null) {
                Global.getSector().reportPlayerReputationChange(faction.getId(), delta);
            } else {
                Global.getSector().reportPlayerReputationChange(person, delta);
            }
        }

        return new ReputationAdjustmentResult(delta);
         */
    }

}
