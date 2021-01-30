package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import static exerelin.campaign.CovertOpsManager.NPC_EFFECT_MULT;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class DestroyCommodityStocks extends CovertActionIntel {
	
	protected String commodityId;
	protected String commodityName;
	protected float duration;
	protected int effect;

	public DestroyCommodityStocks(AgentIntel agentIntel, MarketAPI market, String commodityId, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agentIntel, market, agentFaction, targetFaction, playerInvolved, params);
		this.commodityId = commodityId;
		commodityName = getCommodityName();
	}
	
	public void setCommodity(String commodityId) {
		this.commodityId = commodityId;
		commodityName = getCommodityName();
	}
	
	public String getCommodityName() {
		if (commodityId == null) return null;
		return market.getCommodityData(commodityId).getCommodity().getName().toLowerCase();
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
		commodity.getAvailableStat().addTemporaryModFlat(duration, commodityId, desc, -effect);
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
		return agent.getMarket().getCommodityData(this.commodityId) != null;
	}
	
	@Override
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		super.addBulletPoints(info, color, initPad, pad);
		if (result != null && result.isSuccessful())
		{
			info.addPara(getString("commodityLossEffectShort"), pad, color, 
					Misc.getHighlightColor(), effect + "");
		}
			
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSuccessful())
		{
			TooltipMakerAPI item = info.beginImageWithText(getIcon(), 40);
			item.addPara(getString("commodityLossEffect"), 0, Misc.getHighlightColor(), 
					commodityName, effect + "");
			info.addImageWithText(pad);
			
		}
		super.addResultPara(info, pad);
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_destroyCommodities", false);
		info.addPara(action, pad, Misc.getHighlightColor(), commodityName);
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_destroyCommodities", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), 
				commodityName, Math.round(daysRemaining) + "");
	}
	
	
	@Override
	protected List<Pair<String, String>> getStandardReplacements() {
		List<Pair<String, String>> sub = super.getStandardReplacements();
		sub.add(new Pair<>("$commodity", commodityName));
		
		return sub;
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
