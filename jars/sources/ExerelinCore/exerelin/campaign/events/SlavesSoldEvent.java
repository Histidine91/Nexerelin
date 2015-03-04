package exerelin.campaign.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import exerelin.campaign.ExerelinReputationPlugin;


public class SlavesSoldEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(SlavesSoldEvent.class);
	protected static final int DAYS_TO_KEEP = 60;
	
	protected float repPenalty;
        protected List<String> factionsToNotify;
	protected Map<String, Object> params;
        
	protected float age;	
	protected boolean done;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
                repPenalty = 0;
                factionsToNotify = new ArrayList<>();
		age = 0;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
                factionsToNotify = (List<String>)params.get("factionsToNotify");
		repPenalty = (Float)params.get("repPenalty");
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
	public void startEvent() {
		// we can set the reputation change only on message delivery
		// but problem is, the token replacement method needs to know the relationship change NOW
		//DiplomacyManager.adjustRelations(event, market, market.getFaction(), otherFaction, delta);
		MessagePriority priority = MessagePriority.ENSURE_DELIVERY;
		Global.getSector().reportEventStage(this, "report", market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
			public void beforeDelivery(CommMessageAPI message) {
                                for (String factionId : factionsToNotify)
                                {
                                        ExerelinReputationPlugin.adjustPlayerReputation(Global.getSector().getFaction(factionId), repPenalty);
                                }
			}
		});
	}

	@Override
	public String getEventName() {
		return ("Slave trade on " + market.getName());
	}
	
	/*
	@Override
	public String getCurrentImage() {
		return newOwner.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return newOwner.getLogo();
	}
	*/
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$repPenaltyAbs", "" + (int)Math.ceil(Math.abs(repPenalty*100f)));
                map.put("$location", market.getPrimaryEntity().getContainingLocation().getName());
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$repPenaltyAbs");
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
}