package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class DestroyCommodityStocks extends CovertOpsAction {
	
	protected String commodityId;

	public DestroyCommodityStocks(AgentIntel agentIntel, MarketAPI market, String commodityId, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.commodityId = commodityId;
	}

	@Override
	public void onSuccess() {
		float effectMin = getDef().effect.one;
		float effectMax = getDef().effect.two;
		float effect = Math.round(MathUtils.getRandomNumberInRange(effectMin, effectMax));
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;
		
		// TODO apply removal
		// should probably apply a market condition that expires on its own
		CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
		
		
		repResult = adjustRepIfDetected(RepLevel.INHOSPITABLE, null);
		
		reportEvent(repResult);
	}
	
	@Override
	public void onFailure() {
		repResult = adjustRepIfDetected(RepLevel.INHOSPITABLE, RepLevel.HOSTILE);
		reportEvent(repResult);
	}

	@Override
	public String getActionDefId() {
		return "destroyStocks";
	}

	@Override
	protected CovertActionIntel reportEvent(ExerelinReputationAdjustmentResult repResult) {
		return null;
	}
}
