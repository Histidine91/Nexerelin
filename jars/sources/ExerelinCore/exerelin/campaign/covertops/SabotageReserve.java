package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.fleets.ResponseFleetManager;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class SabotageReserve extends CovertOpsBase {

	public SabotageReserve(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	protected CovertActionResult rollSuccess() {
		return covertActionRoll(
				"sabotageReserveSuccessChance", 
				"sabotageReserveDetectionChance", 
				"sabotageReserveDetectionChanceFail",
				playerInvolved);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRepIfDetected()
	{
		if (!result.isDetected())
		{
			float effectMin = getConfigFloat("sabotageReserveRepLossOnDetectionMin");
			float effectMax = getConfigFloat("sabotageReserveRepLossOnDetectionMax");
			return adjustRelations(agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.INHOSPITABLE, null, 
					result.isSucessful() ? null : RepLevel.HOSTILE,
					false);
		}
		else return NO_EFFECT;
	}

	@Override
	public void onSuccess() {
		float effectMin = getConfigFloat("sabotageReserveEffectMin");
		float effectMax = getConfigFloat("sabotageReserveEffectMax");
		float effect = -MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;

		float delta = ResponseFleetManager.modifyReserveSize(market, effect);

		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		Map<String, Object> eventParams = makeEventParams(repResult);
		eventParams.put("reserveDamage", -delta);
		
		reportEvent(eventParams);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		reportEvent(repResult);
	}
	
	
	@Override
	protected String getEventId() {
		return "exerelin_saboteur_sabotage_reserve";
	}

	@Override
	protected float getAlertLevel() {
		return getConfigFloat("sabotageReserveSecurityLevelRise");
	}
}
