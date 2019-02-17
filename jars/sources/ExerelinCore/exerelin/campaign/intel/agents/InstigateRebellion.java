package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.StringHelper;
import java.util.Map;

public class InstigateRebellion extends CovertActionIntel {

	public InstigateRebellion(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
	}

	@Override
	public void onSuccess() {
		RebellionEvent event = RebellionEventCreator.createRebellion(market, agentFaction.getId(), false);
		if (event == null) return;
		
		adjustRepIfDetected(RepLevel.HOSTILE, null);
		reportEvent();
	}
	
	@Override
	public void onFailure() {		
		adjustRepIfDetected(RepLevel.INHOSPITABLE, null);
		reportEvent();
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		// TODO
	}

	@Override
	public String getDefId() {
		return "instigateRebellion";
	}
}
