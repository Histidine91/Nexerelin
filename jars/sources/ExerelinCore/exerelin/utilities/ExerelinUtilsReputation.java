package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import static com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.addAdjustmentMessage;
import static com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.addNoChangeMessage;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;

public class ExerelinUtilsReputation
{
	public static ReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction, float delta)
	{
		return adjustPlayerReputation(faction, delta, null, null);
	}
	
	public static ReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction, float delta, CommMessageAPI message, TextPanelAPI textPanel)
	{
		String factionId = faction.getId();
		FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);
		float before = player.getRelationship(factionId);
		
		player.adjustRelationship(factionId, delta);
		float after = player.getRelationship(factionId);
		delta = after - before;
		
		//if (delta != 0) {
		if (Math.abs(delta) >= 0.01f) {
			addAdjustmentMessage(delta, faction, message, textPanel);
		} else {
			addNoChangeMessage(1.0f, faction, message, textPanel);
		}
		
		if (delta != 0) {
			Global.getSector().reportPlayerReputationChange(factionId, delta);
		}
		
		// moved to DiplomacyManager listener
		/*
		syncFactionRelationshipToPlayer(PlayerFactionStore.getPlayerFactionId(), factionId);
		syncFactionRelationshipToPlayer("player_npc", factionId);
		AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", factionId);
		
		if (player.isAtBest(PlayerFactionStore.getPlayerFactionId(), RepLevel.INHOSPITABLE))
			SectorManager.scheduleExpelPlayerFromFaction();
		
		SectorManager.checkForVictory();
		*/
		return new ReputationAdjustmentResult(delta);
	}
	
	public static void syncFactionRelationshipToPlayer(String factionIdToSync, String otherFactionId)
	{
		if (otherFactionId.equals("player_npc")) return;
		if (otherFactionId.equals("merc_hostile") && !factionIdToSync.equals("merc_hostile")) return;
		
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
		if (!playerAlignedFactionId.equals("player_npc"))
		{
			syncFactionRelationshipsToPlayer("player_npc");
		}
	}
	
	public static void syncPlayerRelationshipsToFaction(String factionId, boolean noUpdateAlliance)
	{
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getFaction("player");
		FactionAPI faction = sector.getFaction(factionId);
		List<FactionAPI> factions = sector.getAllFactions();
		
		for (FactionAPI otherFaction: factions)
		{
			if (otherFaction != playerFaction && otherFaction != faction)
			{
				String otherFactionId = otherFaction.getId();
				if (otherFactionId.equals("player_npc")) continue;
				float relationship = faction.getRelationship(otherFactionId);
				playerFaction.setRelationship(otherFactionId, relationship);
				if (!noUpdateAlliance)
					AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", otherFactionId);
			}
		}
		Global.getSector().getFaction(Factions.PLAYER).setRelationship("merc_hostile", -1);
		SectorManager.checkForVictory();
	}
	
	public static void syncPlayerRelationshipsToFaction(boolean noUpdateAlliance)
	{
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		syncPlayerRelationshipsToFaction(playerAlignedFactionId, noUpdateAlliance);
	}
}
