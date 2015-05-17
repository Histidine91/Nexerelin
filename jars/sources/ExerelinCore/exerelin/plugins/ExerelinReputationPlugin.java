package exerelin.plugins;

import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import exerelin.campaign.AllianceManager;
import static exerelin.utilities.ExerelinUtilsReputation.syncFactionRelationshipsToPlayer;

/**
 * same as vanilla one except also syncs faction reputation to player one
 */
public class ExerelinReputationPlugin extends CoreReputationPlugin {
    
    @Override
    public ReputationActionResponsePlugin.ReputationAdjustmentResult handlePlayerReputationAction(Object actionObject, String factionId)
    {
        ReputationActionResponsePlugin.ReputationAdjustmentResult result = super.handlePlayerReputationAction(actionObject, factionId);
        AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", factionId);
        syncFactionRelationshipsToPlayer();
        return result;
    }
}
