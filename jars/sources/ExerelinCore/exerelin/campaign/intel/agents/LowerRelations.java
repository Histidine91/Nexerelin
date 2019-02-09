package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.intel.DiplomacyIntel;
import static exerelin.campaign.intel.agents.CovertActionIntel.NO_EFFECT;
import exerelin.utilities.ExerelinUtilsFaction;
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
	}
	
	@Override
	public void addImages(TooltipMakerAPI info, float width, float pad) {
		String crest1 = isAgentFactionKnown() ? agentFaction.getCrest() : 
				Global.getSector().getFaction(Factions.INDEPENDENT).getCrest();
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
		if (result.isSucessful()) {
			DiplomacyIntel.addRelationshipChangePara(info, targetFaction.getId(), thirdFaction.getId(), 
					relation, repResult, pad);
		}
		else if (result.isDetected()) {
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation, repResult, pad);
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation2, repResult, pad);
		}
	}
	
	@Override
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		boolean afKnown = isAgentFactionKnown();
		if (afKnown)
			ExerelinUtilsFaction.addFactionNamePara(info, initPad, color, agentFaction);
		ExerelinUtilsFaction.addFactionNamePara(info, afKnown ? pad : initPad, color, targetFaction);
		ExerelinUtilsFaction.addFactionNamePara(info, pad, color, thirdFaction);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(thirdFaction.getId());
		return tags;
	}

	@Override
	public String getActionDefId() {
		return "lowerRelations";
	}
}
