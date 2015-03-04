package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;

/**
 *  same as vanilla one, except with a bit of extra stuff to handle being a member of a faction
 */
public class ExerelinReputationPlugin extends CoreReputationPlugin 
{
	@Override
	public ReputationAdjustmentResult handlePlayerReputationAction(Object actionObject, String factionId)
	{
		ReputationAdjustmentResult result = super.handlePlayerReputationAction(actionObject, factionId);
		syncFactionRelationshipsToPlayer();
		return result;
	}
	
	public static ReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction, float delta)
	{
	    return adjustPlayerReputation(faction, delta, null, null);
	}
	
	public static ReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction, float delta, CommMessageAPI message, TextPanelAPI textPanel)
	{
		FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);
		float before = player.getRelationship(faction.getId());
		
		player.adjustRelationship(faction.getId(), delta);
		float after = player.getRelationship(faction.getId());
		delta = after - before;
		
		//if (delta != 0) {
		if (Math.abs(delta) >= 0.01f) {
			addAdjustmentMessage(delta, faction, message, textPanel);
		} else {
			addNoChangeMessage(1.0f, faction, message, textPanel);
		}
		
		if (delta != 0) {
			Global.getSector().reportPlayerReputationChange(faction.getId(), delta);
		}
		syncFactionRelationshipsToPlayer();
		return new ReputationAdjustmentResult(delta);
	}
	
	public static void syncFactionRelationshipsToPlayer()
	{
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
				faction.setRelationship(playerAlignedFactionId, relationship);
			}
		}
		SectorManager.checkForVictory();
	}
}
