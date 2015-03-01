package exerelin.campaign.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;

/**
 * this handles the market condition
 * for event reporting in intel screen see AgentDestabilizeMarketEvent
 */
public class AgentDestabilizeMarketEventForCondition extends CovertOpsEventBase {

	public static Logger log = Global.getLogger(AgentDestabilizeMarketEventForCondition.class);
	public static final float DAYS_PER_STAGE = 30f;
	protected float elapsedDays = 0f;
	protected String conditionToken = null;
	protected int stabilityPenalty;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		stabilityPenalty = 0;
	}
	
	@Override
	public void setParam(Object param) {
		super.setParam(param);
		if (params.containsKey("stabilityPenalty"))
			stabilityPenalty = (Integer)params.get("stabilityPenalty");
	}
	
	@Override
	public void startEvent() {
		super.startEvent();
		if (market == null) {
			endEvent();
			return;
		}
		conditionToken = market.addCondition("exerelin_agent_destabilize_condition", true, this);
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
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$stabilityPenalty", "" + stabilityPenalty);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$stabilityPenalty");
		return result.toArray(new String[0]);
	}
		
	@Override
	public String getEventName() {
		return "Agent terror attacks on " + market.getName();
	}
	
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}
}