package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.intel.agents.AgentIntel.Specialization;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.util.Map;

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
	public String getDefId() {
		return "recruitAgent";
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
	
}
