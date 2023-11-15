package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.IndustryPickerListener;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.utilities.StringHelper;
import lombok.NoArgsConstructor;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;

@NoArgsConstructor
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
	protected String getSubbedName() {
		return String.format(getDef().nameForSub, industry.getCurrentName());
	}

	@Override
	public List<Object> dialogGetTargets(AgentOrdersDialog dialog) {
		List<Object> targets = new ArrayList<>();
		for (Industry ind : market.getIndustries()) {
			if (!ind.canBeDisrupted()) continue;
			if (ind.getSpec().hasTag(Industries.TAG_STATION))
				continue;
			targets.add(ind);
		}
		return targets;
	}

	@Override
	public void dialogSetTarget(AgentOrdersDialog dialog, Object target) {
		this.industry = (Industry)target;
	}

	// no reason to assume the first industry we pick is the one we actually want
	/*
	@Override
	public void dialogAutopickTarget(AgentOrdersDialog dialog, List<Object> targets) {
		if (targets == null) {
			dialogSetTarget(dialog, null);
			return;
		}
		dialogSetTarget(dialog, targets.get(0));
	}
	 */

	@Override
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		String industryName = industry.getCurrentName();
		Color hl = Misc.getHighlightColor();
		String mktName = market.getName();

		dialog.getText().addPara(getString("dialogInfoHeaderSabotageIndustry"), hl, industryName, mktName);
		dialog.setHighlights(industryName, mktName, hl, targetFaction.getColor());
		dialog.addEffectPara(0, 1);

		super.dialogPrintActionInfo(dialog);
	}

	@Override
	protected void dialogPopulateMainMenuOptions(AgentOrdersDialog dialog) {
		String str = getString("dialogOption_target");
		String target = industry != null? industry.getCurrentName() : StringHelper.getString("none");
		str = StringHelper.substituteToken(str, "$target", target);
		dialog.getOptions().addOption(str, AgentOrdersDialog.Menu.TARGET);
	}

	@Override
	protected void dialogPopulateTargetOptions(final AgentOrdersDialog dialog) {
		List<Industry> industries = new ArrayList<>();
		for (Object obj : dialog.getCachedTargets())
			industries.add((Industry)obj);

		dialog.getDialog().showIndustryPicker(getString("dialogIndustryPickerHeader"),
				StringHelper.getString("select", true), market,
				industries, new IndustryPickerListener() {
					public void pickedIndustry(Industry industry) {
						dialogSetTarget(dialog, industry);
						dialog.printActionInfo();
						dialog.optionSelected(null, AgentOrdersDialog.Menu.MAIN_MENU);
					}
					public void cancelledIndustryPicking() {
						dialog.optionSelected(null, AgentOrdersDialog.Menu.MAIN_MENU);
					}
				});
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		dialog.getTargets();

		dialog.getText().setFontSmallInsignia();
		dialog.getText().addPara(getString("dialogInfoSabotageIndustryMission"));
		dialog.getText().setFontInsignia();
	}

	@Override
	public boolean dialogCanActionProceed(AgentOrdersDialog dialog) {
		return industry != null;
	}

	@Override
	public String getDefId() {
		return "sabotageIndustry";
	}
}
