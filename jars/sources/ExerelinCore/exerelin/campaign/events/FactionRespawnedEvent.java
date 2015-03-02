package exerelin.campaign.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;


public class FactionRespawnedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionRespawnedEvent.class);
	private static final int DAYS_TO_KEEP = 180;
	
	float age;
	boolean originalFaction;
	private Map<String, Object> params;
		
	public boolean done;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		age = 0;
		originalFaction = false;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		originalFaction = (boolean)params.get("originalFaction");
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
		if (originalFaction) stage = "respawned";
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
	}
	
	@Override
	public String getEventName() {
		return (faction.getDisplayName() + " arrives in Exerelin");
	}
	
	@Override
	public String getCurrentImage() {
		return faction.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return faction.getLogo();
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