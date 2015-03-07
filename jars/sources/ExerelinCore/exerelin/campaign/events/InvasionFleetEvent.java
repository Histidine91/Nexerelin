package exerelin.campaign.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;


public class InvasionFleetEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(InvasionFleetEvent.class);
	private static final int DAYS_TO_KEEP = 30;
	private Map<String, Object> params;
	private MarketAPI target;
	public boolean done;
	private float age;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		target = null;
		age = 0;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		target = (MarketAPI)params.get("target");
	}
		
	@Override
	public void startEvent()
	{
		MessagePriority priority = MessagePriority.SECTOR;
		String stage = "report";
		if (faction.getId().equals(PlayerFactionStore.getPlayerFactionId()))
			stage = "report_player";
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
	}

	@Override
	public void advance(float amount)
	{
		if (done)
			return;
		
		age = age + Global.getSector().getClock().convertToDays(amount);
		if (age > DAYS_TO_KEEP)
		{
			done = true;
			return;
		}
	}
	
	@Override
	public String getEventName() {
		return (faction.getDisplayName() + " invasion fleet launched");
	}
	
	@Override
	public String getCurrentImage() {
		return faction.getLogo();
	}
			
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		FactionAPI targetFaction = target.getFaction();
		LocationAPI loc = market.getContainingLocation();
		String locName = loc.getName();
		if (loc instanceof StarSystemAPI)
			locName = "the " + ((StarSystemAPI)loc).getName();
		String targetFactionStr = targetFaction.getEntityNamePrefix();
		String theTargetFactionStr = targetFaction.getDisplayNameWithArticle();
		map.put("$sender", faction.getEntityNamePrefix());
		map.put("$target", target.getName());
		map.put("$targetLocation", locName);
		map.put("$targetFaction", targetFactionStr);
		map.put("$TargetFaction", Misc.ucFirst(targetFactionStr));
		map.put("$theTargetFaction", theTargetFactionStr);
		map.put("$TheTargetFaction", Misc.ucFirst(theTargetFactionStr));
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