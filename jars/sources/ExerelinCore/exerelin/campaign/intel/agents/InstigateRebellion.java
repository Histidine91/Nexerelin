package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;

public class InstigateRebellion extends CovertActionIntel {
	
	public static final int MAX_STABILITY = 6;

	public InstigateRebellion(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
	}
	
	@Override
	protected MutableStat getSuccessChance() {
		MutableStat stat = super.getSuccessChance();
		float stabilityModifier = 1.2f - (market.getStabilityValue() - 2) * 0.1f;
		
		stat.modifyMult("stability", stabilityModifier, StringHelper.getString("stability", true));
		
		return stat;
	}
	
	@Override
	public float getEffectMultForLevel() {
		int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
		float mult = 0.7f + 0.15f * (level - 1);
		return mult;
	}

	@Override
	public void onSuccess() {
		RebellionIntel event = RebellionCreator.getInstance().createRebellion(market, agentFaction.getId(), true);
		if (event == null) return;
		
		event.setRebelStrength(event.getRebelStrength() * getEffectMultForLevel());
		
		adjustRepIfDetected(RepLevel.HOSTILE, null);
		reportEvent();
	}
	
	@Override
	public void onFailure() {
		adjustRepIfDetected(RepLevel.HOSTILE, null);
		reportEvent();
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_instigateRebellion");
		info.addPara(action, pad);
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_instigateRebellion", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}

	@Override
	public String getDefId() {
		return "instigateRebellion";
	}
}
