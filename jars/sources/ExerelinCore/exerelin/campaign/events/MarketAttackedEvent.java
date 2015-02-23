package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;

/**
 * This is really just RecentUnrestEvent with a different condition 
 * but I have the feeling it'll be too messy to try and override its condition token, 
 * so we copypasta the whole thing instead of inheriting
 */
public class MarketAttackedEvent extends BaseEventPlugin {

	public static final float DAYS_PER_STAGE = 10f;
	private float elapsedDays = 0f;
	private int stabilityPenalty = 0;
	private String conditionToken = null;
	
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
		conditionToken = market.addCondition("exerelin_market_attacked_condition", true, this);
	}
	
        @Override
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		elapsedDays += days;
		
		if (elapsedDays >= DAYS_PER_STAGE) {
			elapsedDays -= DAYS_PER_STAGE;
			stabilityPenalty--;
			market.reapplyCondition(conditionToken);
		}
		
		if (stabilityPenalty <= 0) {
			endEvent();
		}
	}
	
	private boolean ended = false;
	private void endEvent() {
		if (market != null && conditionToken != null) {
			market.removeSpecificCondition(conditionToken);
		}
		ended = true;
	}

        @Override
	public boolean isDone() {
		return ended;
	}

	public int getStabilityPenalty() {
		return stabilityPenalty;
	}

	public void setStabilityPenalty(int stabilityPenalty) {
		this.stabilityPenalty = stabilityPenalty;
		if (stabilityPenalty <= 0) {
			endEvent();
		} else {
			market.reapplyCondition(conditionToken);
		}
	}
	
	public void increaseStabilityPenalty(int penalty) {
		this.stabilityPenalty += penalty;
		if (stabilityPenalty <= 0) {
			endEvent();
		} else {
			market.reapplyCondition(conditionToken);
		}
	}
	
	public void reduceStabilityPenalty(int penalty) {
		this.stabilityPenalty -= penalty;
		if (stabilityPenalty <= 0) {
			endEvent();
		} else {
			market.reapplyCondition(conditionToken);
		}
	}
	
        @Override
	public String getEventName() {
		return "Recent attack on " + market.getName();
	}
        
        @Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}
}
