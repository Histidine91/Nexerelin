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
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;


public class LeaveFaction extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		SectorAPI sector = Global.getSector();
		
		String newFactionId = ExerelinConstants.PLAYER_NPC_ID;
		FactionAPI newFaction = sector.getFaction(newFactionId);
		String oldFactionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI oldFaction = sector.getFaction(oldFactionId);
		TextPanelAPI text = dialog.getTextPanel();
		String str = StringHelper.getString("exerelin_factions", "leftFaction");
		
		PlayerFactionStore.loadIndependentPlayerRelations(true);
		PlayerFactionStore.setPlayerFactionId(newFactionId);
		ExerelinUtilsReputation.syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);
		
		MemoryAPI memory = memoryMap.get(MemKeys.PLAYER);
		memory.set("$faction", newFaction, 0);
		memory.set("$factionId", newFactionId, 0);
		
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(null, "exerelin_faction_changed");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(null, "exerelin_faction_changed", null);
		FactionChangedEvent event = (FactionChangedEvent)eventSuper;
		event.reportEvent(oldFaction, newFaction, "leave", dialog.getInteractionTarget());
		//ExerelinUtilsFaction.revokeCommission(oldFactionId);
		
		str = StringHelper.substituteToken(str, "$theOldFaction", oldFaction.getDisplayNameWithArticle());
		text.addParagraph(str);
		return true;
	}
}