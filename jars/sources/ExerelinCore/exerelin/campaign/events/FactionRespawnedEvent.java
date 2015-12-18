package exerelin.campaign.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;


public class FactionRespawnedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionRespawnedEvent.class);
	private static final int DAYS_TO_KEEP = 90;
	
	float age;
	boolean existedBefore;
	private Map<String, Object> params;
		
	public boolean done;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		age = 0;
		existedBefore = false;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		existedBefore = (boolean)params.get("existedBefore");
	}
		
	@Override
	public void advance(float amount)
	{
		if (done)
		{
			return;
		}
		age = age + Global.getSector().getClock().convertToDays(amount);
		if (age > DAYS_TO_KEEP)
		{
			done = true;
			return;
		}
	}

	@Override
	public void startEvent()
	{
		MessagePriority priority = MessagePriority.SECTOR;
		String stage = "new";
		if (existedBefore) stage = "respawned";
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$clusterName", SectorManager.getFirstStarName());
		return map;
	}
	
	@Override
	public String getEventName() {
		return (Misc.ucFirst(faction.getDisplayName()) + " arrives in Exerelin");
	}
	
	@Override
	public String getCurrentImage() {
		return faction.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return faction.getCrest();
	}
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
}