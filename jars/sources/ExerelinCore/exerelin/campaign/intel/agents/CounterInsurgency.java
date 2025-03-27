package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.NoArgsConstructor;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class CounterInsurgency extends CovertActionIntel {

	public CounterInsurgency(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction,
                             FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		repResult = NO_EFFECT;
	}

	@Override
	public void advanceImpl(float amount) {
		super.advanceImpl(amount);

		checkRebellionOngoing();
	}

	@Override
	public void onSuccess() {
		RebellionIntel reb = RebellionIntel.getOngoingEvent(market);
		if (reb == null) return;

		targetFaction = reb.getRebelFaction();
		damageRebellion(reb);
		int size = market.getSize();
		if (agentFaction != market.getFaction()) {
			repResult = adjustRelations(agentFaction, market.getFaction(), size * 0.01f, size * 0.015f, null, null, null, false);
			relation = agentFaction.getRelationship(market.getFactionId());
		}
		
		adjustRepIfDetected(null, null);
		reportEvent();
	}
	
	@Override
	protected MutableStat getSuccessChance(boolean checkSP) {
		CovertOpsManager.CovertActionDef def = getDef();
		int level = getLevel();
		MutableStat stat = new MutableStat(0);
		
		if (checkSP && sp.preventFailure()) {
			stat.modifyFlat("baseChance", 999, getString("baseChance", true));
			return stat;
		}
		
		// base chance
		float base = def.successChance * 100;
		if (base <= 0) return stat;
		stat.modifyFlat("baseChance", base, getString("baseChance", true));
		
		// level
		float failChance = 100 - base;
		float failChanceNew = failChance * (1 - 0.15f * (level - 1));
		float diff = failChance - failChanceNew;
		stat.modifyFlat("agentLevel", diff, StringHelper.getString("nex_agents", "agentLevel", true));

		// stability
		float stabilityModifier = 0.7f + (market.getStabilityValue() - 2) * 0.1f;
		stat.modifyMult("stability", stabilityModifier, StringHelper.getString("stability", true));
		
		// buildings
		// note that the buildings are basically on our side, so they _increase_ success chance
		// only if we're on good terms with that faction though
		if (market.getFaction().isAtWorst(Factions.PLAYER, RepLevel.FAVORABLE)) {
			for (Industry ind : market.getIndustries()) {
				float mult = CovertOpsManager.getIndustrySuccessMult(ind);
				if (mult < 1) {
					// e.g. if mult is 0.25 (75% reduction in normal success chance), bonus is 0.75
					float bonus = 1 - mult;
					stat.modifyMult(ind.getId(), 1 + bonus, ind.getNameForModifier());
				}
			}
			
			// AI admin
			if (market.getAdmin() != null && market.getAdmin().isAICore()) {
				float bonus = 1 - AI_ADMIN_SUCCESS_MULT;
				stat.modifyMult("aiAdmin", 1 + bonus, StringHelper.getString("nex_agents", "aiAdmin", true));
			}
		}
		
		return stat;
	}

	@Override
	public void onFailure() {
		RebellionIntel reb = RebellionIntel.getOngoingEvent(market);
		if (reb == null) return;
		targetFaction = reb.getRebelFaction();

		adjustRepIfDetected(null, RepLevel.INHOSPITABLE);
		reportEvent();
	}
	
	public void damageRebellion(RebellionIntel reb) {
		float mult = getEffectMultForLevel();
		float effectMin = getDef().effect.one * mult;
		float effectMax = getDef().effect.two * mult;

		float curr = reb.getRebelStrength();
		reb.setRebelStrength(curr - MathUtils.getRandomNumberInRange(effectMin, effectMax));
		reb.getIntelModifier().modifyFlat("agentCOIN", 1);
	}

	protected void checkRebellionOngoing() {
		RebellionIntel reb = RebellionIntel.getOngoingEvent(market);
		if (reb == null) {
			abort();
			agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ABORTED, false);
			return;
		}
	}
	
	@Override
	public boolean allowOwnMarket() {
		return true;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		boolean afKnown = isAgentFactionKnown();
		if (afKnown)
			NexUtilsFaction.addFactionNamePara(info, initPad, tc, agentFaction);
		
		info.addPara(getString("intelBulletTarget"), afKnown ? 0 : initPad, tc, 
				market.getFaction().getBaseUIColor(), market.getName());
	}
	
	@Override
	public void addMainDescPara(TooltipMakerAPI info, float pad) {
		List<Pair<String,String>> replace = getStandardReplacements();
		
		String[] highlights = new String[] {agentFaction.getDisplayName(), 
			targetFaction.getDisplayName(), market.getName()};
		Color[] highlightColors = new Color[] {agentFaction.getBaseUIColor(), 
			targetFaction.getBaseUIColor(), market.getFaction().getBaseUIColor()};
		
		addPara(info, getDescStringId(), replace, highlights, highlightColors, pad);
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_counterInsurgency");
		info.addPara(action, pad, targetFaction.getBaseUIColor(), targetFaction.getDisplayName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_counterInsurgency", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		dialog.printActionInfo();
	}

	@Override
	public boolean dialogCanShowAction(AgentOrdersDialog dialog) {
		RebellionIntel reb = RebellionIntel.getOngoingEvent(market);
		if (reb == null) return false;

		return true;
	}

	@Override
	public String getDefId() {
		return "counterInsurgency";
	}
}
