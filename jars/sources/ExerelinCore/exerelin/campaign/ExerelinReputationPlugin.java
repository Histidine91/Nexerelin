package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import java.util.List;

/**
 *
 */
public class ExerelinReputationPlugin extends CoreReputationPlugin 
{
    @Override
    public ReputationAdjustmentResult handlePlayerReputationAction(Object actionObject, String factionId)
    {
        ReputationAdjustmentResult result = super.handlePlayerReputationAction(actionObject, factionId);
        
        // re-set our faction's relations to match our own
        // easier than trying to override stuff with all the private classes and such
        SectorAPI sector = Global.getSector();
        FactionAPI playerFaction = sector.getFaction("player");
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        
        List<FactionAPI> factions = sector.getAllFactions();
        
        for (FactionAPI faction: factions)
	{
		if (faction != playerFaction && faction != playerAlignedFaction)
		{
			float relationship = playerFaction.getRelationship(faction.getId());
			playerAlignedFaction.setRelationship(faction.getId(), relationship);
			faction.setRelationship(playerAlignedFactionId, relationship);
		}
	}
        return result;
    }
}
