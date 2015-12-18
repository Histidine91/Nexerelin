package exerelin.campaign.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import java.util.ArrayList;
import java.util.List;


public class InvasionFleetEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(InvasionFleetEvent.class);
	protected Map<String, Object> params;
	protected MarketAPI target;
	protected int dp;
	public boolean done;
	protected float age;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		target = null;
		dp = 0;
		age = 0;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		target = (MarketAPI)params.get("target");
		dp = (int)(float)params.get("dp");
	}
		
	@Override
	public void startEvent()
	{
		
	}
	
	public void reportStart()
	{
		MessagePriority priority = MessagePriority.SECTOR;
		String stage = "start";
		if (faction.getId().equals(PlayerFactionStore.getPlayerFactionId()))
			stage = "start_player";
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
	}
	
	public void endEvent(FleetReturnReason reason, SectorEntityToken reportSource)
	{
		if (done) return;
		done = true;
		String stage = reason.toString().toLowerCase();
		if (faction.getId().equals(PlayerFactionStore.getPlayerFactionId()))
			stage += "_player";
		
		log.info("Ending invasion event: " + stage);
		Global.getSector().reportEventStage(this, stage, reportSource, MessagePriority.SECTOR);
	}

	@Override
	public void advance(float amount)
	{

	}
	
	@Override
	public String getEventName() {
		return (faction.getDisplayName() + " invasion fleet event");
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
		LocationAPI loc = target.getContainingLocation();
		String locName = loc.getName();
		if (loc instanceof StarSystemAPI)
			locName = "the " + ((StarSystemAPI)loc).getName();
		int dpEstimate = Math.round(dp/10f) * 10;
		 
		String targetFactionStr = targetFaction.getEntityNamePrefix();
		String theTargetFactionStr = targetFaction.getDisplayNameWithArticle();
		map.put("$sender", faction.getEntityNamePrefix());
		map.put("$target", target.getName());
		map.put("$targetLocation", locName);
		map.put("$targetFaction", targetFactionStr);
		map.put("$TargetFaction", Misc.ucFirst(targetFactionStr));
		map.put("$theTargetFaction", theTargetFactionStr);
		map.put("$TheTargetFaction", Misc.ucFirst(theTargetFactionStr));
		map.put("$dp", "" + dpEstimate);
		return map;
	}
		
		@Override
		public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
				addTokensToList(result, "$dp");
		return result.toArray(new String[0]);
	}
	
	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
	
	public static enum FleetReturnReason {
		MISSION_COMPLETE, ALREADY_CAPTURED, NO_LONGER_HOSTILE, MARINE_LOSSES, SHIP_LOSSES, OTHER
	}
}