package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.utilities.StringHelper;
import lombok.NoArgsConstructor;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class DestabilizeMarket extends CovertActionIntel {
	
	public static final int NUM_LINES = 8;	// must not exceed what is inside strings.json
	protected int stabilityLoss;
	protected int actionTextNum;

	public DestabilizeMarket(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		
		actionTextNum = MathUtils.getRandomNumberInRange(1, NUM_LINES);
	}

	@Override
	public void onSuccess() {
		float mult = getEffectMultForLevel();
		float effectMin = getDef().effect.one * mult;
		float effectMax = getDef().effect.two * mult;
		stabilityLoss = Math.round(MathUtils.getRandomNumberInRange(effectMin, effectMax));
		
		String reason = StringHelper.getString("exerelin_marketConditions", "agentDestabilization");
		if (isAgentFactionKnown())
			reason = agentFaction.getDisplayName() + " " + Misc.lcFirst(reason);
		
		RecentUnrest.get(market).add(stabilityLoss, reason);
		
		adjustRepIfDetected(RepLevel.INHOSPITABLE, null);
		reportEvent();
	}

	@Override
	public void onFailure() {
		adjustRepIfDetected(RepLevel.INHOSPITABLE, RepLevel.HOSTILE);
		reportEvent();
	}
	
	@Override
	public boolean canRepeat() {
		if (sp != StoryPointUse.NONE) return false;
		return true;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		super.addBulletPoints(info, mode, isUpdate, tc, initPad);
		if (result != null && result.isSuccessful())
			info.addPara(getString("destabilizeEffect"), 0, tc, 
					Misc.getHighlightColor(), stabilityLoss + "");
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSuccessful())
			info.addPara(getString("destabilizeEffect") + ".",
					pad, Misc.getHighlightColor(), stabilityLoss + "");
		
		super.addResultPara(info, pad);
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_destabilizeMarket");
		info.addPara(action, pad);
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_destabilizeMarket", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}

	protected String getSubbedName() {
		return String.format(getDef().nameForSub, market.getName());
	}
	
	@Override
	protected List<Pair<String, String>> getStandardReplacements() {
		List<Pair<String, String>> sub = super.getStandardReplacements();
		String actionName = getString("destabilizeText" + NUM_LINES);
		sub.add(new Pair<>("$actionLine", actionName));
		
		return sub;
	}

	@Override
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		dialog.getText().addPara(getString("dialogInfoHeaderDestabilizeMarket"), targetFaction.getColor(), market.getName());
		dialog.addEffectPara(0, 1);
		super.dialogPrintActionInfo(dialog);
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		actionTextNum = MathUtils.getRandomNumberInRange(1, NUM_LINES);
		dialog.printActionInfo();
	}

	@Override
	public boolean dialogCanActionProceed(AgentOrdersDialog dialog) {
		return true;
	}

	@Override
	public String getDefId() {
		return "destabilizeMarket";
	}
}
