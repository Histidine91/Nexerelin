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
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;


public class JoinFaction extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		SectorAPI sector = Global.getSector();
		
		String newFactionId = params.get(0).getString(memoryMap);
		FactionAPI newFaction = sector.getFaction(newFactionId);
		String oldFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI oldFaction = sector.getFaction(oldFactionId);
		TextPanelAPI text = dialog.getTextPanel();
		boolean isDefection = true;
		String str = StringHelper.getString("exerelin_factions", "switchedFactions");
		
		PlayerFactionStore.setPlayerFactionId(newFactionId);
		if (oldFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			PlayerFactionStore.saveIndependentPlayerRelations();
			str = StringHelper.getString("exerelin_factions", "joinedFaction");
			isDefection = false;
		}
		
		// make sure player_npc has no conflicting alliance loyalties
		if (AllianceManager.getFactionAlliance(ExerelinConstants.PLAYER_NPC_ID) != null && !AllianceManager.areFactionsAllied(ExerelinConstants.PLAYER_NPC_ID, newFactionId))
		{
			AllianceManager.leaveAlliance(ExerelinConstants.PLAYER_NPC_ID, false);
		}
		
		ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(newFactionId, false);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$faction", newFaction, 0);
		memory.set("$factionId", newFactionId, 0);
		
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, "exerelin_faction_changed");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(null, "exerelin_faction_changed", null);
		FactionChangedEvent event = (FactionChangedEvent)eventSuper;
		event.reportEvent(oldFaction, newFaction, isDefection ? "switch" : "join", dialog.getInteractionTarget());
		
		ExerelinUtilsFaction.grantCommission(dialog.getInteractionTarget());
		
		str = StringHelper.substituteToken(str, "$theOldFaction", oldFaction.getDisplayNameWithArticle());
		str = StringHelper.substituteToken(str, "$theNewFaction", newFaction.getDisplayNameWithArticle());
		text.addParagraph(str);
		return true;
	}
}