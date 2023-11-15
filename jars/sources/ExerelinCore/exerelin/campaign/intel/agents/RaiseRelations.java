package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.NoArgsConstructor;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor
public class RaiseRelations extends CovertActionIntel {
	
	public static final String MEM_KEY_COOLDOWN = "$nex_agentModifyRelationsCooldown";
	public static final float MODIFY_RELATIONS_COOLDOWN = 30;	// days
	
	public RaiseRelations(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, FactionAPI thirdFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		if (thirdFaction == null) thirdFaction = agentFaction;
		this.thirdFaction = thirdFaction;
	}

	protected Object readResolve() {
		if (thirdFaction == null) thirdFaction = agentFaction;
		return this;
	}
	
	@Override
	public float getTimeNeeded() {
		float cooldown = getModifyRelationsCooldown(targetFaction);
		if (CovertOpsManager.isDebugMode() || NexUtils.isNonPlaytestDevMode())
			cooldown *= 0.05f;
		return super.getTimeNeeded() + cooldown;
	}
	
	@Override
	public boolean shouldAbortIfOwnMarket() {
		return super.shouldAbortIfOwnMarket() || market.getFaction() == thirdFaction;
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
				thirdFaction, targetFaction, effectMin, effectMax, null, null, null, true);
		relation = thirdFaction.getRelationship(targetFaction.getId());
		Global.getLogger(this.getClass()).info("Reputation change: " + repResult.delta);
		
		reportEvent();
		
		DiplomacyManager.getManager().getDiplomacyBrain(targetFaction.getId()).reportDiplomacyEvent(
					thirdFaction.getId(), repResult.delta);
		
		applyMemoryCooldown(targetFaction);
	}

	@Override
	public void onFailure() {
		adjustRepIfDetected(RepLevel.FAVORABLE, RepLevel.INHOSPITABLE);
		reportEvent();
		applyMemoryCooldown(targetFaction);
	}
	
	@Override
	public boolean canRepeat() {
		if (agent.getMarket() == null) return false;
		if (sp != StoryPointUse.NONE) return false;
		return RaiseRelations.canModifyRelations(agent.getMarket().getFaction(), agent);
	}
	
	@Override
	protected boolean isAgentFactionKnown() {
		if (result != null && result.isSuccessful())
			return true;
		return super.isAgentFactionKnown();
	}
	
	@Override
	public void addImages(TooltipMakerAPI info, float width, float pad) {
		String crest1 = isAgentFactionKnown() ? agentFaction.getCrest() : 
				Global.getSector().getFaction(Factions.NEUTRAL).getCrest();
		if (agentFaction != thirdFaction) {
			info.addImages(width, 96, pad, pad, crest1, targetFaction.getCrest(), thirdFaction.getCrest());
		} else {
			info.addImages(width, 128, pad, pad, crest1, targetFaction.getCrest());
		}
		
	}
	
	@Override
	protected List<Pair<String,String>> getStandardReplacements() {
		List<Pair<String,String>> sub = super.getStandardReplacements();
		StringHelper.addFactionNameTokensCustom(sub, "thirdFaction", thirdFaction);
		
		return sub;
	}

	@Override
	protected void reportEvent() {
		timestamp = Global.getSector().getClock().getTimestamp();
		if (ExerelinModPlugin.isNexDev) {
			//Global.getSector().getCampaignUI().addMessage("reportEvent() called in RaiseRelations");
			if (shouldReportEvent()){
				//Global.getSector().getCampaignUI().addMessage("shouldReportEvent() in reportEvent() @ RaiseRelations TRUE;if intel doesn't display, something bad happened.");
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
					||	thirdFaction.isPlayerFaction()){
					Global.getSector().getIntelManager().addIntel(this, !notify);

					if (!notify && ExerelinModPlugin.isNexDev) {
						Global.getSector().getCampaignUI().addMessage("Suppressed agent action notification "
								+ getName() + " due to filter level", Misc.getHighlightColor());
					}
				}
				else Global.getSector().getIntelManager().queueIntel(this);
				//TODO: make it so if an agent action makes 2 factions hostile, add it
			}

			else Global.getSector().getIntelManager().queueIntel(this);

			endAfterDelay();
		}
	}

	@Override
	public void addMainDescPara(TooltipMakerAPI info, float pad) {
		List<Pair<String,String>> replace = getStandardReplacements();
		
		String[] highlights;
		Color[] highlightColors;
		// if player action, desc para string won't contain agentFaction name
		// if not player action, desc para string won't contain targetFaction name
		if (!playerInvolved) {
			highlights = new String[] {agentFaction.getDisplayName(), targetFaction.getDisplayName()};			
			highlightColors = new Color[] {agentFaction.getBaseUIColor(), targetFaction.getBaseUIColor()};
		}
		else {
			highlights = new String[] {targetFaction.getDisplayName(), thirdFaction.getDisplayName()};
			highlightColors = new Color[] {targetFaction.getBaseUIColor(), thirdFaction.getBaseUIColor()};
		}
		
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
		}
		else if (repResult != null && repResult.delta != 0) {
			// show warning message
			info.addPara("This is an error, a relationship change has been set when it should not have been: " 
					+ repResult.delta, pad);
		}
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_raiseRelations");
		if (thirdFaction == null) {
			info.addPara("Error: third faction is null! Abort the action and report this bug", pad);
			return;
		}
		info.addPara(action, pad, thirdFaction.getBaseUIColor(), thirdFaction.getDisplayName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_raiseRelations", true);
		if (thirdFaction == null) {
			info.addPara("Error: third faction is null! Abort the action and report this bug", pad);
			return;
		}
		LabelAPI label = info.addPara(action, pad, color, Misc.getHighlightColor(), thirdFaction.getDisplayName());
		label.setHighlight(thirdFaction.getDisplayName(), Math.round(daysRemaining) + "");
		label.setHighlightColors(thirdFaction.getBaseUIColor(), Misc.getHighlightColor());
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(thirdFaction.getId());
		return tags;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){		
		boolean first = true;
		if (result != null)
		{
			if (result.isSuccessful()) {
				NexUtilsFaction.addFactionNamePara(info, initPad, tc, thirdFaction);
				first = false;
			}
			else if (result.isDetected() || isAgentFactionKnown()) {
				NexUtilsFaction.addFactionNamePara(info, initPad, tc, agentFaction);
				first = false;
			}
		}
		else {
			if (agentFaction != thirdFaction) {
				NexUtilsFaction.addFactionNamePara(info, initPad, tc, thirdFaction);
				first = false;
			}
		}
		NexUtilsFaction.addFactionNamePara(info, first ? initPad : 0, tc, targetFaction);
		
		if (repResult != null && repResult.delta != 0) {
			String relString = NexUtilsReputation.getRelationStr(relation);
			Color relColor = NexUtilsReputation.getRelColor(relation);
			String str = StringHelper.getStringAndSubstituteToken("exerelin_diplomacy", "intelRepCurrentShort",
					"$relationStr", relString);
			info.addPara(str, 0, tc, relColor, relString);
		}
	}

	@Override
	public Set<FactionAPI> dialogGetFactions(AgentOrdersDialog dialog) {
		Set<FactionAPI> factionsSet = new HashSet<>();
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			if (!conf.allowAgentActions) continue;
			factionsSet.add(Global.getSector().getFaction(factionId));
		}
		factionsSet.add(Global.getSector().getFaction(Factions.PLAYER));
		// don't allow raising relationship of target faction with itself
		factionsSet.remove(market.getFaction());
		return factionsSet;
	}

	@Override
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		if (thirdFaction == null) return;

		TextPanelAPI text = dialog.getText();
		String other = thirdFaction.getId();
		String factionName = Nex_FactionDirectoryHelper.getFactionDisplayName(targetFaction);
		Color factionColor = targetFaction.getBaseUIColor();

		text.addPara(getString("dialogInfoHeaderRaiseRelations"), factionColor, factionName);
		dialog.addEffectPara(0, 100);

		// print max relationship if applicable
		if (other.equals(Factions.PLAYER))
			other = PlayerFactionStore.getPlayerFactionId();
		if (!DiplomacyManager.haveRandomRelationships(targetFaction.getId(), other))
		{
			float max = DiplomacyManager.getManager().getMaxRelationship(targetFaction.getId(),	other);
			if (max < 1) {
				String str = StringHelper.getString("exerelin_factions", "relationshipLimit");
				str = StringHelper.substituteToken(str, "$faction1",
						NexUtilsFaction.getFactionShortName(targetFaction));
				str = StringHelper.substituteToken(str, "$faction2",
						NexUtilsFaction.getFactionShortName(thirdFaction));
				String maxStr = NexUtilsReputation.getRelationStr(max);
				str = StringHelper.substituteToken(str, "$relationship", maxStr);
				text.addPara(str, NexUtilsReputation.getRelColor(max), maxStr);
			}
		}
		// print current relationship
		text.addPara(StringHelper.getString("exerelin_factions", "relationshipCurr"),
				targetFaction.getRelColor(thirdFaction.getId()),
				NexUtilsReputation.getRelationStr(targetFaction, thirdFaction));

		super.dialogPrintActionInfo(dialog);

		float cooldown = RaiseRelations.getModifyRelationsCooldown(targetFaction);
		if (cooldown > 0) {
			text.addPara(getString("dialogInfoModifyingRelationsCooldown"));
		}
	}

	@Override
	protected void dialogPopulateMainMenuOptions(AgentOrdersDialog dialog) {
		String str = getString("dialogOption_faction");
		str = StringHelper.substituteToken(str, "$faction", thirdFaction != null ?
				thirdFaction.getDisplayName() : StringHelper.getString("none"));
		dialog.getOptions().addOption(str, AgentOrdersDialog.Menu.FACTION);
		if (dialog.getCachedFactions().isEmpty()) {
			dialog.getOptions().setEnabled(AgentOrdersDialog.Menu.FACTION, false);
		}
	}

	@Override
	protected void dialogPaginatedMenuShown(AgentOrdersDialog dialog, AgentOrdersDialog.Menu menu) {
		if (menu != AgentOrdersDialog.Menu.ACTION_TYPE) return;
		OptionPanelAPI opts = dialog.getDialog().getOptionPanel();
		CovertOpsManager.CovertActionDef def = this.getDef();
		if (!opts.hasOption(def)) return;

		if (!RaiseRelations.canModifyRelations(targetFaction, agent)) {
			opts.setEnabled(def, false);
			String tooltip = getString("dialogTooltipAlreadyModifyingRelations");
			opts.setTooltip(def, tooltip);
		}
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		dialog.getFactions();
	}

	@Override
	public String getDefId() {
		return "raiseRelations";
	}

	@Override
	public String getStrategicActionName() {
		return super.getStrategicActionName() + ": " + this.targetFaction.getDisplayName();
	}

	public static void applyMemoryCooldown(FactionAPI faction) {
		faction.getMemoryWithoutUpdate().set(MEM_KEY_COOLDOWN, true, MODIFY_RELATIONS_COOLDOWN);
	}
	
	public static float getModifyRelationsCooldown(FactionAPI faction) {
		if (!faction.getMemoryWithoutUpdate().contains(MEM_KEY_COOLDOWN))
			return 0;
		return faction.getMemoryWithoutUpdate().getExpire(MEM_KEY_COOLDOWN);
	}
	
	public static boolean canModifyRelations(FactionAPI faction, AgentIntel currAgent) {
		for (AgentIntel agent : CovertOpsManager.getManager().getAgents()) {
			if (agent == currAgent) continue;
			if (isAgentModifyingRelations(agent, faction)) return false;
		}
		return true;
	}
	
	public static boolean isAgentModifyingRelations(AgentIntel agent, FactionAPI faction) {
		
		CovertActionIntel action = agent.getCurrentAction();
		if (action == null) return false;
		if (action instanceof RaiseRelations || action instanceof LowerRelations) {
			return agent.getMarket().getFaction() == faction;
		}
		return false;
	}
}
