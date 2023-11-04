package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.NoArgsConstructor;

import java.awt.*;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class InfiltrateCell extends CovertActionIntel {

	public static final float MAX_SLEEPER_TIME_REMAINING = 90f;
	
	LuddicPathBaseIntel base;
	
	public InfiltrateCell(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		repResult = NO_EFFECT;
	}

	@Override
	public void advanceImpl(float amount) {
		super.advanceImpl(amount);

		checkCellDissolved();
	}

	@Override
	public void onSuccess() {
		targetFaction = Global.getSector().getFaction(Factions.LUDDIC_PATH);
		removeLuddicCell();
		int size = market.getSize();
		if (agentFaction != market.getFaction()) {
			repResult = adjustRelations(agentFaction, market.getFaction(), size * 0.01f, size * 0.01f, null, null, null, false);
			relation = agentFaction.getRelationship(market.getFactionId());
		}
		
		//adjustRepIfDetected(RepLevel.INHOSPITABLE, null);
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
		targetFaction = Global.getSector().getFaction(Factions.LUDDIC_PATH);
		//adjustRepIfDetected(null, RepLevel.INHOSPITABLE);
		reportEvent();
	}
	
	public void removeLuddicCell() {
		MarketConditionAPI cond = market.getCondition(Conditions.PATHER_CELLS);
		if (cond == null)
			return;
		LuddicPathCells cellCond = (LuddicPathCells)(cond.getPlugin());
		LuddicPathCellsIntel cellIntel = cellCond.getIntel();
		
		boolean sleeper = cellIntel.getSleeperTimeout() > 0;
		if (!sleeper) base = LuddicPathCellsIntel.getClosestBase(market);
		if (base != null && (base.isEnding() || base.isEnded()))
			base = null;

		float disruptDur = Global.getSettings().getFloat("patherCellDisruptionDuration");
		
		// kill cell
		/*
		cellIntel.endAfterDelay();
		cellIntel.sendUpdateIfPlayerHasIntel(LuddicPathCellsIntel.UPDATE_DISSOLVED, false);
		*/
		// don't kill cell, disrupt instead
		boolean alreadyDisrupted = cellIntel.getSleeperTimeout() > MAX_SLEEPER_TIME_REMAINING;
		boolean nonDisruptedSleeper = cellIntel.isSleeper() && cellIntel.getSleeperTimeout() <= 0;
		cellIntel.makeSleeper(disruptDur * 2);
		cellIntel.sendUpdateIfPlayerHasIntel(LuddicPathCellsIntel.UPDATE_DISRUPTED, false);
		ListenerUtil.reportCellDisrupted(cellIntel);

		if (!alreadyDisrupted && HostileActivityEventIntel.get() != null && market.getFaction().isPlayerFaction()) {
			int perCell = Global.getSettings().getInt("HA_patherBasePerActiveCell");

			int points = -1 * perCell;
			//if (nonDisruptedSleeper) points /= 2;	// halve points if it was a sleeper cell; don't feel like actually making the distinction
			HACellInfiltratedFactor factor = new HACellInfiltratedFactor(points);
			HostileActivityEventIntel.get().addFactor(factor);
		}
		
		// locate base
		if (base != null && base.isHidden())
			base.makeKnown();
	}

	protected void checkCellDissolved() {
		MarketConditionAPI cond = market.getCondition(Conditions.PATHER_CELLS);
		if (cond == null) {
			abort();
			agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ABORTED, false);
			return;
		}
		LuddicPathCells cellCond = (LuddicPathCells)(cond.getPlugin());
		LuddicPathCellsIntel cellIntel = cellCond.getIntel();
		if (cellIntel.isEnding() || cellIntel.isEnded()) {
			abort();
			agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ABORTED, false);
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
	
		if (result != null && result.isSuccessful() && base != null 
				&& base.getEntity() != null)
		{
			LocationAPI loc = base.getEntity().getContainingLocation();
			info.addPara(getString("intelBulletBaseLoc"), 0, tc, 
					Misc.getHighlightColor(), loc.getNameWithTypeIfNebula());
		}
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
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSuccessful() && base != null 
				&& base.getEntity() != null)
		{
			String str = getString("cellBaseLocation");
			String baseName = base.getEntity().getName();
			LocationAPI loc = base.getEntity().getContainingLocation();
			String locStr = base.getEntity().getContainingLocation().getNameWithLowercaseType();
			String locStrShort = locStr;
			if (loc instanceof StarSystemAPI) {
				locStrShort = ((StarSystemAPI)loc).getBaseName();
			}
			str = StringHelper.substituteToken(str, "$baseName", baseName);
			str = StringHelper.substituteToken(str, "$location", locStr);
			LabelAPI label = info.addPara(str, pad);
			label.setHighlight(baseName, locStrShort);
			label.setHighlightColors(targetFaction.getBaseUIColor(), Misc.getHighlightColor());
		}
		if (repResult != null && repResult.delta != 0) {
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), market.getFaction().getId(), 
					relation, repResult, pad);
		}
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_infiltrateCell");
		info.addPara(action, pad, targetFaction.getBaseUIColor(), targetFaction.getDisplayName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_infiltrateCell", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		dialog.printActionInfo();
	}

	@Override
	public boolean dialogCanShowAction(AgentOrdersDialog dialog) {
		MarketAPI market = dialog.getAgentMarket();
		if (market == null) return false;
		if (!market.hasCondition(Conditions.PATHER_CELLS)) return false;
		MarketConditionAPI cond = market.getCondition(Conditions.PATHER_CELLS);
		LuddicPathCells cellCond = (LuddicPathCells)(cond.getPlugin());
		LuddicPathCellsIntel cellIntel = cellCond.getIntel();
		return cellIntel.getSleeperTimeout() <= InfiltrateCell.MAX_SLEEPER_TIME_REMAINING;
	}

	@Override
	public String getDefId() {
		return "infiltrateCell";
	}
}
