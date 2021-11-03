package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import static com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler.isRuler;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Map;

public class Nex_Alliance extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!isRuler(playerFactionId)) {
			return false;
		}
		
		String arg = params.get(0).getString(memoryMap);
		switch (arg) {
			case "enoughRep":
				return enoughRepToJoin(params.get(1).getString(memoryMap), 
						params.get(2).getString(memoryMap));
			case "isAlignmentCompatible":
				return isAlignmentCompatible(dialog.getInteractionTarget().getFaction().getId(), 
						params.get(1).getString(memoryMap));
			case "invite":
				invite(dialog, memoryMap);
				return true;
			case "form":
				form(dialog, memoryMap);
				return true;
			case "join":
				join(dialog, memoryMap);
				return true;
			case "leave":
				leave(dialog, memoryMap);
				return true;
		}
		
		return true;
	}
	
	public boolean enoughRepToJoin(String factionId, String allianceId) {
		FactionAPI faction = Global.getSector().getFaction(factionId);
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		if (alliance != null) {
			for (String memberId : alliance.getMembersCopy()) {
				if (faction.isHostileTo(memberId))
					return false;
			}
			
			float averageRelation = alliance.getAverageRelationshipWithFaction(factionId);
			if (averageRelation < AllianceManager.MIN_RELATIONSHIP_TO_STAY)
				return false;
		}
		
		return true;
	}
	
	public boolean isAlignmentCompatible(String factionId, String allianceId) {
		if (NexConfig.ignoreAlignmentForAlliances) return true;
		
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		float compat = AllianceManager.getAlignmentCompatibilityWithAlliance(factionId, alliance);
		return compat >= AllianceManager.MIN_ALIGNMENT_TO_JOIN_ALLIANCE;
	}
	
	public void invite(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		String factionId = dialog.getInteractionTarget().getFaction().getId();
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		Alliance alliance = AllianceManager.getFactionAlliance(playerFactionId);
		
		TextPanelAPI text = dialog.getTextPanel();
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		
		AllianceManager.joinAllianceStatic(factionId, alliance);
		AllianceManager.setPlayerInteractionTarget(null);
		
		MemoryAPI memory = memoryMap.get(MemKeys.FACTION);
		AllianceManager.setMemoryKeys(memory, alliance);
		
		msg(text, "invitedToAlliance", factionId, null, alliance.getName());
	}
	
	public void form(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		String factionId = dialog.getInteractionTarget().getFaction().getId();
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		TextPanelAPI text = dialog.getTextPanel();
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		Alliance alliance = AllianceManager.createAlliance(playerFactionId, factionId, AllianceManager.getBestAlignment(factionId, playerFactionId));
		AllianceManager.setPlayerInteractionTarget(null);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		AllianceManager.setMemoryKeys(memory, alliance);
		memory = memoryMap.get(MemKeys.FACTION);
		AllianceManager.setMemoryKeys(memory, alliance);
		
		msg(text, "formedAlliance", factionId, null, alliance.getName(), Misc.getPositiveHighlightColor());
	}
	
	public void join(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		String factionId = dialog.getInteractionTarget().getFaction().getId();
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		
		Alliance oldAlliance = AllianceManager.getFactionAlliance(playerFactionId);
		Alliance newAlliance = AllianceManager.getFactionAlliance(factionId);
		boolean oldAllianceDissolved = false;
		
		TextPanelAPI text = dialog.getTextPanel();
		String id;
		String oldAllianceName = null;
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		
		if (oldAlliance != null && oldAlliance != newAlliance) {
			AllianceManager.leaveAlliance(playerFactionId, false);
			
			oldAllianceDissolved = (oldAlliance.getMembersCopy().size() <= 1);
			id = "switchedAlliances";
			oldAllianceName = oldAlliance.getName();
		} else {
			id = "joinedAlliance";
		}
		if (oldAlliance != newAlliance)
			AllianceManager.joinAllianceStatic(playerFactionId, newAlliance);
		
		AllianceManager.setPlayerInteractionTarget(null);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		AllianceManager.setMemoryKeys(memory, newAlliance);
		
		msg(text, id, null, oldAllianceName, newAlliance.getName(), Misc.getPositiveHighlightColor());
		
		if (oldAllianceDissolved) {
			msg(text, "allianceDissolved", null, oldAllianceName, null);
		}
	}
	
	public void leave(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		Alliance oldAlliance = AllianceManager.getFactionAlliance(playerFactionId);
		boolean oldAllianceDissolved = false;
		
		TextPanelAPI text = dialog.getTextPanel();
		
		AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
		AllianceManager.leaveAlliance(playerFactionId, false);
		AllianceManager.setPlayerInteractionTarget(null);
		
		oldAllianceDissolved = (oldAlliance.getMembersCopy().size() <= 1);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		AllianceManager.unsetMemoryKeys(memory);
		
		if (oldAllianceDissolved) {
			memory = memoryMap.get(MemKeys.FACTION);
			AllianceManager.unsetMemoryKeys(memory);			
		}
		
		msg(text, "leftAlliance", null, oldAlliance.getName(), null);
		if (oldAllianceDissolved) {
			msg(text, "allianceDissolved", null, oldAlliance.getName(), null);
		}
	}
	
	public void msg(TextPanelAPI text, String id, String factionId, String oldAlliance, 
			String newAlliance) 
	{
		msg(text, id, factionId, oldAlliance, newAlliance, Misc.getHighlightColor());
	}
	
	public void msg(TextPanelAPI text, String id, String factionId, String oldAlliance, 
			String newAlliance, Color color) 
	{
		String str = StringHelper.getString("exerelin_alliances", id);
		if (oldAlliance != null)
			str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance);
		if (newAlliance != null)
			str = StringHelper.substituteToken(str, "$NewAlliance", newAlliance);
		if (factionId != null) {
			str = StringHelper.substituteToken(str, "$theFaction", 
					Global.getSector().getFaction(factionId).getDisplayNameLongWithArticle(), true);
		}
			
		text.addParagraph(str, color);
	}
	
}
