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
import exerelin.campaign.AllianceManager;
import exerelin.campaign.AllianceManager.Alliance;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;


public class LeaveFaction extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		SectorAPI sector = Global.getSector();
		
		String newFactionId = "player_npc";
		FactionAPI newFaction = sector.getFaction(newFactionId);
		Alliance newAlliance = null;
		String oldFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI oldFaction = sector.getFaction(oldFactionId);
		Alliance oldAlliance = AllianceManager.getFactionAlliance(oldFactionId);
		boolean stayInAlliance = params.get(0).getBoolean(memoryMap);
		
		TextPanelAPI text = dialog.getTextPanel();
		String str = StringHelper.getString("exerelin_factions", "leftFaction");
		
		if (stayInAlliance && oldAlliance != null) {
			newAlliance = oldAlliance;
			
			PlayerFactionStore.setPlayerFactionId(newFactionId);
		} else {
			PlayerFactionStore.loadIndependentPlayerRelations(true);
			PlayerFactionStore.setPlayerFactionId(newFactionId);
			ExerelinUtilsReputation.syncFactionRelationshipsToPlayer("player_npc");
		}
		
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, "exerelin_faction_changed");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(null, "exerelin_faction_changed", null);
		FactionChangedEvent event = (FactionChangedEvent)eventSuper;
		event.reportEvent(oldFaction, newFaction, "leave", dialog.getInteractionTarget());
		
		str = StringHelper.substituteToken(str, "$theOldFaction", oldFaction.getDisplayNameWithArticle());
		text.addParagraph(str, Misc.getHighlightColor());
		
		if (newAlliance != null)
		{
			AllianceManager.setPlayerInteractionTarget(dialog.getInteractionTarget());
			AllianceManager.joinAllianceStatic(newFactionId, newAlliance);
			AllianceManager.setPlayerInteractionTarget(null);
			
			ExerelinUtilsReputation.syncPlayerRelationshipsToFaction("player_npc", true); //?
			
			str = StringHelper.getString("exerelin_alliances", "joinedAlliance");
			str = StringHelper.substituteToken(str, "$NewAlliance", newAlliance.name);
			text.addParagraph(str, Misc.getPositiveHighlightColor());			
		}
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$faction", newFaction, 0);
		memory.set("$factionId", newFactionId, 0);
		memory.set("$theFaction", newFaction.getDisplayNameWithArticle(), 0);
		
		if (newAlliance != null) {
			memory.set("$isInAlliance", true, 0);
			memory.set("$allianceId", newAlliance.name, 0);
		} else {
			memory.set("$isInAlliance", false, 0);
			memory.unset("$allianceId");
		}
		
		return true;
	}
}