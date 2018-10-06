package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;


public class JoinFaction extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		SectorAPI sector = Global.getSector();
		
		String newFactionId = params.get(0).getString(memoryMap);
		FactionAPI newFaction = sector.getFaction(newFactionId);
		Alliance newAlliance = AllianceManager.getFactionAlliance(newFactionId);
		String oldFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI oldFaction = sector.getFaction(oldFactionId);
		Alliance oldAlliance = AllianceManager.getFactionAlliance(oldFactionId);
		boolean oldAllianceDissolved = false;
		boolean newAllianceDissolved = false;
		boolean isDefection;
		
		TextPanelAPI text = dialog.getTextPanel();
		String str;
		
		PlayerFactionStore.setPlayerFactionId(newFactionId);
		if (oldFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			if (oldAlliance != null)
			{
				AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
				AllianceManager.leaveAlliance(oldFactionId, false);
				AllianceManager.setPlayerInteractionTarget(null);
				
				oldAllianceDissolved = (oldAlliance.getMembersCopy().size() <= 1);
				newAllianceDissolved = (oldAllianceDissolved && newAlliance == oldAlliance);
				
				str = StringHelper.getString("exerelin_alliances", "leftAlliance");
				str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance.getName());
				text.addParagraph(str, Misc.getPositiveHighlightColor());
				
				if (oldAllianceDissolved) {
					str = StringHelper.getString("exerelin_alliances", "allianceDissolved");
					str = StringHelper.substituteToken(str, "$OldAlliance", oldAlliance.getName());
					text.addParagraph(str, Misc.getPositiveHighlightColor());
				}
			} else {
				PlayerFactionStore.saveIndependentPlayerRelations();
			}
			str = StringHelper.getString("exerelin_factions", "joinedFaction");
			isDefection = false;
		} else {
			str = StringHelper.getString("exerelin_factions", "switchedFactions");
			// -5 rep to negate join/leave infinite rep exploit
			NexUtilsReputation.adjustPlayerReputation(oldFaction, -0.05f);
			isDefection = true;
		}
		NexUtilsReputation.syncPlayerRelationshipsToFaction(newFactionId);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$faction", newFaction, 0);
		memory.set("$factionId", newFactionId, 0);
		memory.set("$theFaction", newFaction.getDisplayNameWithArticle(), 0);
		
		if (newAlliance != null && !newAllianceDissolved) {
			AllianceManager.setMemoryKeys(memory, newAlliance);
		} else {
			AllianceManager.unsetMemoryKeys(memory);
		}
		
		if (newAllianceDissolved) {
			memory = memoryMap.get(MemKeys.FACTION);
			AllianceManager.unsetMemoryKeys(memory);
		}
		
		ExerelinUtilsFaction.grantCommission(dialog.getInteractionTarget());
		
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, "exerelin_faction_changed");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(null, "exerelin_faction_changed", null);
		FactionChangedEvent event = (FactionChangedEvent)eventSuper;
		event.reportEvent(oldFaction, newFaction, isDefection ? "switch" : "join", dialog.getInteractionTarget());
		
		str = StringHelper.substituteToken(str, "$theOldFaction", oldFaction.getDisplayNameWithArticle());
		str = StringHelper.substituteToken(str, "$theNewFaction", newFaction.getDisplayNameWithArticle());
		text.addParagraph(str, Misc.getPositiveHighlightColor());
		return true;
	}
}