package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import java.util.Map;

public class RaiseRelations extends CovertOpsBase {

	public RaiseRelations(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	protected CovertActionResult rollSuccess() {
		return covertActionRoll("raiseRelationsSuccessChance", null, "raiseRelationsDetectionChanceFail", playerInvolved);
	}

	@Override
	public void onSuccess() {
		float effectMin = getConfigFloat("raiseRelationsEffectMin");
		float effectMax = getConfigFloat("raiseRelationsEffectMax");
		ExerelinReputationAdjustmentResult repResult = adjustRelations(
				agentFaction, targetFaction, effectMin, effectMax, null, null, null, true);

		reportEvent(repResult);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = NO_EFFECT;
		if (result.isDetected())
		{
			float effectMin = getConfigFloat("raiseRelationsRepLossOnDetectionMin");
			float effectMax = getConfigFloat("raiseRelationsRepLossOnDetectionMax");
			repResult = adjustRelations(
					agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.FAVORABLE, null, RepLevel.INHOSPITABLE, true);
		}
		reportEvent(repResult);
	}
	
	
	@Override
	protected String getEventId() {
		return "exerelin_agent_raise_relations";
	}

	@Override
	protected float getAlertLevel() {
		return 0;
	}
	
}
