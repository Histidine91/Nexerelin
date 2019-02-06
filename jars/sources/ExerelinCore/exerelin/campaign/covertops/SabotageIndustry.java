package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class SabotageIndustry extends CovertOpsAction {
	
	protected Industry industry;

	public SabotageIndustry(AgentIntel agentIntel, MarketAPI market, Industry industry, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.industry = industry;
	}
	
	@Override
	public void onSuccess() {
		float effectMin = getDef().effect.one;
		float effectMax = getDef().effect.two;
		float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;

		float disruptDays = industry.getDisruptedDays();
		industry.setDisrupted(Math.min(disruptDays, effect));

		repResult = adjustRepIfDetected(RepLevel.HOSTILE, null);
		reportEvent(repResult);
	}

	@Override
	public void onFailure() {
		repResult = adjustRepIfDetected(RepLevel.INHOSPITABLE, RepLevel.HOSTILE);
		reportEvent(repResult);
	}

	@Override
	public String getActionDefId() {
		return "sabotageIndustry";
	}

	@Override
	protected CovertActionIntel reportEvent(ExerelinReputationAdjustmentResult repResult) {
		return null;
	}
}
