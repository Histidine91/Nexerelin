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
import exerelin.utilities.StringHelper;
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
		commodityName = market.getCommodityData(commodityId).getCommodity().getName().toLowerCase();
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
		String desc = StringHelper.getString("nex_agentActions", "commodityDestroyModDesc");
		commodity.getAvailableStat().addTemporaryModFlat(duration, commodityId, desc, -effect);
		
		adjustRepIfDetected(RepLevel.INHOSPITABLE, null);
		
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
			info.addPara(exerelin.utilities.StringHelper.getString("nex_agentActions", "commodityLossEffectShort"), 
					pad, color, Misc.getHighlightColor(), effect + "");
		}
			
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSucessful())
		{
			info.addPara(exerelin.utilities.StringHelper.getString("nex_agentActions", "commodityLossEffect"), pad, 
					Misc.getHighlightColor(), commodityName, effect + "");
		}
		super.addResultPara(info, pad);
	}
	
	@Override
	protected List<Pair<String, String>> getStandardReplacements() {
		List<Pair<String, String>> sub = super.getStandardReplacements();
		sub.add(new Pair<>("$commodity", commodityName));
		
		return sub;
	}

	@Override
	public String getActionDefId() {
		return "destroyCommodities";
	}
	
	@Override
	public String getIcon() {
		return Global.getSettings().getCommoditySpec(commodityId).getIconName();
	}
}
