package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
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
import java.util.UUID;

import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;

@NoArgsConstructor
public class DestroyCommodityStocks extends CovertActionIntel {
	
	protected String commodityId;
	protected float duration;
	protected int effect;

	public DestroyCommodityStocks(AgentIntel agentIntel, MarketAPI market, String commodityId, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.commodityId = commodityId;
	}
	
	public void setCommodity(String commodityId) {
		this.commodityId = commodityId;
	}
	
	public String getCommodityName() {
		if (commodityId == null) return null;
		return StringHelper.getCommodityName(commodityId);
	}

	@Override
	public void onSuccess() {
		float mult = getEffectMultForLevel();
		float effectMin = getDef().effect.one * mult;
		float effectMax = getDef().effect.two * mult;
		effect = Math.round(MathUtils.getRandomNumberInRange(effectMin, effectMax));
		//if (!playerInvolved) effect *= NPC_EFFECT_MULT;
		
		duration = Math.round(MathUtils.getRandomNumberInRange(60, 90));
		if (!playerInvolved) effect *= NPC_EFFECT_MULT;
		
		// apply availability loss
		CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
		String desc = getString("commodityDestroyModDesc");
		String id = "agent_" + (agent == null ? UUID.randomUUID().toString() : agent.getAgent().getId());
		commodity.getAvailableStat().addTemporaryModFlat(duration, id, desc, -effect);
		market.reapplyConditions();
		market.reapplyIndustries();
		
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
		return agent.getMarket().getCommodityData(this.commodityId) != null;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		super.addBulletPoints(info, mode, isUpdate, tc, initPad);
		if (result != null && result.isSuccessful())
		{
			info.addPara(getString("commodityLossEffectShort"), 0, tc, 
					Misc.getHighlightColor(), effect + "");
		}
			
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSuccessful())
		{
			TooltipMakerAPI item = info.beginImageWithText(getIcon(), 40);
			item.addPara(getString("commodityLossEffect"), 0, Misc.getHighlightColor(), 
					getCommodityName(), effect + "");
			info.addImageWithText(pad);
			
		}
		super.addResultPara(info, pad);
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_destroyCommodities", false);
		info.addPara(action, pad, Misc.getHighlightColor(), getCommodityName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_destroyCommodities", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(),
				getCommodityName(), Math.round(daysRemaining) + "");
	}
	
	@Override
	protected List<Pair<String, String>> getStandardReplacements() {
		List<Pair<String, String>> sub = super.getStandardReplacements();
		sub.add(new Pair<>("$commodity", getCommodityName()));
		
		return sub;
	}

	@Override
	public List<Object> dialogGetTargets(AgentOrdersDialog dialog) {
		List<Object> targets = new ArrayList<>();
		for (CommodityOnMarketAPI commodity : market.getCommoditiesCopy()) {
			if (commodity.isNonEcon() || commodity.isIllegal()) continue;
			if (commodity.isPersonnel()) continue;
			if (commodity.getAvailable() < 2) continue;
			targets.add(commodity.getId());
		}
		return targets;
	}

	@Override
	public void dialogSetTarget(AgentOrdersDialog dialog, Object target) {
		this.commodityId = (String)target;
	}
	
	@Override
	public void dialogAutopickTarget(AgentOrdersDialog dialog, List<Object> targets) {
		if (targets == null) {
			dialogSetTarget(dialog, null);
			return;
		}
		dialogSetTarget(dialog, targets.get(0));
	}

	@Override
	public void dialogPrintActionInfo(AgentOrdersDialog dialog) {
		String commodity = getCommodityName();
		String mktName = market.getName();
		Color hl = Misc.getHighlightColor();

		dialog.getText().addPara(getString("dialogInfoHeaderDestroyCommodities"), hl, commodity, mktName);
		dialog.setHighlights(commodity, mktName, hl, targetFaction.getColor());
		dialog.addEffectPara(0, 1);

		super.dialogPrintActionInfo(dialog);
	}

	@Override
	protected void dialogPopulateMainMenuOptions(AgentOrdersDialog dialog) {
		String str = getString("dialogOption_target");
		String target = commodityId != null? StringHelper.getCommodityName(commodityId) : StringHelper.getString("none");
		str = StringHelper.substituteToken(str, "$target", target);
		dialog.getOptions().addOption(str, AgentOrdersDialog.Menu.TARGET);
		if (dialog.getCachedTargets().isEmpty()) {
			dialog.getOptions().setEnabled(AgentOrdersDialog.Menu.TARGET, false);
		}
	}

	@Override
	protected void dialogPopulateTargetOptions(final AgentOrdersDialog dialog) {
		for (Object commod : dialog.getCachedTargets()) {
			String commodityId = (String)commod;
			String name = StringHelper.getCommodityName(commodityId);
			dialog.optionsList.add(new Pair<String, Object>(name, commodityId));
		}
		dialog.showPaginatedMenu();
	}

	@Override
	public void dialogInitAction(AgentOrdersDialog dialog) {
		super.dialogInitAction(dialog);
		dialog.getTargets();
		dialog.printActionInfo();
	}

	@Override
	public boolean dialogCanActionProceed(AgentOrdersDialog dialog) {
		return commodityId != null;
	}

	@Override
	protected String getSubbedName() {
		return String.format(getDef().nameForSub, getCommodityName());
	}

	@Override
	public String getDefId() {
		return "destroyCommodities";
	}
	
	@Override
	public String getIcon() {
		return Global.getSettings().getCommoditySpec(commodityId).getIconName();
	}
}
