package exerelin.campaign.events;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import java.util.HashMap;

/**
 * comment goes here
 */
public class MarketCapturedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(MarketCapturedEvent.class);
	
		private SectorEntityToken location;
		private FactionAPI newOwner;
		private FactionAPI oldOwner;
		private boolean playerInvolved;
		private Map<String, Object> params;
	
		private boolean done;
		
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		newOwner = (FactionAPI)params.get("newOwner");
		oldOwner = (FactionAPI)params.get("oldOwner");
		playerInvolved = (Boolean)params.get("playerInvolved");
		//log.info("Params newOwner: " + newOwner);
		//log.info("Params oldOwner: " + oldOwner);
		//log.info("Params playerInvolved: " + playerInvolved);
	}
		
	@Override
	public void advance(float amount)
	{
		if (done)
			return;
				
		String stage = "report";
		MessagePriority priority = MessagePriority.SECTOR;
		if (playerInvolved) 
		{
			stage = "report_player";
			//priority = MessagePriority.ENSURE_DELIVERY;
		}
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
		done = true;
	}

	@Override
	public String getEventName() {
		return (newOwner.getDisplayName() + " captures " + market.getName() + " from " + oldOwner.getDisplayName());
	}
		
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}

	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$newOwner", newOwner.getDisplayNameLong());
		map.put("$oldOwner", oldOwner.getDisplayNameLong());
		map.put("$oldOwnerWithArticle", oldOwner.getDisplayNameLongWithArticle());
		return map;
	}

	public boolean isDone() {
		return false;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
}
