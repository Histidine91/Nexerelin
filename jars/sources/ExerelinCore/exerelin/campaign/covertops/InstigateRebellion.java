package exerelin.campaign.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import java.util.HashMap;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class InstigateRebellion extends CovertOpsBase {

	public InstigateRebellion(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	protected CovertActionResult rollSuccess() {
		return covertActionRoll(
				"instigateRebellionSuccessChance", 
				"instigateRebellionDetectionChance",
				"instigateRebellionDetectionChanceFail",
				playerInvolved);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRepIfDetected()
	{
		if (!result.isDetected())
		{
			float effectMin = getConfigFloat("instigateRebellionRepLossOnDetectionMin");
			float effectMax = getConfigFloat("instigateRebellionRepLossOnDetectionMax");
			return adjustRelations(agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.HOSTILE, null, null, false);
		}
		else return NO_EFFECT;
	}

	@Override
	public void onSuccess() {
		SectorAPI sector = Global.getSector();
		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(new CampaignEventTarget(market), "nex_rebellion");
		if (eventSuper != null)
			return;
		
		float prepTime = market.getSize() * 2 * MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		Map<String, Object> eventParams = new HashMap<>();
		eventParams.put("rebelFactionId", agentFaction.getId());
		eventParams.put("delay", prepTime);
		sector.getEventManager().startEvent(new CampaignEventTarget(market), "nex_rebellion", eventParams);
		
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		eventParams = makeEventParams(repResult);
		eventParams.put("timeFrame", Misc.getAtLeastStringForDays((int)prepTime));
		reportEvent(eventParams);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		reportEvent(repResult);
	}
	
	@Override
	protected String getEventId() {
		return "nex_instigate_rebellion";
	}

	@Override
	protected float getAlertLevel() {
		return getConfigFloat("instigateRebellionSecurityLevelRise");
	}
	
}
