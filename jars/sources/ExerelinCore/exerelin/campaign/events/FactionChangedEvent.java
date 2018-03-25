package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;


public class FactionChangedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionChangedEvent.class);
	
	private FactionAPI oldFaction;
	private FactionAPI newFaction;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
		
	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "factionChanged");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
	public void reportEvent(FactionAPI oldFaction, FactionAPI newFaction, String stage, SectorEntityToken entity)
	{
		this.oldFaction = oldFaction;
		this.newFaction = newFaction;
		Global.getSector().reportEventStage(this, stage, entity, MessagePriority.DELIVER_IMMEDIATELY);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String oldFactionStr = ExerelinUtilsFaction.getFactionShortName(oldFaction);
		String theOldFactionStr = oldFaction.getDisplayNameWithArticle();
		String newFactionStr = ExerelinUtilsFaction.getFactionShortName(newFaction);
		String theNewFactionStr = newFaction.getDisplayNameWithArticle();
		String sender = newFaction.getId().equals(ExerelinConstants.PLAYER_NPC_ID) ? 
				ExerelinUtilsFaction.getFactionShortName(oldFaction): ExerelinUtilsFaction.getFactionShortName(newFaction);
		map.put("$sender", sender);
		map.put("$oldFaction", oldFactionStr);
		map.put("$theOldFaction", theOldFactionStr);
		map.put("$OldFaction", Misc.ucFirst(oldFactionStr));
		map.put("$TheOldFaction", Misc.ucFirst(theOldFactionStr));
		map.put("$newFaction", newFactionStr);
		map.put("$theNewFaction", theNewFactionStr);
		map.put("$NewFaction", Misc.ucFirst(newFactionStr));
		map.put("$TheNewFaction", Misc.ucFirst(theNewFactionStr));
		return map;
	}
	
	/*
	@Override
	public String getCurrentImage() {
		if (newFaction.getId().equals(ExerelinConstants.PLAYER_NPC_ID)) return oldFaction.getLogo();
		return newFaction.getLogo();
	}
	*/
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}