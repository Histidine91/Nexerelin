package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.DiplomacyIntel;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public abstract class CovertActionIntel extends BaseIntelPlugin {
	
	public static final String[] EVENT_ICONS = new String[]{
		"graphics/exerelin/icons/intel/spy4.png",
		"graphics/exerelin/icons/intel/spy4_amber.png",
		"graphics/exerelin/icons/intel/spy4_red.png"
	};
	
	public static final ExerelinReputationAdjustmentResult NO_EFFECT = new ExerelinReputationAdjustmentResult(0);
	public static final boolean ALWAYS_REPORT = false;	// debug
	public static final int DEFAULT_AGENT_LEVEL = 2;
	
	protected Map<String, Object> params;
	protected MarketAPI market;
	protected AgentIntel agent;
	protected FactionAPI agentFaction;
	protected FactionAPI targetFaction;
	protected boolean playerInvolved = false;
	protected CovertActionResult result;
	protected ExerelinReputationAdjustmentResult repResult;
	protected float relation;
	protected int xpGain = -1;
	protected int newLevel = -1;
	protected float days;
	protected float cost;
	protected float daysRemaining;
	protected Float injuryTime;
	protected MarketAPI agentEscapeDest;
	
	/**
	 *
	 * @param agent Agent executing the covert action (null for NPC actions)
	 * @param market Target market. Usually the market the agent is on, but 
	 * for {@code Travel} this is the destination market.
	 * @param agentFaction Faction conducting the covert action.
	 * @param targetFaction
	 * @param playerInvolved
	 * @param params
	 */
	public CovertActionIntel(AgentIntel agent, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params)
	{
		this.agent = agent;
		this.market = market;
		this.agentFaction = agentFaction;
		this.targetFaction = targetFaction;
		this.playerInvolved = playerInvolved;
		this.params = params;
	}
	
	public void init() {
		daysRemaining = getTimeNeeded();
	}
	
	public void activate() {
		init();
		days = daysRemaining;
		cost = getCost();
		Global.getSector().addScript(this);
	}
	
	public ExerelinReputationAdjustmentResult getReputationResult() {
		return repResult;
	}
	
	public void setMarket(MarketAPI market) {
		this.market = market;
	}
	
	public void setResult(CovertActionResult result) {
		this.result = result;
	}
	
	public abstract String getDefId();
	
	public CovertOpsManager.CovertActionDef getDef() {
		return CovertOpsManager.getDef(getDefId());
	}	
	
	public String getActionName(boolean uppercase) {
		String name = getDef().name;
		if (!uppercase) {
			name = Misc.ucFirst(name.toLowerCase());
		}
		return name;
	}
	
	public FactionAPI getAgentFaction() {
		return agentFaction;
	}
	
	public FactionAPI getTargetFaction() {
		return targetFaction;
	}
	
	public boolean isPlayerInvolved() {
		return playerInvolved;
	}
	
	public float getTimeNeeded() {
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		float time = getDef().time;
		time *= 1 - 0.1f * (level - 1);
		
		if (getDef().costScaling) {
			time *= 1 + 0.25f * (market.getSize() - 3);
		}
		if (CovertOpsManager.DEBUG_MODE)
			time *= 0.1f;
		
		return time;
	}
	
	public int getCost() {
		return getCostStat().getModifiedInt();
	}
	
	public MutableStat getCostStat() {
		MutableStat cost = new MutableStat(0);
		
		int baseCost = getDef().baseCost;
		cost.modifyFlat("base", baseCost, getString("costBase", true));
		
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		float levelMult = 1 - 0.1f * (level - 1);
		//cost.modifyMult("levelMult", baseCost, getString("costLevelMult", true));
		
		if (getDef().costScaling) {
			int sizeMult = Math.max(market.getSize() - 3, 1);
			cost.modifyMult("sizeMult", sizeMult, getString("costSizeMult", true));
		}
		
		return cost;
	}
	
	public int getAbortRefund() {
		float daysLeftMult = daysRemaining/days;
		if (days - daysRemaining <= 1) daysLeftMult = 1;
		
		return Math.round(cost * daysLeftMult);
	}
	
	public float getEffectMultForLevel() {
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		float mult = 1 + 0.2f * (level - 1);
		return mult;
	}
	
	protected MutableStat getSuccessChance() {
		CovertOpsManager.CovertActionDef def = getDef();
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		MutableStat stat = new MutableStat(0);
		
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
		if (def.useIndustrySecurity) {
			for (Industry ind : market.getIndustries()) {
				float mult = CovertOpsManager.getIndustrySuccessMult(ind);
				if (mult != 1)
					stat.modifyMult(ind.getId(), mult, ind.getNameForModifier());
			}
		}
		
		// alert level
		if (def.useAlertLevel) {
			float mult = 1 - CovertOpsManager.getAlertLevel(market);
			stat.modifyMult("alertLevel", mult, StringHelper.getString("nex_agents", "alertLevel", true));
		}
		
		return stat;
	}
	
	protected MutableStat getDetectionChance(boolean fail) {
		CovertOpsManager.CovertActionDef def = getDef();
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		MutableStat stat = new MutableStat(0);
		
		// base chance
		float base = fail ? def.detectionChance : def.detectionChanceFail;
		if (base <= 0) return stat;
		base *= 100;
		
		stat.modifyFlat("baseChance", base, getString("baseChance", true));
		
		// level
		float levelMult = 1 - 0.15f * (level - 1);
		if (levelMult < 1)
			stat.modifyMult("agentLevel", levelMult, StringHelper.getString("nex_agents", "agentLevel", true));
		
		// buildings
		for (Industry ind : market.getIndustries()) {
			float mult = CovertOpsManager.getIndustryDetectionMult(ind);
			if (mult != 1)
				stat.modifyMult(ind.getId(), mult, ind.getNameForModifier());
		}
		
		return stat;
	}
	
	public boolean canAbort() {
		return true;
	}
	
	public void abort() {
		if (playerInvolved) {
			int refund = getAbortRefund();
			Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
		}
		if (agent != null) {
			agent.currentAction = null;
		}
		endImmediately();
	}
		
	/**
	 * Rolls a success/failure and detected/undetected result for the covert action.
	 * @return
	 */
	protected CovertActionResult covertActionRoll()
	{
		CovertOpsManager.CovertActionDef def = getDef();
		CovertActionResult rollResult = null;
		
		Random random = CovertOpsManager.getRandom(market);
		
		MutableStat sChance = getSuccessChance();
		MutableStat sDetectChance = getDetectionChance(false);
		MutableStat fDetectChance = getDetectionChance(true);
			
		if (random.nextFloat() * 100 < sChance.getModifiedValue())
		{
			rollResult = CovertActionResult.SUCCESS;
			if (random.nextFloat() * 100 < sDetectChance.getModifiedValue())
				rollResult = CovertActionResult.SUCCESS_DETECTED;
		}
		else
		{
			rollResult = CovertActionResult.FAILURE;
			if (random.nextFloat() * 100 < fDetectChance.getModifiedValue())
				rollResult = CovertActionResult.FAILURE_DETECTED;
		}
		return rollResult;
	}
	
	public CovertActionResult execute()
	{
		targetFaction = market.getFaction();
		if (playerInvolved) {
			agentFaction = PlayerFactionStore.getPlayerFaction();	// in case player changed faction since launching action
			if (agentFaction == targetFaction) {
				agentFaction = Global.getSector().getPlayerFaction();
			}
		}
		
		result = covertActionRoll();
				
		if (result.isSucessful())
			onSuccess();
		else
			onFailure();
		
		if (agent != null) {
			gainAgentXP();
			agent.notifyActionCompleted();
			rollInjury();			
		}
			
		if (market != null) CovertOpsManager.modifyAlertLevel(market, getAlertLevelIncrease());
		return result;
	}
	
	public boolean rollInjury() {
		if (result.isSucessful() && !result.isDetected())
			return false;
		
		float chance = CovertOpsManager.getBaseInjuryChance(result.isSucessful());
		chance *= getDef().detectionChance;
		if (CovertOpsManager.getRandom(market).nextFloat() <= chance)
		{
			Injury injury = new Injury(agent, agentFaction, targetFaction, playerInvolved, params);
			agent.setCurrentAction(injury);
			injury.activate();
			injuryTime = injury.days;
			return true;
		}
		return false;
	}
	
	public CovertActionResult getResult()	{
		return result;
	}
	
	@Override
	public void advanceImpl(float amount) {
		super.advanceImpl(amount);
		
		if (!allowOwnMarket() && market.getFaction() == agent.faction)
		{
			abort();
			agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ABORTED, false);
			return;
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		if (result == null) {
			daysRemaining -= days;
			if (daysRemaining <= 0)
				execute();
		}
	}
	
	protected abstract void onSuccess();
	
	protected abstract void onFailure();
	
	protected void adjustRepIfDetected(RepLevel ensureAtBest, RepLevel limit)
	{
		if (agentFaction == targetFaction)
			repResult = NO_EFFECT;
		else if (result.isDetected())
		{
			repResult = adjustRelationsFromDetection(
					agentFaction, targetFaction, ensureAtBest, null, limit, false);
			DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					agentFaction.getId(), repResult.delta);
			relation = agentFaction.getRelationship(targetFaction.getId());
		}
		else repResult = NO_EFFECT;
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
		ExerelinReputationAdjustmentResult result = DiplomacyManager.adjustRelations(
				faction1, faction2, effect, ensureAtBest, ensureAtWorst, limit);
						
		return result;
	}
	
	protected float getXPMult() {
		return 0.5f + 0.5f * (market.getSize() - 2);
	}
	
	protected void gainAgentXP() {
		if (agent == null) return;
		xpGain = (int)(getDef().xp * getXPMult());
		
		int currLevel = agent.level;
		agent.gainXP(xpGain);
		if (agent.level > currLevel) {
			newLevel = agent.level;
		}
	}
	
	public boolean allowOwnMarket() {
		return false;
	}
	
	protected boolean shouldReportEvent() {
		return ALWAYS_REPORT 
				|| playerInvolved
				|| agentFaction == PlayerFactionStore.getPlayerFaction()
				//|| result != null && result.isDetected()
				|| repResult != null && repResult != NO_EFFECT
				|| Global.getSettings().isDevMode();
	}
	
	protected void reportEvent() {
		timestamp = Global.getSector().getClock().getTimestamp();
		if (shouldReportEvent()) {
			Global.getSector().getIntelManager().addIntel(this);
		}
		endAfterDelay();
	}
	
	public float getAlertLevelIncrease() {
		return getDef().alertLevelIncrease;
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = Global.getSector().getPlayerFaction().getBaseUIColor();

		info.addPara(getName(), c, 0);

		Color tc = getBulletColorForMode(mode);
		Color hl = Misc.getHighlightColor();
		float initPad = 3;
		float pad = 0;
		
		bullet(info);
		
		addBulletPoints(info, tc, initPad, pad);
	}
	
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		boolean afKnown = isAgentFactionKnown();
		if (afKnown)
			ExerelinUtilsFaction.addFactionNamePara(info, initPad, color, agentFaction);
		
		info.addPara(getString("intelBulletTarget"), afKnown ? pad : initPad, color, 
				targetFaction.getBaseUIColor(), market.getName());
	}
	
	protected String getName() {
		String str = getDef().name;
		if (result != null) { 
			if (result.isSucessful())
				str += " - " + StringHelper.getString("nex_agents", "verbSuccess", true);
			else
				str += " - " + StringHelper.getString("nex_agents", "verbFailed", true);
		}
		
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		addImages(info, width, opad);
		addMainDescPara(info, opad);
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
		
		info.addSectionHeading(getString("intelResultHeader"), Alignment.MID, opad);
		addResultPara(info, opad);
		addAgentOutcomePara(info, opad);
	}
	
	protected String getDescStringId() {
		String id = "intelDesc_" + getDef().id + "_";
		if (result.isSucessful())
			id += "success";
		else
			id += "failure";
		if (result.isDetected())
			id += "Detected";
		if (playerInvolved)
			id += "Player";
		
		return id;
	}
	
	/**
	 * Is the agent faction known to the player? 
	 * (for whether to conceal some information in report)
	 * @return
	 */
	protected boolean isAgentFactionKnown() {
		if (playerInvolved) return true;
		if (agentFaction.isPlayerFaction()) return true;
		if (agentFaction == PlayerFactionStore.getPlayerFaction()) return true;
		if (result != null && result.isDetected()) return true;
		if (Global.getSettings().isDevMode()) return true;
		
		return false;
	}
	
	protected List<Pair<String,String>> getStandardReplacements() {
		List<Pair<String,String>> sub = new ArrayList<>();
		if (agent != null)
			sub.add(new Pair("$agentName", agent.getAgent().getNameString()));
		if (market != null) {
			sub.add(new Pair<>("$market", market.getName()));
			sub.add(new Pair<>("$onOrAt", market.getOnOrAt()));
		}			
		
		if (isAgentFactionKnown())
			StringHelper.addFactionNameTokensCustom(sub, "agentFaction", agentFaction);
		else {
			String unknownFaction = getString("unknownFaction");
			String anUnknownFaction = getString("anUnknownFaction");
			sub.add(new Pair<>("$agentFaction", unknownFaction));
			sub.add(new Pair<>("$theAgentFaction", anUnknownFaction));
			sub.add(new Pair<>("$AgentFaction", Misc.ucFirst(unknownFaction)));
			sub.add(new Pair<>("$TheAgentFaction", Misc.ucFirst(unknownFaction)));
		}
		StringHelper.addFactionNameTokensCustom(sub, "faction", targetFaction);
		
		return sub;
	}
	
	/**
	 * Adds images to the top of the intel description panel.
	 * @param info
	 * @param width
	 * @param pad
	 */
	public void addImages(TooltipMakerAPI info, float width, float pad) {
		String crest1 = isAgentFactionKnown() ? agentFaction.getCrest() : 
				Global.getSector().getFaction(Factions.NEUTRAL).getCrest();
		info.addImages(width, 128, pad, pad, crest1, targetFaction.getCrest());
	}
	
	public void addPara(TooltipMakerAPI info, String stringId, List<Pair<String,String>> sub, 
		String[] highlights, Color[] highlightColors, float pad) {
		String str = StringHelper.getStringAndSubstituteTokens("nex_agentActions", stringId, sub);
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(highlights);
		label.setHighlightColors(highlightColors);
	}
	
	/**
	 * Generates a general description of the action for the intel item.
	 * @param info
	 * @param pad Padding.
	 */
	public void addMainDescPara(TooltipMakerAPI info, float pad) {
		List<Pair<String,String>> replace = getStandardReplacements();
		
		String[] highlights = new String[] {agentFaction.getDisplayName(), targetFaction.getDisplayName()};
		Color[] highlightColors = new Color[] {agentFaction.getBaseUIColor(), targetFaction.getBaseUIColor()};
		
		addPara(info, getDescStringId(), replace, highlights, highlightColors, pad);
	}
	
	/**
	 * Generates text for the intel item's description, covering the action's effects.
	 * @param info
	 * @param pad Padding.
	 */
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (repResult != null && repResult.delta != 0) {
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation, repResult, pad);
		}
	}
	
	public abstract void addCurrentActionPara(TooltipMakerAPI info, float pad);
	
	public abstract void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad);
	
	public String getActionString(String strId) {
		return getActionString(strId, false);
	}
	
	public String getActionString(String strId, boolean withTime) {
		String action = getString(strId);
		if (withTime) {
			String daysNum = Math.round(daysRemaining) + "";
			String daysStr = RaidIntel.getDaysString(daysRemaining);
			String daysLeftStr = StringHelper.getString("nex_agents", "intelDescCurrActionDaysShort");
			daysLeftStr = StringHelper.substituteToken(daysLeftStr, "$daysStr", daysStr);
			daysLeftStr = String.format(daysLeftStr, daysNum);
			
			action += " (" + daysLeftStr + ")";
		}
		
		return action;
	}
	
	public void addLastMessagePara(TooltipMakerAPI info, float pad) {
		String str = StringHelper.getString("nex_agentActions", "intel_lastMessage_" 
				+ getDefId() + "_" + (result.isSucessful() ? "success" : "failure"));
		str = StringHelper.substituteTokens(str, getStandardReplacements());
		
		info.addPara(str, pad);
	}
	
	public void addAgentOutcomePara(TooltipMakerAPI info, float pad) {
		if (agent == null) return;
		String name = agent.getAgent().getName().getFullName();
		Color hl = Misc.getHighlightColor();
		String str;
		
		if (agent.isDead) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_agentLost", "$agentName", agent.getAgent().getNameString());
			info.addPara(str, pad);
			return;
		}
		
		if (newLevel > -1) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_gainedXPAndLeveledUp", "$agentName", name);
			info.addPara(str, pad, hl, xpGain + "", newLevel + "");
		}
		else if (xpGain > 0) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_gainedXP", "$agentName", name);
			info.addPara(str, pad, hl, xpGain + "");
		}
		
		if (injuryTime != null) {
			CovertActionIntel injuryAction = agent.currentAction;
			
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_injured", "$agentName", name);
			info.addPara(str, pad, Misc.getNegativeHighlightColor(), Math.round(injuryTime) + "");
		}
		
		if (agentEscapeDest != null) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_agentExfiltrate", "$agentName", name);
			info.addPara(str, pad, market.getFaction().getBaseUIColor(), agentEscapeDest.getName());
		}
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		if (repResult != null) {
			if (repResult.wasHostile && !repResult.isHostile) return 15;
			if (repResult.isHostile && !repResult.wasHostile) return 30;
		}
		return 10;
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("nex_agents", "agents", true));
		tags.add(agentFaction.getId());
		tags.add(targetFaction.getId());
		return tags;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (market != null)
			return market.getPrimaryEntity();
		return null;
	}
	
	@Override
	public String getIcon() {
		int significance = 0;
		if (result != null) {
			if (result.isDetected()) significance = 1;
		}
		if (repResult != null) {
			if (repResult.wasHostile && !repResult.isHostile) significance = 1;
			if (repResult.isHostile && !repResult.wasHostile) significance = 2;
		}
		return EVENT_ICONS[significance];
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_agentActions", id, ucFirst);
	}
}
