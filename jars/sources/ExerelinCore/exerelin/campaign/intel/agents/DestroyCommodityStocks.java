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
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;

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
