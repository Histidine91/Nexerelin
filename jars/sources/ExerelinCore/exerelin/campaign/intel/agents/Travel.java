package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;

public class Travel extends CovertActionIntel {
	
	public static final float DAYS_PER_LY = 0.5f;
	
	protected float timeElapsed;
	protected float departTime;
	protected float travelTime;
	protected float arriveTime;
	// 0 = at starting market, preparing to leave
	// 1 = travelling between markets
	// 2 = at destination market, preparing to insert
	// 3 = embedded at destination market
	protected int status = 0;
	
	public Travel(AgentIntel agent, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agent, market, agentFaction, targetFaction, playerInvolved, params);
		
	}
	
	@Override
	public void init() {
		getTimeNeeded(true);
	}
	
	public float getTimeNeeded(boolean resetVars) {
		if (market == null) return 0;
		
		float departTime = getDepartOrArriveTime(agent.getMarket(), true);
		float travelTime = getTravelTime(agent.getMarket(), market);
		float arriveTime = getDepartOrArriveTime(market, false);
		float days = departTime + travelTime + arriveTime;
		
		if (resetVars) {
			this.departTime = departTime;
			this.travelTime = travelTime;
			this.arriveTime = arriveTime;
			daysRemaining = days;
		}
		
		return days;
	}
	
	@Override
	public float getTimeNeeded() {
		return getTimeNeeded(false);
	}
	
	protected float getDepartOrArriveTime(MarketAPI market, boolean departing) {
		if (market == null) return 0;
		
		float baseTime = 2;
		float penalty = 0;
		if (!market.isInEconomy()) {	// market decivilized
			if (!departing) return 1;
			
			baseTime = 0;
			penalty = 15;
		}
		else {
			switch (market.getFaction().getRelationshipLevel(Factions.PLAYER)) {
				case SUSPICIOUS:
					penalty = 2;
					break;
				case INHOSPITABLE:
					penalty = 4;
					break;
				case VENGEFUL:
					penalty = 7;
					break;
			}
		}
		if (penalty > 0) {
			int level = agent != null ? agent.getLevel() : DEFAULT_AGENT_LEVEL;
			penalty *= 1 - 0.15f * (level - 1);
		}
		
		return baseTime + penalty;
	}
	
	// can't abort once we've departed the planet
	@Override
	public boolean canAbort() {
		return status == 0;
	}
	
	protected float getTravelTime(MarketAPI one, MarketAPI two) {
		if (one == null || two == null)
			return 15;
		float dist = Misc.getDistanceLY(one.getLocationInHyperspace(), two.getLocationInHyperspace());
		return dist * DAYS_PER_LY;
	}
	
	@Override
	public void setMarket(MarketAPI market) {
		super.setMarket(market);
		getTimeNeeded();
	}
	
	@Override
	public void advanceImpl(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		timeElapsed += days;
		if (status == 0 && timeElapsed > departTime) {
			status = 1;
			agent.setMarket(null);
		}
		else if (status == 1 && timeElapsed > departTime + travelTime) {
			status = 2;
		}
		else if (status == 2 && timeElapsed > departTime + travelTime + arriveTime) {
			status = 3;	// done
		}
		super.advanceImpl(amount);
	}
	
	@Override
	public CovertOpsManager.CovertActionResult execute() {
		result = CovertActionResult.SUCCESS;
		agent.setMarket(market);
		agent.notifyActionCompleted();
		return result;
	}
	
	@Override
	public void addBulletPoints(TooltipMakerAPI info, Color color, float initPad, float pad) {		
		info.addPara(StringHelper.getString("nex_agentActions", "intelBulletTarget"), 
				initPad, color, market.getFaction().getBaseUIColor(), market.getName());
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = StringHelper.getString("nex_agentActions", "intelStatus_travel");
		String statusStr = StringHelper.getString("nex_agentActions", "intelStatus_travel" + status);
		action = StringHelper.substituteToken(action, "$market", market.getName());
		action = StringHelper.substituteToken(action, "$status", statusStr);
		
		info.addPara(action, pad, market.getFaction().getBaseUIColor(), market.getName());
	}
	
	@Override
	public void addLastMessagePara(TooltipMakerAPI info, float pad) {
		String str = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
				"intel_lastMessage_travel", "$market", market.getName());
		info.addPara(str, pad);
	}
	
	@Override
	public String getDefId() {
		return "travel";
	}

	@Override
	protected void onSuccess() {
		agent.setMarket(market);
		agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ARRIVED, false);
	}

	@Override
	protected void onFailure() {
		// do nothing
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/intel/stars.png";
	}
}
