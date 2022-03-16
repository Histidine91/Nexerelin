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
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.awt.Color;
import java.util.List;

public class NexUtilsReputation
{
	public static float getClampedRelationshipDelta(String faction1Id, String faction2Id, float delta)
	{
		if (faction1Id.equals(faction2Id))
			return delta;
		float max = DiplomacyManager.getManager().getMaxRelationship(faction1Id, faction2Id);
		float min = NexFactionConfig.getMinRelationship(faction1Id, faction2Id);
		float curr = Global.getSector().getFaction(faction1Id).getRelationship(faction2Id);
		if (delta > 0 && curr + delta > max)
			delta = max - curr;
		if (delta < 0 && curr + delta < min)
			delta = min - curr;
		return delta;
	}
	
	public static void printToTextPanelOrCampaignUI(TextPanelAPI text, String str,
			String highlight, Color highlightColor)
	{
		if (highlightColor == null) highlightColor = Misc.getHighlightColor();
		
		if (text != null)
		{
			text.setFontSmallInsignia();
			LabelAPI label = text.addParagraph(str, Misc.getGrayColor());
			if (highlight != null) {
				label.setHighlight(highlight);
				label.setHighlightColor(highlightColor);
				//text.highlightFirstInLastPara(highlight, highlightColor);
			}
			text.setFontInsignia();
		}
		else
		{
			if (highlight != null)
				Global.getSector().getCampaignUI().addMessage(str, Misc.getTextColor(), highlight, "do_not_highlight", highlightColor, highlightColor);
			else
				Global.getSector().getCampaignUI().addMessage(str);
		}
	}
	
	public static ExerelinReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction,	float delta)
	{
		return adjustPlayerReputation(faction, null, delta, delta, null, null);
	}
	
	public static ExerelinReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction,
			float delta, CommMessageAPI message, TextPanelAPI textPanel)
	{
		return adjustPlayerReputation(faction, null, delta, delta, message, textPanel);
	}
	
	public static ExerelinReputationAdjustmentResult adjustPlayerReputation(FactionAPI faction,
			PersonAPI person, float delta, float deltaPerson, CommMessageAPI message, TextPanelAPI textPanel)
	{
		String factionId = faction.getId();
		FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);
		
		if (faction == player) return new ExerelinReputationAdjustmentResult(0);
		
		boolean wasHostile = player.isHostileTo(faction);
		ReputationAdjustmentResult result;
		
		// clamp to configs' min/max relationships
		float deltaBase = delta;
		boolean clamp = false;
		String myFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!DiplomacyManager.haveRandomRelationships(myFactionId, faction.getId()))
		{
			delta = getClampedRelationshipDelta(myFactionId, faction.getId(), delta);
			clamp = deltaBase > 0 && delta < deltaBase || deltaBase < 0 && delta > deltaBase;
		}
		
		CustomRepImpact impact = new CustomRepImpact();
		impact.delta = delta;
		result = Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact, message, textPanel, true), factionId);
		
		if (person != null) 
		{
			CustomRepImpact impact2 = new CustomRepImpact();
			impact2.delta = deltaPerson;
			result = Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact2, message, textPanel, true), person);
		}
		
		// print relationship limit message
		FactionAPI playerAligned = PlayerFactionStore.getPlayerFaction();
		if (clamp && playerAligned != faction)
		{
			String str = StringHelper.getString("exerelin_factions", "relationshipLimit");
			str = StringHelper.substituteToken(str, "$faction1", 
					NexUtilsFaction.getFactionShortName(playerAligned.getId()));
			str = StringHelper.substituteToken(str, "$faction2", 
					NexUtilsFaction.getFactionShortName(faction));
			String rel = getRelationStr(faction, playerAligned);
			str = StringHelper.substituteToken(str, "$relationship", rel);
			printToTextPanelOrCampaignUI(textPanel, str, rel, faction.getRelColor(playerAligned.getId()));
		}
		
		boolean isHostile = player.isHostileTo(faction);
		ExerelinReputationAdjustmentResult result2 = new ExerelinReputationAdjustmentResult(result.delta, wasHostile, isHostile);
		return result2;
	}
	
	public static void syncFactionRelationshipToPlayer(String factionIdToSync, String otherFactionId)
	{
		if (factionIdToSync.equals(otherFactionId)) return;
		if (NexConfig.getFactionConfig(factionIdToSync).noSyncRelations)
			return;
		if (NexConfig.getFactionConfig(otherFactionId).noSyncRelations)
			return;
		
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getFaction(Factions.PLAYER);
		FactionAPI factionToSync = sector.getFaction(factionIdToSync);
		
		float relationship = playerFaction.getRelationship(otherFactionId);
		float relationshipOld = factionToSync.getRelationship(otherFactionId);
		factionToSync.setRelationship(otherFactionId, relationship);
		DiplomacyManager.clampRelations(factionIdToSync, otherFactionId, 0);
		
		if (relationship < relationshipOld)
			AllianceManager.remainInAllianceCheck(factionIdToSync, otherFactionId);
	}
	
	// re-set our faction's relations to match our own
	// easier than trying to override stuff with all the private classes and such
	public static void syncFactionRelationshipsToPlayer(String factionId)
	{
		if (NexConfig.getFactionConfig(factionId).noSyncRelations)
			return;
		
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
	}
	
	public static void syncPlayerRelationshipToFaction(String factionId, String otherFactionId)
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
		if (factionId.equals(otherFactionId)) return;
		
		if (NexConfig.getFactionConfig(factionId).noSyncRelations)
			return;
		if (NexConfig.getFactionConfig(otherFactionId).noSyncRelations)
			return;
		
		float relationship = faction.getRelationship(otherFactionId);
		Global.getSector().getPlayerFaction().setRelationship(otherFactionId, relationship);
	}
	
	public static void syncPlayerRelationshipsToFaction(String factionId)
	{
		if (factionId.equals(Factions.PLAYER)) return;
		
		SectorAPI sector = Global.getSector();	
		FactionAPI playerFaction = sector.getPlayerFaction();
		FactionAPI faction = sector.getFaction(factionId);
		if (NexConfig.getFactionConfig(factionId).noSyncRelations)
			return;
		
		for (FactionAPI otherFaction: sector.getAllFactions())
		{
			if (otherFaction != playerFaction && otherFaction != faction)
			{
				syncPlayerRelationshipToFaction(factionId, otherFaction.getId());
			}
		}
		
		//syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);
		//SectorManager.checkForVictory(); // already done in syncFactionRelationshipsToPlayer
	}
	
	public static void syncPlayerRelationshipsToFaction()
	{
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		syncPlayerRelationshipsToFaction(playerAlignedFactionId);
	}
	
	public static String getRelationStr(FactionAPI faction1, FactionAPI faction2)
	{
		return getRelationStr(faction1.getRelationship(faction2.getId()));
	}
	
	public static String getRelationStr(float rel)
	{
		int repInt = (int) Math.ceil(rel * 100f);
		
		String standing = "" + repInt + "/100" + " (" + Misc.lcFirst(RepLevel.getLevelFor(rel).getDisplayName()) + ")";
		return standing;
	}
	
	public static Color getRelColor(float rel)
	{  
		Color relColor = new Color(125,125,125,255);
		if (rel > 1) rel = 1;
		if (rel < -1) rel = -1;

		if (rel > 0) {
			relColor = Misc.interpolateColor(relColor, Misc.getPositiveHighlightColor(), Math.max(0.15f, rel));  
		} else if (rel < 0) {
			relColor = Misc.interpolateColor(relColor, Misc.getNegativeHighlightColor(), Math.max(0.15f, -rel));  
		}  
		return relColor;
	}  
}
