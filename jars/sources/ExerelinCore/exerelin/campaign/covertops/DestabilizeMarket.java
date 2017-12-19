package exerelin.campaign.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.events.covertops.AgentDestabilizeMarketEventForCondition;
import java.util.Map;

public class DestabilizeMarket extends CovertOpsBase {

	public DestabilizeMarket(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	protected CovertActionResult rollSuccess() {
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
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market_for_condition");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(new CampaignEventTarget(market), "exerelin_agent_destabilize_market_for_condition", null);
		AgentDestabilizeMarketEventForCondition event = (AgentDestabilizeMarketEventForCondition)eventSuper;

		int currentPenalty = event.getStabilityPenalty();
		int delta = 1;
		if (currentPenalty < 2) delta = 2;
		if (!playerInvolved) delta = Math.round(delta * NPC_EFFECT_MULT);
		event.increaseStabilityPenalty(delta);
		
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();

		Map<String, Object> eventParams = makeEventParams(repResult);
		eventParams.put("stabilityPenalty", delta);
		
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
