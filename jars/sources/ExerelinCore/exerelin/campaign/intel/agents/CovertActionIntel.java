package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
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
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public abstract class CovertActionIntel extends BaseIntelPlugin {
	
	public static final String[] EVENT_ICONS = new String[]{
		"graphics/exerelin/icons/intel/spy4.png",
		"graphics/exerelin/icons/intel/spy4_amber.png",
		"graphics/exerelin/icons/intel/spy4_red.png"
	};
	
	public static final ExerelinReputationAdjustmentResult NO_EFFECT = new ExerelinReputationAdjustmentResult(0);
	public static final boolean ALWAYS_REPORT = true;	// debug
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
	protected MarketAPI agentEscapeDest;
	
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
		Global.getSector().addScript(this);
	}
	
	public ExerelinReputationAdjustmentResult getReputationResult() {
		return repResult;
	}
	
	public void setResult(CovertActionResult result) {
		this.result = result;
	}
	
	public abstract String getActionDefId();
	
	public CovertOpsManager.CovertActionDef getDef() {
		return CovertOpsManager.getDef(getActionDefId());
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
	
	public float getPositiveChanceForLevel(float baseChance) {
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		float failChance = 1 - baseChance;
		failChance *= 1 - 0.15f * (level - 1);
		return 1 - failChance;
	}
	
	public float getNegativeChanceForLevel(float baseChance) {
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		return baseChance * (1 - 0.15f * (level - 1));
	}
	
	public float getEffectMultForLevel() {
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		float mult = 1 + 0.2f * (level - 1);
		return mult;
	}
	    
	/**
	 * Rolls a success/failure and detected/undetected result for the covert action.
	 * @param useAlertLevel If true, modifies success chance by the market's alert level
	 * @return
	 */
    protected CovertActionResult covertActionRoll(boolean useAlertLevel)
    {
		CovertOpsManager.CovertActionDef def = getDef();
        CovertActionResult rollResult = null;
        
		float sChance = def.successChance;
		float sDetectChance = def.detectionChance;
		float fDetectChance = def.detectionChanceFail;
		
        if (useAlertLevel) {
            sChance *= (1 - CovertOpsManager.getAlertLevel(market));
        }
		
		// TODO: modify using security level, special buildings
		sChance = getPositiveChanceForLevel(sChance);
		sDetectChance = getNegativeChanceForLevel(sDetectChance);
		fDetectChance = getNegativeChanceForLevel(fDetectChance);
            
        if (Math.random() < sChance)
        {
            rollResult = CovertActionResult.SUCCESS;
            if (Math.random() < sDetectChance) rollResult = CovertActionResult.SUCCESS_DETECTED;
        }
        else
        {
            rollResult = CovertActionResult.FAILURE;
            if (Math.random() < fDetectChance) rollResult = CovertActionResult.FAILURE_DETECTED;
        }
        return rollResult;
    }
	
	public CovertActionResult execute()
	{
		result = covertActionRoll(true);
				
		if (result.isSucessful())
			onSuccess();
		else
			onFailure();
		
		if (agent != null)
			agent.notifyActionCompleted();
		if (market != null) CovertOpsManager.modifyAlertLevel(market, getAlertLevelIncrease());
		return result;
	}
	
	public CovertActionResult getResult()	{
		return result;
	}
	
	@Override
	public void advanceImpl(float days) {
		super.advanceImpl(days);
	}
	
	protected abstract void onSuccess();
	
	protected abstract void onFailure();
	
	protected void adjustRepIfDetected(RepLevel ensureAtBest, RepLevel limit)
	{
		if (result.isDetected())
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
	
	protected boolean shouldReportEvent() {
		return ALWAYS_REPORT 
				|| playerInvolved
				|| result.isSucessful()
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
		
		info.addPara(StringHelper.getString("nex_agentActions", "intelBulletTarget"), 
				afKnown ? pad : initPad, color, targetFaction.getBaseUIColor(), market.getName());
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
		
		info.addSectionHeading(StringHelper.getString("nex_agentActions", "intelResultHeader"),
				Alignment.MID, opad);
		addResultPara(info, opad);
		addAgentOutcomePara(info, opad);
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
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
			sub.add(new Pair("$agent", agent.getAgent().getNameString()));
		if (market != null) {
			sub.add(new Pair<>("$market", market.getName()));
			sub.add(new Pair<>("$onOrAt", market.getOnOrAt()));
		}			
		
		if (isAgentFactionKnown())
			StringHelper.addFactionNameTokensCustom(sub, "agentFaction", agentFaction);
		else {
			String unknownFaction = StringHelper.getString("nex_agentActions", "unknownFaction");
			String anUnknownFaction = StringHelper.getString("nex_agentActions", "anUnknownFaction");
			sub.add(new Pair<>("$agentFaction", unknownFaction));
			sub.add(new Pair<>("$theAgentFaction", anUnknownFaction));
			sub.add(new Pair<>("$AgentFaction", Misc.ucFirst(unknownFaction)));
			sub.add(new Pair<>("$TheAgentFaction", Misc.ucFirst(unknownFaction)));
		}
		StringHelper.addFactionNameTokensCustom(sub, "faction", targetFaction);
		
		return sub;
	}
	
	public void addImages(TooltipMakerAPI info, float width, float pad) {
		String crest1 = isAgentFactionKnown() ? agentFaction.getCrest() : 
				Global.getSector().getFaction(Factions.INDEPENDENT).getCrest();
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
		if (repResult != null && repResult != NO_EFFECT) {
			DiplomacyIntel.addRelationshipChangePara(info, agentFaction.getId(), targetFaction.getId(), 
					relation, repResult, pad);
		}
	}
	
	public void addAgentOutcomePara(TooltipMakerAPI info, float pad) {
		if (agent == null) return;
		String lastName = agent.getAgent().getName().getLast();
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
					"intelDesc_gainedXPAndLeveledUp", "$agentName", lastName);
			info.addPara(str, pad, hl, xpGain + "", newLevel + "");
		}
		else if (xpGain > 0) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_gainedXP", "$agentName", lastName);
			info.addPara(str, pad, hl, xpGain + "");
		}
		
		if (agentEscapeDest != null) {
			str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"intelDesc_agentExfiltrate", "$agentName", lastName);
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
	public String getIcon() {
		int significance = 0;
		if (result != null) {
			if (result.isDetected()) significance = 1;
			if (repResult.wasHostile && !repResult.isHostile) significance = 1;
			if (repResult.isHostile && !repResult.wasHostile) significance = 2;
		}		
		return EVENT_ICONS[significance];
	}
}
