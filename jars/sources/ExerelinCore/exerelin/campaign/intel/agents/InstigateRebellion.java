package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import java.awt.Color;
import java.util.Map;

public class InstigateRebellion extends CovertActionIntel {

	public InstigateRebellion(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
	}

	@Override
	public void onSuccess() {
		RebellionIntel event = RebellionCreator.getInstance().createRebellion(market, agentFaction.getId());
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
	public void addCurrentActionBullet(TooltipMakerAPI arg0, Color color, float arg1) {
		// TODO
	}

	@Override
	public String getDefId() {
		return "instigateRebellion";
	}
}
