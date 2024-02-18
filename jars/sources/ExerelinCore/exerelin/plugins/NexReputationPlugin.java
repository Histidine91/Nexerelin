package exerelin.plugins;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NexReputationPlugin extends CoreReputationPlugin {

    public static final Set<RepActions> COVERED_ACTIONS = new HashSet<>(Arrays.asList(new RepActions[] {
            RepActions.TRANSPONDER_OFF
    }));

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

        if (RepActions.TRANSPONDER_OFF == action) {
            if (factionId.equals(Misc.getCommissionFactionId())) {
                return new ReputationAdjustmentResult(0);
            }
        }

        return super.handlePlayerReputationActionInner(actionObject, factionId, person, delegate);
    }

}
