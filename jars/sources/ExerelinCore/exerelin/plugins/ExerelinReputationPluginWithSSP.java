package exerelin.plugins;

import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin;
import data.scripts.campaign.SSP_ReputationPlugin;
import static exerelin.utilities.ExerelinUtilsReputation.syncFactionRelationshipsToPlayer;

/**
 * same as vanilla one except also syncs faction reputation to player one
 */
public class ExerelinReputationPluginWithSSP extends SSP_ReputationPlugin {
    
    @Override
    public ReputationActionResponsePlugin.ReputationAdjustmentResult handlePlayerReputationAction(Object actionObject, String factionId)
    {
        ReputationActionResponsePlugin.ReputationAdjustmentResult result = super.handlePlayerReputationAction(actionObject, factionId);
        syncFactionRelationshipsToPlayer();
        return result;
    }
}
