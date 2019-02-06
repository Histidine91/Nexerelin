package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import java.util.Map;

public class InstigateRebellion extends CovertOpsAction {

	public InstigateRebellion(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
	}

	@Override
	public void onSuccess() {
		RebellionEvent event = RebellionEventCreator.createRebellion(market, agentFaction.getId(), false);
		if (event == null) return;
		
		repResult = adjustRepIfDetected(RepLevel.HOSTILE, null);
		reportEvent(repResult);
	}
	
	@Override
	public void onFailure() {		
		repResult = adjustRepIfDetected(RepLevel.INHOSPITABLE, null);
		reportEvent(repResult);
	}

	@Override
	public String getActionDefId() {
		return "instigateRebellion";
	}

	@Override
	protected CovertActionIntel reportEvent(ExerelinReputationAdjustmentResult repResult) {
		return null;
	}
}
