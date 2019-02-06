package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import java.util.Map;

public class LowerRelations extends CovertOpsAction {
	
	protected FactionAPI thirdFaction;
	protected ExerelinReputationAdjustmentResult repResult2;

	public LowerRelations(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, 
			FactionAPI thirdFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.thirdFaction = thirdFaction;
	}

	@Override
	public void onSuccess() {
		float effectMin = -getDef().effect.two;
		float effectMax = -getDef().effect.one;
		repResult = adjustRelations(
				targetFaction, thirdFaction, effectMin, effectMax, null, null, RepLevel.HOSTILE, true);

		reportEvent(repResult, null);
	}

	@Override
	public void onFailure() {
		repResult = NO_EFFECT;
		repResult2 = NO_EFFECT;
		
		if (result.isDetected())
		{
			repResult = adjustRelationsFromDetection(agentFaction, targetFaction, 
					RepLevel.NEUTRAL, null, RepLevel.HOSTILE, true);
			repResult2 = adjustRelationsFromDetection(agentFaction, thirdFaction, 
					RepLevel.NEUTRAL, null, RepLevel.HOSTILE, true);
		}
		reportEvent(repResult, repResult2);
	}
	
	/*
		To avoid future confusion:
		If successful, repResult is change between target faction and third faction
		If failed and caught, repResult is change between self and target faction,
		repResult2 is change between self and third faction
	*/
	protected void reportEvent(ExerelinReputationAdjustmentResult repResult, 
			ExerelinReputationAdjustmentResult repResult2)
	{
		if (!playerInvolved)
		{
			if (Math.abs(repResult.delta) < 0.01f) return;
			if (repResult2 != null && Math.abs(repResult2.delta) < 0.01f) return;
		}
		
		// TODO: report event
		
		if (result.isDetected())
		{
			DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					agentFaction.getId(), repResult.delta);
			if (repResult2 != null)
				DiplomacyManager.getManager().getDiplomacyBrain(thirdFaction.getId()).reportDiplomacyEvent(
						agentFaction.getId(), repResult2.delta);
		}
		else
		{
			DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					thirdFaction.getId(), repResult.delta);
		}
	}	

	@Override
	public String getActionDefId() {
		return "lowerRelations";
	}

	@Override
	protected CovertActionIntel reportEvent(ExerelinReputationAdjustmentResult repResult) {
		return null;
	}
}
