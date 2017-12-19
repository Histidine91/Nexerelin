package exerelin.campaign.covertops;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import static exerelin.campaign.CovertOpsManager.log;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class DestroyFood extends CovertOpsBase {

	public DestroyFood(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(market, agentFaction, targetFaction, playerInvolved, params);
	}
		
	@Override
	protected CovertActionResult rollSuccess() {
		return covertActionRoll(
				"destroyFoodSuccessChance", 
				"destroyFoodDetectionChance", 
				"destroyFoodDetectionChanceFail",
				playerInvolved);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRepIfDetected()
	{
		if (result.isDetected())
		{
			float effectMin = getConfigFloat("destroyFoodRepLossOnDetectionMin");
			float effectMax = getConfigFloat("destroyFoodRepLossOnDetectionMax");
			return adjustRelations(agentFaction, targetFaction, -effectMax, -effectMin, RepLevel.INHOSPITABLE, null, 
					result.isSucessful() ? null : RepLevel.HOSTILE,
					false);
		}
		else return NO_EFFECT;
	}

	@Override
	public void onSuccess() {
		float effectMin = getConfigFloat("destroyFoodEffectMin");
		float effectMax = getConfigFloat("destroyFoodEffectMax");
		float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;

		float foodDestroyed = (float)Math.pow(market.getSize(), 2) * effect;

		CommodityOnMarketAPI food = market.getCommodityData(Commodities.FOOD);
		float before = food.getStockpile();
		food.removeFromStockpile(foodDestroyed);
		float after = food.getStockpile();
		log.info("Remaining food: " + food.getStockpile());
		
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		Map<String, Object> eventParams = makeEventParams(repResult);
		eventParams.put("foodDestroyed", before - after);
		
		reportEvent(eventParams);
	}

	@Override
	public void onFailure() {
		ExerelinReputationAdjustmentResult repResult = adjustRepIfDetected();
		reportEvent(repResult);
	}
	
	@Override
	protected String getEventId() {
		return "exerelin_saboteur_destroy_food";
	}

	@Override
	protected float getAlertLevel() {
		return getConfigFloat("destroyFoodSecurityLevelRise");
	}
}
