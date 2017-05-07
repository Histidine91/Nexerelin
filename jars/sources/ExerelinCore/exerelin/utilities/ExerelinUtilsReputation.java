package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;

public class ExerelinUtilsReputation
{
	public static float getClampedRelationshipDelta(String faction1Id, String faction2Id, float delta)
	{
		float max = ExerelinFactionConfig.getMaxRelationship(faction1Id, faction2Id);
		float min = ExerelinFactionConfig.getMinRelationship(faction1Id, faction2Id);
		float curr = Global.getSector().getFaction(faction1Id).getRelationship(faction2Id);
		if (delta > 0 && curr + delta > max)
			delta = max - curr;
		if (delta < 0 && curr + delta < min)
			delta = min - curr;
		return delta;
	}
	
	public static ExerelinReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction, PersonAPI person, float delta)
	{
		return adjustPlayerReputation(faction, person, delta, null, null);
	}
	
	public static ExerelinReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction, PersonAPI person, float delta, CommMessageAPI message, TextPanelAPI textPanel)
	{
		String factionId = faction.getId();
		FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);
		boolean wasHostile = player.isHostileTo(faction);
		ReputationAdjustmentResult result;
		
		// clamp to configs' min/max relationships
		if (!DiplomacyManager.getRandomFactionRelationships())
		{
			String myFactionId = PlayerFactionStore.getPlayerFactionId();
			delta = getClampedRelationshipDelta(myFactionId, faction.getId(), delta);
		}
		
		CustomRepImpact impact = new CustomRepImpact();
		impact.delta = delta;
		if (textPanel != null)
			result = Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact, textPanel), factionId);
		else
			result = Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact, message, true), factionId);
		
		if (person != null) 
		{
			CustomRepImpact impact2 = new CustomRepImpact();
			impact2.delta = delta * 1.5f;
			if (textPanel != null)
				result = Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact2, textPanel), person);
			else
				result = Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact2, message, true), person);
		}
		
		boolean isHostile = player.isHostileTo(faction);
		ExerelinReputationAdjustmentResult result2 = new ExerelinReputationAdjustmentResult(result.delta, wasHostile, isHostile);
		return result2;
	}
	
	public static void syncFactionRelationshipToPlayer(String factionIdToSync, String otherFactionId)
	{
		if (otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;
		if (otherFactionId.equals("merc_hostile")) return;
		if (otherFactionId.equals("famous_bounty")) return;
		if (otherFactionId.equals("shippackfaction")) return;
		
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getFaction("player");
		FactionAPI factionToSync = sector.getFaction(factionIdToSync);
		
		float relationship = playerFaction.getRelationship(otherFactionId);
		factionToSync.setRelationship(otherFactionId, relationship);
		DiplomacyManager.clampRelations(factionIdToSync, otherFactionId, 0);
		AllianceManager.remainInAllianceCheck(factionIdToSync, otherFactionId);
	}
	
	// re-set our faction's relations to match our own
	// easier than trying to override stuff with all the private classes and such
	public static void syncFactionRelationshipsToPlayer(String factionId)
	{
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getFaction(Factions.PLAYER);
		FactionAPI faction = sector.getFaction(factionId);
		List<FactionAPI> factions = sector.getAllFactions();
		
		for (FactionAPI otherFaction: factions)
		{
			if (otherFaction != playerFaction && otherFaction != faction)
			{
				syncFactionRelationshipToPlayer(factionId, otherFaction.getId());
			}
		}
		SectorManager.checkForVictory();
	}
	
	public static void syncFactionRelationshipsToPlayer()
	{
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		syncFactionRelationshipsToPlayer(playerAlignedFactionId);
		if (!playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);
		}
	}
	
	public static void syncPlayerRelationshipToFaction(String factionId, String otherFactionId)
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
		if (otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;
		float relationship = faction.getRelationship(otherFactionId);
		Global.getSector().getPlayerFaction().setRelationship(otherFactionId, relationship);
	}
	
	public static void syncPlayerRelationshipsToFaction(String factionId)
	{
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getPlayerFaction();
		FactionAPI faction = sector.getFaction(factionId);
		
		for (FactionAPI otherFaction: sector.getAllFactions())
		{
			if (otherFaction != playerFaction && otherFaction != faction)
			{
				syncPlayerRelationshipToFaction(factionId, otherFaction.getId());
			}
		}
		playerFaction.setRelationship("merc_hostile", -1);
		playerFaction.setRelationship("famous_bounty", -1);
		playerFaction.setRelationship("shippackfaction", RepLevel.FRIENDLY);
		
		syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);
		//SectorManager.checkForVictory(); // already done in syncFactionRelationshipsToPlayer
	}
	
	public static void syncPlayerRelationshipsToFaction()
	{
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		syncPlayerRelationshipsToFaction(playerAlignedFactionId);
	}
}
