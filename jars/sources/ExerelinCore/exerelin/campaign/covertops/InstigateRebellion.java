package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import java.util.Map;

public class InstigateRebellion extends CovertOpsBase {

	public InstigateRebellion(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	public CovertActionResult rollSuccess() {
		return covertActionRoll(
				"instigateRebellionSuccessChance", 
				"instigateRebellionDetectionChance",
				"instigateRebellionDetectionChanceFail",
				playerInvolved);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRepIfDetected()
	{
		if (result.isDetected())
		{
			float effectMin = getConfigFloat("instigateRebellionRepLossOnDetectionMin");
			float effectMax = getConfigFloat("instigateRebellionRepLossOnDetectionMax");
			return adjustRelations(agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.HOSTILE, null, null, false);
		}
		else return NO_EFFECT;
	}

	@Override
	public void onSuccess() {
		RebellionEvent event = RebellionEventCreator.createRebellion(market, agentFaction.getId(), false);
		if (event == null) return;
		
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		Map<String, Object> eventParams = makeEventParams(repResult);
		eventParams.put("timeFrame", event.getDelay());
		reportEvent(eventParams);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		reportEvent(repResult);
	}
	
	@Override
	protected String getEventId() {
		return "nex_instigate_rebellion";
	}

	@Override
	protected float getAlertLevel() {
		return getConfigFloat("instigateRebellionSecurityLevelRise");
	}
	
}
