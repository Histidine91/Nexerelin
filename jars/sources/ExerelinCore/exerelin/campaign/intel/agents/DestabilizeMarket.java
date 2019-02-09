package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

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
			reason = agentFaction.getDisplayName() + " " + reason;
		else
			reason = Misc.lcFirst(reason);
		
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
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {
		super.addBulletPoints(info, color, initPad, pad);
		if (result != null && result.isSucessful())
			info.addPara(StringHelper.getString("nex_agentActions", "destabilizeEffect"), pad, 
					color, Misc.getHighlightColor(), stabilityLoss + "");
	}
	
	@Override
	public void addResultPara(TooltipMakerAPI info, float pad) {
		if (result != null && result.isSucessful())
			info.addPara(StringHelper.getString("nex_agentActions", "destabilizeEffect") + ".",
					pad, Misc.getHighlightColor(), stabilityLoss + "");
		
		super.addResultPara(info, pad);
	}
	
	@Override
	protected List<Pair<String, String>> getStandardReplacements() {
		List<Pair<String, String>> sub = super.getStandardReplacements();
		String actionName = StringHelper.getString("nex_agentActions", "destabilizeText" + NUM_LINES);
		sub.add(new Pair<>("$actionLine", actionName));
		
		return sub;
	}

	@Override
	public String getActionDefId() {
		return "destabilizeMarket";
	}
}
