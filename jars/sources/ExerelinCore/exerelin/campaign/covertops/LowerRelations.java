package exerelin.campaign.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import java.util.Map;

public class LowerRelations extends CovertOpsBase {
	
	protected FactionAPI thirdFaction;

	public LowerRelations(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
		thirdFaction = (FactionAPI)params.get("thirdFaction");
	}
		
	@Override
	protected CovertActionResult rollSuccess() {
		return covertActionRoll("lowerRelationsSuccessChance", null, "lowerRelationsDetectionChanceFail", playerInvolved);
	}

	@Override
	public void onSuccess() {
		float effectMin = getConfigFloat("lowerRelationsEffectMin");
		float effectMax = getConfigFloat("lowerRelationsEffectMax");
		
		ExerelinReputationAdjustmentResult repResult = adjustRelations(
				targetFaction, thirdFaction, -effectMax, -effectMin, null, null, RepLevel.HOSTILE, true);

		reportEvent(repResult, null);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = NO_EFFECT;
		ExerelinReputationAdjustmentResult repResult2 = NO_EFFECT;
		
		if (result.isDetected())
		{
			float effectMin = getConfigFloat("lowerRelationsRepLossOnDetectionMin");
            float effectMax = getConfigFloat("lowerRelationsRepLossOnDetectionMax");
			repResult = adjustRelations(
					agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.NEUTRAL, null, RepLevel.HOSTILE, true);
			repResult2 = adjustRelations(
					agentFaction, thirdFaction, -effectMax, -effectMin, RepLevel.NEUTRAL, null, RepLevel.HOSTILE, true);
		}
		reportEvent(repResult, repResult2);
	}
	
	protected void reportEvent(ExerelinReputationAdjustmentResult repResult, 
			ExerelinReputationAdjustmentResult repResult2)
	{
		if (!playerInvolved)
		{
			if (Math.abs(repResult.delta) < 0.01f) return;
			if (repResult2 != null && Math.abs(repResult2.delta) < 0.01f) return;
		}
		
		Map<String, Object> params = makeEventParams(repResult);
		params.put("thirdFaction", thirdFaction);
		if (repResult2 != null)
		{
			params.put("repResult2", repResult2);
		}
		else
		{
			params.put("repEffect2", repResult.delta);
		}
		Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), getEventId(), params);
	}
	
	@Override
	protected String getEventId() {
		return "exerelin_agent_lower_relations";
	}
	
	@Override
	protected float getAlertLevel() {
		return 0;
	}
	
}
