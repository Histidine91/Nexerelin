package exerelin.campaign.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;


public class VictoryEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(VictoryEvent.class);
	private boolean diplomaticVictory;
	private String victorFactionId;
	private Map<String, Object> params;
	private boolean playerVictory;
	    
	public boolean done;
	public boolean transmitted;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		transmitted = false;
		diplomaticVictory = false;
		playerVictory = false;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		diplomaticVictory = (boolean)params.get("diplomaticVictory");
		victorFactionId = (String)params.get("victorFactionId");
		playerVictory = (boolean)params.get("playerVictory");
	}
		
	@Override
	public void advance(float amount)
	{
		if (done)
		{
			return;
		}
		if (!transmitted)
		{
			MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
			String stage = "conquest";
			if (diplomaticVictory) stage = "diplomatic";
			else if (playerVictory) stage = "conquest_player";
			Global.getSector().reportEventStage(this, stage, Global.getSector().getPlayerFleet(), priority);
			log.info("VICTORY EVENT: " + stage);
			if (playerVictory) 
				Global.getSector().getCampaignUI().addMessage("You have won the game!", Global.getSettings().getColor("textFriendColor"));
			transmitted = true;
		}
	}

	@Override
	public String getEventName() {
		FactionAPI victorFaction = Global.getSector().getFaction(victorFactionId);
		return (victorFaction.getDisplayName() + " victory");
	}
	
	@Override
	public String getCurrentImage() {
		return Global.getSector().getFaction(victorFactionId).getLogo();
	}
	
	@Override
	public String getCurrentMessageIcon() {
		return Global.getSector().getFaction(victorFactionId).getLogo();
	}
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		FactionAPI victorFaction = Global.getSector().getFaction(victorFactionId);
		String victorFactionStr = victorFaction.getEntityNamePrefix();
		String theVictorFactionStr = victorFaction.getDisplayNameWithArticle();
		map.put("$victorFaction", victorFactionStr);
		map.put("$VictorFaction", Misc.ucFirst(victorFactionStr));
		map.put("$theVictorFaction", theVictorFactionStr);
		map.put("$TheVictorFaction", Misc.ucFirst(theVictorFactionStr));
		return map;
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}