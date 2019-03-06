package exerelin.campaign.events.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import exerelin.utilities.StringHelper;

public class SecurityAlertEvent extends BaseEventPlugin {

	public static final float DAYS_PER_STAGE = 30f;
	public static final float ALERT_LEVEL_DECREMENT = 0.05f;
	protected float elapsedDays = 0f;
	protected float alertLevel = 0;
	//protected String conditionToken = null;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
	
	@Override
	public void startEvent() {
		super.startEvent();
		if (market == null) {
			endEvent();
			return;
		}
		//conditionToken = market.addCondition("exerelin_security_alert_condition", true, this);
	}
	
	@Override
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		elapsedDays += days;
		
		if (elapsedDays >= DAYS_PER_STAGE) {
			elapsedDays -= DAYS_PER_STAGE;
			alertLevel -= ALERT_LEVEL_DECREMENT;
			//market.reapplyCondition(conditionToken);
		}
		
		if (alertLevel <= 0) {
			endEvent();
		}
	}
	
	private boolean ended = false;
	private void endEvent() {
		/*
		if (market != null && conditionToken != null) {
			//market.removeSpecificCondition(conditionToken);
		}
		*/
		ended = true;
	}

	@Override
	public boolean isDone() {
		return ended;
	}

	public float getAlertLevel() {
		return alertLevel;
	}

	public void setAlertLevel(float alertLevel) {
		this.alertLevel = alertLevel;
		if (alertLevel <= 0) {
			endEvent();
		} else {
			//market.reapplyCondition(conditionToken);
		}
	}
	
	public void increaseAlertLevel(float level) {
		this.alertLevel += level;
		if (alertLevel <= 0) {
			endEvent();
		} else {
			//market.reapplyCondition(conditionToken);
		}
	}
	
	public void decreaseAlertLevel(float level) {
		this.alertLevel -= level;
		if (alertLevel <= 0) {
			endEvent();
		} else {
			//market.reapplyCondition(conditionToken);
		}
	}
	
	@Override
	public String getEventName() {
		return StringHelper.getStringAndSubstituteToken("exerelin_events", "securityAlert", 
				"$market", market.getName());
	}
	
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
}
