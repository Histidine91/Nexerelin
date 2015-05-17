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


public class FactionChangedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionChangedEvent.class);
	
	private FactionAPI oldFaction;
	private FactionAPI newFaction;
	private Map<String, Object> params;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
		
	@Override
	public String getEventName() {
		return ("Faction changed");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
	public void reportEvent(FactionAPI oldFaction, FactionAPI newFaction, String stage, SectorEntityToken entity)
	{
		this.oldFaction = oldFaction;
		this.newFaction = newFaction;
		Global.getSector().reportEventStage(this, stage, entity, MessagePriority.ENSURE_DELIVERY);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String oldFactionStr = oldFaction.getEntityNamePrefix();
		String theOldFactionStr = oldFaction.getDisplayNameWithArticle();
		String newFactionStr = newFaction.getEntityNamePrefix();
		String theNewFactionStr = newFaction.getDisplayNameWithArticle();
		String sender = newFaction.getId().equals("player_npc") ? oldFaction.getEntityNamePrefix() : newFaction.getEntityNamePrefix();
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
		if (newFaction.getId().equals("player_npc")) return oldFaction.getLogo();
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