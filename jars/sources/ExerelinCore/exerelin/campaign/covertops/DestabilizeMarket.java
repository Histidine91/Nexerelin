package exerelin.campaign.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import java.util.Map;

public class DestabilizeMarket extends CovertOpsBase {

	public DestabilizeMarket(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	public CovertActionResult rollSuccess() {
		return covertActionRoll(
				"destabilizeSuccessChance", 
				"destabilizeDetectionChance", 
				"destabilizeDetectionChanceFail",
				playerInvolved);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRepIfDetected()
	{
		if (result.isDetected())
		{
			float effectMin = getConfigFloat("destabilizeRepLossOnDetectionMin");
			float effectMax = getConfigFloat("destabilizeRepLossOnDetectionMax");
			return adjustRelations(agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.INHOSPITABLE, null, 
					result.isSucessful() ? null : RepLevel.HOSTILE,
					false);
			
		}
		else return NO_EFFECT;
	}

	@Override
	public void onSuccess() {
		SectorAPI sector = Global.getSector();
		RecentUnrest.get(market).add(4, agentFaction.getDisplayName() + " agent destabilization");	// TODO externalize
		
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();

		Map<String, Object> eventParams = makeEventParams(repResult);
		eventParams.put("stabilityPenalty", 4);
		
		reportEvent(eventParams);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		reportEvent(repResult);
	}
	
	@Override
	protected String getEventId() {
		return "exerelin_agent_destabilize_market";
	}

	@Override
	protected float getAlertLevel() {
		return getConfigFloat("destabilizeSecurityLevelRise");
	}
	
}
