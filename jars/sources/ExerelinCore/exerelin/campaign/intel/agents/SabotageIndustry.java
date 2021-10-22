package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.BaseDisruptIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Pair;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import java.awt.Color;
import java.util.ArrayList;
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
		float mult = getEffectMultForLevel();
		float effectMin = getDef().effect.one * mult;
		float effectMax = getDef().effect.two * mult;
		float effect = MathUtils.getRandomNumberInRange(effectMin, effectMax);
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;

		disruptTime = Math.max(industry.getDisruptedDays(), effect);
		industry.setDisrupted(disruptTime);

		adjustRepIfDetected(RepLevel.HOSTILE, null);
		
		/*
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(BaseDisruptIndustry.class)) {
			BaseDisruptIndustry dis = (BaseDisruptIndustry)intel;
			List<Token> params = new ArrayList<>();
		}
		*/
		
		reportEvent();
	}

	@Override
	public void onFailure() {
		adjustRepIfDetected(RepLevel.INHOSPITABLE, RepLevel.HOSTILE);
		reportEvent();
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		super.addBulletPoints(info, mode, isUpdate, tc, initPad);
		if (result != null && result.isSuccessful())
		{
			String industryName = industry.getCurrentName();
			info.addPara(exerelin.utilities.StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"industryDisruptEffect", "$industry", industryName), 0, tc, Misc.getHighlightColor(), (int)disruptTime + "");
		}
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSuccessful())
		{
			String industryName = industry.getCurrentName();
			info.addPara(exerelin.utilities.StringHelper.getStringAndSubstituteToken("nex_agentActions", 
					"industryDisruptEffect", "$industry", industryName) + ".", pad, 
					Misc.getHighlightColor(), (int)disruptTime + "");
		}
		super.addResultPara(info, pad);
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_sabotageIndustry");
		info.addPara(action, pad, Misc.getHighlightColor(), industry.getCurrentName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_sabotageIndustry", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), 
				industry.getCurrentName(), Math.round(daysRemaining) + "");
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
