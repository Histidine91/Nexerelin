package exerelin.campaign.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import java.util.HashMap;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public abstract class CovertOpsBase {
	
	public static final ExerelinReputationAdjustmentResult NO_EFFECT = new ExerelinReputationAdjustmentResult(0);
	
	protected Map<String, Object> params = null;
	protected MarketAPI market = null;
	protected FactionAPI agentFaction = null;
	protected FactionAPI targetFaction = null;
	protected boolean playerInvolved = false;
	protected CovertActionResult result = null;
	
	public CovertOpsBase(MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params)
	{
		this.market = market;
		this.agentFaction = agentFaction;
		this.targetFaction = targetFaction;
		this.playerInvolved = playerInvolved;
		this.params = params;
	}
	
	protected static Object getConfigValue(String key)
	{
		if (key == null) return null;
		Map<String, Object> conf = CovertOpsManager.getConfig();
		if (!conf.containsKey(key)) return null;
		return conf.get(key);
	}
	
	protected static float getConfigFloat(String key)
	{
		Object result = getConfigValue(key);
		if (result == null) return 0;
		return (float)(double)result;
	}
	
	protected CovertActionResult covertActionRoll(String sChance, String sDetectChance, String fDetectChance, boolean playerInvolved)
    {
        return covertActionRoll(
			getConfigFloat(sChance), 
			getConfigFloat(sDetectChance), 
			getConfigFloat(fDetectChance),
			false, null, playerInvolved
		);
    }
	
    protected CovertActionResult covertActionRoll(double sChance, double sDetectChance, double fDetectChance, boolean playerInvolved)
    {
        return covertActionRoll(sChance, sDetectChance, fDetectChance, false, null, playerInvolved);
    }
    
    protected CovertActionResult covertActionRoll(double sChance, double sDetectChance, double fDetectChance, boolean useAlertLevel, MarketAPI market, boolean playerInvolved)
    {
        CovertActionResult result = null;
        
        if (useAlertLevel)
        {
            sChance = sChance * (1 - CovertOpsManager.getAlertLevel(market));
        }
        
        if (playerInvolved)
        {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (!playerFleet.isTransponderOn())
            {
                sDetectChance *= 0.5f;
                fDetectChance *= 0.75f;
            }
        }
            
        if (Math.random() < sChance)
        {
            result = CovertActionResult.SUCCESS;
            if (Math.random() < sDetectChance) result = CovertActionResult.SUCCESS_DETECTED;
        }
        else
        {
            result = CovertActionResult.FAILURE;
            if (Math.random() < fDetectChance) result = CovertActionResult.FAILURE_DETECTED;
        }
        return result;
    }
	
	protected abstract CovertActionResult rollSuccess();
	
	public CovertActionResult execute()
	{
		result = rollSuccess();
				
		if (result.isSucessful())
			onSuccess();
		else
			onFailure();
		
		if (market != null) CovertOpsManager.modifyAlertLevel(market, getAlertLevel());
		return result;
	}
	
	protected abstract void onSuccess();
	
	protected abstract void onFailure();
	
	
	protected ExerelinReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, 
			float effectMin, float effectMax, RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit,
			boolean useNPCMult)
	{
		float effect = -MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved && useNPCMult) effect *= NPC_EFFECT_MULT;
		ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(
				agentFaction, targetFaction, effect, ensureAtBest, ensureAtWorst, limit);
		return repResult;
	}
	
	protected void reportEvent(ExerelinReputationAdjustmentResult repResult)
	{
		if (Math.abs(repResult.delta) < 0.01f && !playerInvolved)
			return;
		
		Map<String, Object> params = makeEventParams(repResult);
		Global.getSector().getEventManager().startEvent(new CampaignEventTarget(market), getEventId(), params);
	}
	
	protected abstract float getAlertLevel();
	
	protected abstract String getEventId();
	
	protected Map<String, Object> makeEventParams(ExerelinReputationAdjustmentResult repResult)
    {
        HashMap<String, Object> params = new HashMap<>();
        params.put("agentFaction", agentFaction);
		params.put("result", result);
        params.put("playerInvolved", playerInvolved);
        params.put("repEffect", repResult.delta);
		params.put("repResult", repResult);
        return params;
    }
}
