package exerelin.campaign.covertops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.CovertOpsManager.CovertActionDef;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public abstract class CovertOpsAction {
	
	public static final ExerelinReputationAdjustmentResult NO_EFFECT = new ExerelinReputationAdjustmentResult(0);
	
	protected Map<String, Object> params;
	protected MarketAPI market;
	protected AgentIntel agentIntel;
	protected FactionAPI agentFaction;
	protected FactionAPI targetFaction;
	protected boolean playerInvolved = false;
	protected CovertActionResult result;
	protected ExerelinReputationAdjustmentResult repResult;
	
	public CovertOpsAction(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params)
	{
		this.agentIntel = agentIntel;
		this.market = market;
		this.agentFaction = agentFaction;
		this.targetFaction = targetFaction;
		this.playerInvolved = playerInvolved;
		this.params = params;
	}
	
	public abstract String getActionDefId();
	
	public CovertActionDef getDef() {
		return CovertOpsManager.getDef(getActionDefId());
	}
	
	public FactionAPI getAgentFaction() {
		return agentFaction;
	}
	
	public FactionAPI getTargetFaction() {
		return targetFaction;
	}
	    
	/**
	 * Rolls a success/failure and detected/undetected result for the covert action.
	 * @param useAlertLevel If true, modifies success chance by the market's alert level
	 * @return
	 */
    protected CovertActionResult covertActionRoll(boolean useAlertLevel)
    {
		CovertActionDef def = getDef();
        CovertActionResult result = null;
        
		float sChance = def.successChance;
		float sDetectChance = def.detectionChance;
		float fDetectChance = def.detectionChanceFail;
		
        if (useAlertLevel) {
            sChance *= (1 - CovertOpsManager.getAlertLevel(market));
        }
		
		// TODO: modify using agent level
		int agentLevel = agentIntel != null ? agentIntel.getLevel() : 2;
            
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
	
	public CovertActionResult execute()
	{
		result = covertActionRoll(true);
				
		if (result.isSucessful())
			onSuccess();
		else
			onFailure();
		
		if (market != null) CovertOpsManager.modifyAlertLevel(market, getAlertLevelIncrease());
		return result;
	}
	
	public CovertActionResult getResult()	{
		return result;
	}
	
	public ExerelinReputationAdjustmentResult getReputationResult() {
		return repResult;
	}
	
	public void setResult(CovertActionResult result) {
		this.result = result;
	}
	
	public void advance(float days) {
		
	}
	
	protected abstract void onSuccess();
	
	protected abstract void onFailure();
	
	protected ExerelinReputationAdjustmentResult adjustRepIfDetected(
			RepLevel ensureAtBest, RepLevel limit)
	{
		if (result.isDetected())
		{
			ExerelinReputationAdjustmentResult repResult = adjustRelationsFromDetection(
					agentFaction, targetFaction, ensureAtBest, null, limit, false);
			DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					agentFaction.getId(), repResult.delta);
			
			return repResult;
		}
		else return NO_EFFECT;
	}
	
	protected ExerelinReputationAdjustmentResult adjustRelationsFromDetection(FactionAPI faction1, 
			FactionAPI faction2, RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit, boolean useNPCMult)
	{
		float effectMin = -getDef().repLossOnDetect.two;
		float effectMax = -getDef().repLossOnDetect.one;
		return adjustRelations(faction1, faction2, effectMin, effectMax, ensureAtBest, ensureAtWorst, limit, useNPCMult);
	}
	
	protected ExerelinReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, 
			float effectMin, float effectMax, RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit,
			boolean useNPCMult)
	{
		float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved && useNPCMult) effect *= NPC_EFFECT_MULT;
		ExerelinReputationAdjustmentResult repResult = DiplomacyManager.adjustRelations(
				faction1, faction2, effect, ensureAtBest, ensureAtWorst, limit);
		
		// print relationship change
		float delta = repResult.delta;
		if (playerInvolved && delta != 0)
			//&& (faction1.isPlayerFaction() || faction1 == PlayerFactionStore.getPlayerFaction()))
		{
			boolean withPlayer = faction1.isPlayerFaction() || faction1 == PlayerFactionStore.getPlayerFaction();
			InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			if (dialog != null)
			{
				TextPanelAPI text = dialog.getTextPanel();
				String changeStr = delta > 0 ? StringHelper.getString("improvedBy") 
						: StringHelper.getString("reducedBy");
				changeStr = StringHelper.substituteToken(changeStr, "$amount", (int) Math.ceil(repResult.delta * 100f) + "");
				String repStr = NexUtilsReputation.getRelationStr(faction1, faction2);
				
				String str = StringHelper.getString("exerelin_diplomacy", withPlayer ? "repChangeMsg" : "repChangeMsgOther");
				str = StringHelper.substituteFactionTokens(str, faction2);
				str = StringHelper.substituteToken(str, "$changedBy", changeStr);
				if (!withPlayer) str = StringHelper.substituteToken(str, "$otherFaction", 
						ExerelinUtilsFaction.getFactionShortName(faction1), true);
				str = StringHelper.substituteToken(str, "$currentRep", repStr);
				
				text.setFontSmallInsignia();
				text.addParagraph(str);
				Highlights h = new Highlights();
				h.setColors(delta > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), 
						faction1.getRelColor(faction2.getId()));
				h.setText(changeStr, repStr);
				text.setHighlightsInLastPara(h);
				text.setFontInsignia();
			}
		}
		
		return repResult;
	}
	
	/**
	 * Creates a covert ops intel item for the intel screen, and alters diplomacy disposition 
	 * of the target towards the agent's faction.
	 * @param repResult
	 * @return 
	 */
	protected abstract CovertActionIntel reportEvent(ExerelinReputationAdjustmentResult repResult);
	
	public float getAlertLevelIncrease() {
		return getDef().alertLevelIncrease;
	}
}
