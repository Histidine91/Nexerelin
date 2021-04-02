package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import static exerelin.campaign.intel.agents.CovertActionIntel.NO_EFFECT;
import static exerelin.campaign.intel.agents.RaiseRelations.applyMemoryCooldown;

import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LowerRelations extends CovertActionIntel {
	
	/*
		To avoid future confusion:
		If successful, repResult is change between target faction and third faction
		If failed and caught, repResult is change between self and target faction,
		repResult2 is change between self and third faction
	*/
	
	protected FactionAPI thirdFaction;
	protected ExerelinReputationAdjustmentResult repResult2;
	protected float relation2;

	public LowerRelations(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, FactionAPI targetFaction, 
			FactionAPI thirdFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.thirdFaction = thirdFaction;
	}
	
	public void setThirdFaction(FactionAPI thirdFaction) {
		this.thirdFaction = thirdFaction;
	}
	
	@Override
	public void reset() {
		relation2 = 0;
		repResult2 = null;
		super.reset();
	}
	
	@Override
	public float getTimeNeeded() {
		return super.getTimeNeeded() + RaiseRelations.getModifyRelationsCooldown(targetFaction);
	}
	
	@Override
	public boolean shouldAbortIfOwnMarket() {
		return super.shouldAbortIfOwnMarket() || market.getFaction() == thirdFaction;
	}
	
	@Override
	protected CovertOpsManager.CovertActionResult covertActionRoll() {
		CovertActionResult result = super.covertActionRoll();
		if (result == CovertActionResult.SUCCESS_DETECTED)
			result = CovertActionResult.SUCCESS;
		return result;
	}

	@Override
	protected void reportEvent() {
		timestamp = Global.getSector().getClock().getTimestamp();
		if (ExerelinModPlugin.isNexDev) {
		Global.getSector().getCampaignUI().addMessage("reportEvent() called in LowerRelations");
		if (shouldReportEvent()){
			Global.getSector().getCampaignUI().addMessage("shouldReportEvent() in reportEvent() @ LowerRelations TRUE;if intel doesn't display, something bad happened.");
		}
		}
		if (shouldReportEvent()) {
			boolean notify = shouldNotify();
			if (NexConfig.nexIntelQueued <= 1) {
				if (NexConfig.nexIntelQueued <= 0
					||	affectsPlayerRep()
					||	playerInvolved
					||	agentFaction == PlayerFactionStore.getPlayerFaction()
					||	targetFaction.isPlayerFaction()
					||	targetFaction == Misc.getCommissionFaction()
					||	thirdFaction == Misc.getCommissionFaction()
					|| 	thirdFaction == PlayerFactionStore.getPlayerFaction()) {
					Global.getSector().getIntelManager().addIntel(this, !notify);

					if (!notify && ExerelinModPlugin.isNexDev) {
						Global.getSector().getCampaignUI().addMessage("Suppressed agent action notification "
								+ getName() + " due to filter level", Misc.getHighlightColor());
					}
				}
				else Global.getSector().getIntelManager().queueIntel(this);
			}

			else Global.getSector().getIntelManager().queueIntel(this);

			endAfterDelay();
		}
	}

	@Override
	public void onSuccess() {
		float mult = getEffectMultForLevel();
		float effectMin = -getDef().effect.two * mult;
		float effectMax = -getDef().effect.one * mult;
		repResult = adjustRelations(
				targetFaction, thirdFaction, effectMin, effectMax, null, null, null, true);
		relation = targetFaction.getRelationship(thirdFaction.getId());
		
		DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					thirdFaction.getId(), repResult.delta);

		reportEvent();
		applyMemoryCooldown(targetFaction);
	}

	@Override
	public void onFailure() {
		repResult = NO_EFFECT;
		repResult2 = NO_EFFECT;
		
		if (result.isDetected())
		{
			repResult = adjustRelationsFromDetection(agentFaction, targetFaction, 
					RepLevel.NEUTRAL, null, RepLevel.HOSTILE, true);
			relation = agentFaction.getRelationship(targetFaction.getId());
			repResult2 = adjustRelationsFromDetection(agentFaction, thirdFaction, 
					RepLevel.NEUTRAL, null, RepLevel.HOSTILE, true);
			relation2 = agentFaction.getRelationship(thirdFaction.getId());
			
			DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					agentFaction.getId(), repResult.delta);
			if (repResult2 != null)
				DiplomacyManager.getManager().getDiplomacyBrain(thirdFaction.getId()).reportDiplomacyEvent(
						agentFaction.getId(), repResult2.delta);
		}
		reportEvent();
		applyMemoryCooldown(targetFaction);
	}
	
	@Override
	protected boolean affectsPlayerRep() {
		if (repResult2 != null && thirdFaction != null && 
				(thirdFaction.isPlayerFaction() || thirdFaction == Misc.getCommissionFaction()))
		{
			return true;
		}
		return super.affectsPlayerRep();
	}
	
	@Override
	public boolean canRepeat() {
		if (agent.getMarket() == null) return false;
		if (hasStoryPoint) return false;
		return RaiseRelations.canModifyRelations(agent.getMarket().getFaction(), agent);
	}
	
	@Override
	protected boolean shouldNotify() {
		if (repResult2 != null && repResult2.wasHostile != repResult2.isHostile)
			return true;
		return super.shouldNotify();
	}
	
	@Override
	public void addImages(TooltipMakerAPI info, float width, float pad) {
		String crest1 = isAgentFactionKnown() ? agentFaction.getCrest() : 
				Global.getSector().getFaction(Factions.NEUTRAL).getCrest();
		info.addImages(width, 96, pad, pad, crest1, targetFaction.getCrest(), thirdFaction.getCrest());
	}
	
	@Override
	protected List<Pair<String,String>> getStandardReplacements() {
		List<Pair<String,String>> sub = super.getStandardReplacements();
		StringHelper.addFactionNameTokensCustom(sub, "thirdFaction", thirdFaction);
		
		return sub;
	}
	
	@Override
	public void addMainDescPara(TooltipMakerAPI info, float pad) {
		List<Pair<String,String>> replace = getStandardReplacements();
		
		String[] highlights = new String[] {agentFaction.getDisplayName(), 
			targetFaction.getDisplayName(), thirdFaction.getDisplayName()};
		Color[] highlightColors = new Color[] {agentFaction.getBaseUIColor(), 
			targetFaction.getBaseUIColor(), thirdFaction.getBaseUIColor()};
		
		addPara(info, getDescStringId(), replace, highlights, highlightColors, pad);
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result.isSuccessful()) {
			DiplomacyIntel.addRelationshipChangePara(info, targetFaction.getId(), thirdFaction.getId(), 
					relation, repResult, pad);
		}
		else if (result.isDetected()) {
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation, repResult, pad);
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation2, repResult, pad);
		}
		else if (repResult != null && repResult.delta != 0) {
			// show warning message
			info.addPara("This is an error, a relationship change has been set when it should not have been: " 
					+ repResult.delta, pad);
		}
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_lowerRelations");
		if (thirdFaction == null) {
			info.addPara("Error: third faction is null! Abort the action and report this bug", pad);
			return;
		}
		info.addPara(action, pad, thirdFaction.getBaseUIColor(), thirdFaction.getDisplayName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_lowerRelations", true);
		if (thirdFaction == null) {
			info.addPara("Error: third faction is null! Abort the action and report this bug", pad, color);
			return;
		}
		LabelAPI label = info.addPara(action, pad, color, Misc.getHighlightColor(), thirdFaction.getDisplayName());
		label.setHighlight(thirdFaction.getDisplayName(), Math.round(daysRemaining) + "");
		label.setHighlightColors(thirdFaction.getBaseUIColor(), Misc.getHighlightColor());
	}
	
	@Override
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		boolean afKnown = isAgentFactionKnown();
		if (afKnown)
			NexUtilsFaction.addFactionNamePara(info, initPad, color, agentFaction);
		NexUtilsFaction.addFactionNamePara(info, afKnown ? pad : initPad, color, targetFaction);
		NexUtilsFaction.addFactionNamePara(info, pad, color, thirdFaction);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(thirdFaction.getId());
		return tags;
	}

	@Override
	public String getDefId() {
		return "lowerRelations";
	}
}
