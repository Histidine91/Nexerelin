package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.intel.agents.AgentIntel.Specialization;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class RecruitAgent extends CovertActionIntel {
	
	public static final int STARTING_LEVEL = 1;
	public static final int MIN_LEVEL = 3;
	
	@Getter @Setter protected Specialization specialization = Specialization.pickRandomSpecialization();
	
	public RecruitAgent(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		repResult = NO_EFFECT;
	}
	
	public void createAgent() {
		PersonAPI pers = Global.getSector().getFaction(Factions.INDEPENDENT).createRandomPerson();
		pers.setRankId(Ranks.AGENT);
		pers.setPostId(Ranks.POST_AGENT);
		
		AgentIntel newAgent = new AgentIntel(pers, Global.getSector().getPlayerFaction(), STARTING_LEVEL);
		newAgent.addSpecialization(specialization);
		newAgent.init();
		newAgent.setMarket(market);
		newAgent.setImportant(true);
		//Global.getSector().getIntelManager().addIntel(newAgent);	// already done in init, but test this
	}
	
	public float getSpecializationDistance() {
		// cross-specialization modifier
		if (specialization == null) return 0;
		if (agent.getSpecializationsCopy().isEmpty()) return 0;
		
		float lowestDist = 999;
		for (Specialization mySpec : agent.getSpecializationsCopy()) {
			float dist = mySpec.getSpecializationDistance(specialization);
			if (dist < lowestDist) lowestDist = dist;
		}
		return lowestDist;
	}
	
	@Override
	public MutableStat getCostStat() {
		MutableStat cost = new MutableStat(0);
		
		int salary = AgentIntel.getSalary(STARTING_LEVEL);
		int hiringBonus = salary * 4;
		float baseCost = hiringBonus * 1.5f;
		cost.modifyFlat("base", baseCost, getString("costBase", true));
		
		// cross-specialization modifier
		if (specialization != null) {
			float dist = getSpecializationDistance();
			cost.modifyMult("specialization", 1 + dist, StringHelper.getString("nex_agents", "specialization", true));
		}
				
		return cost;
	}
	
	@Override
	public float getTimeNeeded() {
		float time = super.getTimeNeeded();
		// cross-specialization modifier
		if (specialization != null) {
			float dist = getSpecializationDistance();
			time *= (1 + dist);
		}
		return time;
	}

	@Override
	public List<Object> dialogGetTargets(AgentOrdersDialog dialog) {
		return new ArrayList<Object>(Arrays.asList(AgentIntel.Specialization.values()));
	}

	@Override
	public CovertOpsManager.CovertActionResult execute() {
		result = CovertOpsManager.CovertActionResult.SUCCESS;
		return super.execute();
	}

	@Override
	protected void onSuccess() {
		createAgent();
		reportEvent();
	}

	@Override
	protected void onFailure() {
		reportEvent();
	}

	@Override
	public boolean shouldAbortIfOwnMarket() {
		return false;
	}

	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_recruitAgent");
		info.addPara(action, pad, Misc.getHighlightColor(), specialization.getName());
	}

	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getString("intelStatus_recruitAgent");
		info.addPara(action, pad, color, Misc.getHighlightColor(), specialization.getName());
	}

	@Override
	public void dialogSetTarget(AgentOrdersDialog dialog, Object target) {
		Specialization prevSpec = specialization;
		specialization = (Specialization)target;
		if (prevSpec != specialization) dialog.printActionInfo();
	}

	@Override
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		String specialization = this.specialization.getName();
		dialog.getText().addPara(getString("dialogInfoHeaderRecruitAgent"), Misc.getHighlightColor(), specialization);
		super.dialogPrintActionInfo(dialog);
	}

	@Override
	protected void dialogPopulateMainMenuOptions(AgentOrdersDialog dialog) {
		String str = getString("dialogOption_target");
		String target = specialization != null? specialization.getName() : StringHelper.getString("none");
		str = StringHelper.substituteToken(str, "$target", target);
		dialog.getOptions().addOption(str, AgentOrdersDialog.Menu.TARGET);
	}

	@Override
	protected void dialogPopulateTargetOptions(final AgentOrdersDialog dialog) {
		for (Object spec : dialog.getCachedTargets()) {
			Specialization spec2 = (Specialization)spec;
			String name = spec2.getName();
			dialog.getOptionsList().add(new Pair<String, Object>(name, spec2));
		}
		dialog.showPaginatedMenu();
	}

	@Override
	protected void dialogPaginatedMenuShown(AgentOrdersDialog dialog, AgentOrdersDialog.Menu menu) {
		if (menu != AgentOrdersDialog.Menu.ACTION_TYPE) return;
		OptionPanelAPI opts = dialog.getDialog().getOptionPanel();
		CovertOpsManager.CovertActionDef def = this.getDef();
		if (!opts.hasOption(def)) return;

		int minRecruitLevel = RecruitAgent.MIN_LEVEL;
		if (dialog.getAgent().level < minRecruitLevel) {
			opts.setEnabled(def, false);
			String tooltip = String.format(getString("dialogTooltipRecruitTooLowLevel"), minRecruitLevel);
			opts.setTooltip(def, tooltip);
			opts.setTooltipHighlights(def, minRecruitLevel + "");
		}

		int maxAgents = CovertOpsManager.getManager().getMaxAgents().getModifiedInt();
		if (maxAgents <= CovertOpsManager.getAgentsStatic().size()) {
			opts.setEnabled(def, false);
			String tooltip = getString("dialogTooltipRecruitMaxAgents");
			opts.setTooltip(def, tooltip);
		}
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		TextPanelAPI text = dialog.getText();
		text.setFontSmallInsignia();
		text.addPara(getString("dialogInfoRecruitAgentSpecialization"));
		text.setFontInsignia();
		dialog.getTargets();
		dialog.printActionInfo();	// since this action's getTargets() call doesn't lead to it
		repResult = NO_EFFECT;
	}

	@Override
	public boolean dialogCanActionProceed(AgentOrdersDialog dialog) {
		return specialization != null;
	}

	@Override
	public boolean showSuccessChance() {
		return false;
	}

	@Override
	public String getDefId() {
		return "recruitAgent";
	}
}
