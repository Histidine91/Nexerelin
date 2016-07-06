package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import static com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.addAdjustmentMessage;
import static com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.addNoChangeMessage;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;

public class ExerelinUtilsReputation
{
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
		
		// 0ld 0.65.2 implementation
		/*
		float before = player.getRelationship(factionId);
		
		player.adjustRelationship(factionId, delta);
		float after = player.getRelationship(factionId);
		delta = after - before;
		
		//if (delta != 0) {
		if (Math.abs(delta) >= 0.01f) {
			addAdjustmentMessage(delta, faction, person, message, textPanel);
		} else {
			addNoChangeMessage(1.0f, faction, person, message, textPanel);
		}
		
		if (delta != 0) {
			Global.getSector().reportPlayerReputationChange(factionId, delta);
		}
		*/
		
		// moved to DiplomacyManager listener
		/*
		syncFactionRelationshipToPlayer(PlayerFactionStore.getPlayerFactionId(), factionId);
		syncFactionRelationshipToPlayer(ExerelinConstants.PLAYER_NPC_ID, factionId);
		AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", factionId);
		
		if (player.isAtBest(PlayerFactionStore.getPlayerFactionId(), RepLevel.INHOSPITABLE))
			SectorManager.scheduleExpelPlayerFromFaction();
		
		SectorManager.checkForVictory();
		*/
		//return new ExerelinReputationAdjustmentResult(delta);
	}
	
	public static void syncFactionRelationshipToPlayer(String factionIdToSync, String otherFactionId)
	{
		if (otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;
		if (otherFactionId.equals("merc_hostile")) return;
		if (otherFactionId.equals("famous_bounty")) return;
		
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getFaction("player");
		FactionAPI factionToSync = sector.getFaction(factionIdToSync);
		
		float relationship = playerFaction.getRelationship(otherFactionId);
		factionToSync.setRelationship(otherFactionId, relationship);
		AllianceManager.remainInAllianceCheck(factionIdToSync, otherFactionId);
		AllianceManager.syncAllianceRelationshipsToFactionRelationship(factionIdToSync, otherFactionId);
	}
	
	// re-set our faction's relations to match our own
	// easier than trying to override stuff with all the private classes and such
	public static void syncFactionRelationshipsToPlayer(String factionId)
	{
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getFaction("player");
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
	
	public static void syncPlayerRelationshipsToFaction(String factionId, boolean noUpdateAlliance)
	{
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getPlayerFaction();
		FactionAPI faction = sector.getFaction(factionId);
		List<FactionAPI> factions = sector.getAllFactions();
		
		for (FactionAPI otherFaction: factions)
		{
			if (otherFaction != playerFaction && otherFaction != faction)
			{
				String otherFactionId = otherFaction.getId();
				if (otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
				float relationship = faction.getRelationship(otherFactionId);
				playerFaction.setRelationship(otherFactionId, relationship);
				if (!noUpdateAlliance)
					AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", otherFactionId);
			}
		}
		Global.getSector().getFaction(Factions.PLAYER).setRelationship("merc_hostile", -1);
		Global.getSector().getFaction(Factions.PLAYER).setRelationship("famous_bounty", -1);
		syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);
		//SectorManager.checkForVictory(); // already done in syncFactionRelationshipsToPlayer
	}
	
	public static void syncPlayerRelationshipsToFaction(boolean noUpdateAlliance)
	{
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		syncPlayerRelationshipsToFaction(playerAlignedFactionId, noUpdateAlliance);
	}
}
