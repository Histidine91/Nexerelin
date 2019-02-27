package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;

public class RaiseRelations extends CovertActionIntel {

	public RaiseRelations(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
	}
	
	@Override
	protected CovertOpsManager.CovertActionResult covertActionRoll() {
		CovertOpsManager.CovertActionResult result = super.covertActionRoll();
		if (result == CovertOpsManager.CovertActionResult.SUCCESS_DETECTED)
			result = CovertOpsManager.CovertActionResult.SUCCESS;
		return result;
	}

	@Override
	public void onSuccess() {
		float mult = getEffectMultForLevel();
		float effectMin = getDef().effect.one * mult;
		float effectMax = getDef().effect.two * mult;
		repResult = adjustRelations(
				agentFaction, targetFaction, effectMin, effectMax, null, null, null, true);
		relation = agentFaction.getRelationship(targetFaction.getId());
		Global.getLogger(this.getClass()).info("Reputation change: " + repResult.delta);

		reportEvent();
		
		DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					agentFaction.getId(), repResult.delta);
	}

	@Override
	public void onFailure() {
		adjustRepIfDetected(RepLevel.FAVORABLE, RepLevel.INHOSPITABLE);
		reportEvent();
	}
	
	@Override
	protected boolean isAgentFactionKnown() {
		if (result != null && result.isSucessful())
			return true;
		return super.isAgentFactionKnown();
	}
	
	@Override
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		boolean afKnown = isAgentFactionKnown();
		if (afKnown)
			ExerelinUtilsFaction.addFactionNamePara(info, initPad, color, agentFaction);
		ExerelinUtilsFaction.addFactionNamePara(info, afKnown ? pad : initPad, color, targetFaction);
		
		if (repResult != null && repResult != NO_EFFECT) {
			String relString = NexUtilsReputation.getRelationStr(relation);
			Color relColor = NexUtilsReputation.getRelColor(relation);
			String str = StringHelper.getStringAndSubstituteToken("exerelin_diplomacy", "intelRepCurrentShort",
					"$relationStr", relString);
			info.addPara(str, pad, color, relColor, relString);
		}
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_raiseRelations");
		info.addPara(action, pad);
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_raiseRelations", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}

	@Override
	public String getDefId() {
		return "raiseRelations";
	}	
}
