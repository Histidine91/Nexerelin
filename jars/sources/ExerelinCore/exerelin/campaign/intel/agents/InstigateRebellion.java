package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import lombok.NoArgsConstructor;

import java.awt.*;
import java.util.Map;

@NoArgsConstructor
public class InstigateRebellion extends CovertActionIntel {
	
	public static final int MAX_STABILITY = 6;
	public static final float LIBERATION_MULT_STRONG = 1.4f;
	public static final float LIBERATION_MULT = 1.2f;

	public InstigateRebellion(AgentIntel agentIntel, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
	}
	
	@Override
	protected MutableStat getSuccessChance(boolean checkSP) {
		MutableStat stat = super.getSuccessChance(checkSP);
		if (checkSP && sp.preventFailure()) {
			return stat;
		}
		
		float stabilityModifier = 1.2f - (market.getStabilityValue() - 2) * 0.1f;
		
		stat.modifyMult("stability", stabilityModifier, StringHelper.getString("stability", true));
		
		// liberation bonus
		//float nonOwnerMult = 1;
		
		if (RebellionIntel.isNotUnderOriginalOwner(market)) 
		{
			String origOwnerId = NexUtilsMarket.getOriginalOwner(market);
			FactionAPI origOwner = Global.getSector().getFaction(origOwnerId);
			if (RebellionIntel.isRebelOriginalFaction(origOwner, agentFaction)) {
				stat.modifyMult("liberation", LIBERATION_MULT_STRONG, getString("dialogInfoRebellionLiberationBonusStrong"));
			}
			else {
				stat.modifyMult("liberation", LIBERATION_MULT, getString("dialogInfoRebellionLiberationBonus"));
			}
		}
		
		return stat;
	}
	
	
	@Override
	public float getEffectMultForLevel() {
		int level = getLevel();
		float mult = 0.7f + 0.15f * (level - 1);
		return mult;
	}
	
	@Override
	public void onSuccess() {
		RebellionIntel event = RebellionCreator.getInstance().createRebellion(market, agentFaction.getId(), true);
		if (event == null) {
			endAfterDelay();
			return;
		}
		if (playerInvolved) {
			event.setPlayerInitiated(true);
		}
		
		float mult = getEffectMultForLevel();
		event.setRebelStrength(event.getRebelStrength() * mult);
		
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
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		TextPanelAPI text = dialog.getText();
		Color hl = Misc.getHighlightColor(), neg = Misc.getNegativeHighlightColor();

		text.addPara(getString("dialogInfoHeaderInstigateRebellion"), targetFaction.getColor(), market.getName());

		int stability = (int)market.getStabilityValue(), required = InstigateRebellion.MAX_STABILITY;

		String stabilityStr = getString("dialogInfoRebellionStability");
		LabelAPI label = text.addPara(stabilityStr, hl, stability + "", required + "");
		label.setHighlight(stability + "", required + "");
		label.setHighlightColors(stability > required ? neg : hl, hl);

		String effectStr = getString("dialogInfoEffectRebellion");
		float strMult = getEffectMultForLevel();
		text.addPara(effectStr, strMult < 1 ? neg : hl, String.format("%.2f", strMult));

		super.dialogPrintActionInfo(dialog);
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		dialog.printActionInfo();
	}

	@Override
	public boolean dialogCanShowAction(AgentOrdersDialog dialog) {
		return dialog.canConductLocalActions() && RebellionCreator.ENABLE_REBELLIONS && NexUtilsMarket.canBeInvaded(dialog.getAgentMarket(), true);
	}

	// TODO: tooltip?
	@Override
	public boolean dialogCanActionProceed(AgentOrdersDialog dialog) {
		return market.getStabilityValue() <= InstigateRebellion.MAX_STABILITY;
	}

	@Override
	public String getDefId() {
		return "instigateRebellion";
	}
}
