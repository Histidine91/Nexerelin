package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class SabotageIndustry extends CovertActionIntel {
	
	protected Industry industry;
	protected float disruptTime;

	public SabotageIndustry(AgentIntel agentIntel, MarketAPI market, Industry industry, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.industry = industry;
	}
	
	public void setIndustry(Industry industry) {
		this.industry = industry;
	}
	
	public Industry getIndustry() {
		return industry;
	}
	
	@Override
	public void onSuccess() {
		float effectMin = getDef().effect.one;
		float effectMax = getDef().effect.two;
		float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;

		disruptTime = Math.max(industry.getDisruptedDays(), effect);
		industry.setDisrupted(disruptTime);

		adjustRepIfDetected(RepLevel.HOSTILE, null);
		reportEvent();
	}

	@Override
	public void onFailure() {
		adjustRepIfDetected(RepLevel.INHOSPITABLE, RepLevel.HOSTILE);
		reportEvent();
	}
	
	@Override
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		super.addBulletPoints(info, color, initPad, pad);
		if (result != null && result.isSucessful())
		{
			String industryName = industry.getCurrentName();
			info.addPara(exerelin.utilities.StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"industryDisruptEffect", "$industry", industryName), pad, color, Misc.getHighlightColor(), (int)disruptTime + "");
		}
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSucessful())
		{
			String industryName = industry.getCurrentName();
			info.addPara(exerelin.utilities.StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"industryDisruptEffect", "$industry", industryName) + ".", pad, Misc.getHighlightColor(), (int)disruptTime + "");
		}
		super.addResultPara(info, pad);
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_sabotageIndustry");
		info.addPara(action, pad, Misc.getHighlightColor(), industry.getCurrentName());
	}
	
	@Override
	protected List<Pair<String, String>> getStandardReplacements() {
		List<Pair<String, String>> sub = super.getStandardReplacements();
		sub.add(new Pair<>("$industry", industry.getCurrentName()));
		
		return sub;
	}

	@Override
	public String getDefId() {
		return "sabotageIndustry";
	}
}
