package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;

public class Travel extends CovertActionIntel {
	
	public static final float DAYS_PER_LY = 0.5f;
	
	protected float timeElapsed;
	protected MutableStat departTime;
	protected MutableStat travelTime;
	protected MutableStat arriveTime;
	// 0 = at starting market, preparing to leave
	// 1 = travelling between markets
	// 2 = at destination market, preparing to insert
	// 3 = embedded at destination market
	protected int status = 0;
	protected MarketAPI from;
	
	public Travel(AgentIntel agent, MarketAPI market, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agent, market, agentFaction, targetFaction, playerInvolved, params);
		from = agent.getMarket();
	}
	
	@Override
	public void init() {
		getTimeNeeded(true);
	}
	
	public float getTimeNeeded(boolean resetVars) {
		if (market == null) return 0;
		
		MutableStat departTime = getDepartOrArriveTime(agent.getMarket(), true);
		MutableStat travelTime = getTravelTime(agent.getMarket(), market);
		MutableStat arriveTime = getDepartOrArriveTime(market, false);
		float days = departTime.getModifiedValue() + travelTime.getModifiedValue() 
				+ arriveTime.getModifiedValue();
		
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
	
	public MutableStat getDepartTime() {
		return departTime;
	}
	
	public MutableStat getTravelTime() {
		return travelTime;
	}
	
	public MutableStat getArriveTime() {
		return arriveTime;
	}
	
	protected MutableStat getDepartOrArriveTime(MarketAPI market, boolean departing) {
		MutableStat time = new MutableStat(0);
		if (market == null) return time;
		
		String str = getString("travelTimeStatBase");
		String departureOrArrival = StringHelper.getString(departing ? "departure" : "arrival");
		str = StringHelper.substituteToken(str, "$departureOrArrival", departureOrArrival);
		
		float baseTime = 2;
		float penalty = 0;
		boolean deciv = false;
		boolean unfriendly = false;
		
		// market decivilized
		if (!market.isInEconomy()) {
			deciv = true;
			if (!departing) {
				baseTime = 1;
			} else {
				baseTime = 0;
				penalty = 12;
			}
		}
		// unfriendly market
		else {
			switch (market.getFaction().getRelationshipLevel(Factions.PLAYER)) {
				case SUSPICIOUS:
					penalty = 2;
					unfriendly = true;
					break;
				case INHOSPITABLE:
					penalty = 4;
					unfriendly = true;
					break;
				case HOSTILE:
				case VENGEFUL:
					penalty = 7;
					unfriendly = true;
					break;
			}
			if (market.isFreePort()) penalty = 0;
		}
		
		time.modifyFlat("base", baseTime, str);
		
		// reduce penalty based on agent level
		if (penalty > 0) {
			int level = getLevel();
			penalty *= 1 - 0.15f * (level - 1);
		}
		
		if (deciv && departing) {
			str = getString("travelTimeStatDeciv");
			time.modifyFlat("departDeciv", penalty, str);
		}
		if (unfriendly) {
			str = getString("travelTimeStatRepLevel");
			String sub = market.getFaction().getRelationshipLevel(Factions.PLAYER).getDisplayName().toLowerCase();
			//if (market.isFreePort()) sub = StringHelper.getString("freePort");	// doesn't display anyway in this case
			str = StringHelper.substituteToken(str, "$repLevel", sub);
			time.modifyFlat("security", penalty, str);
		}
		
		return time;
	}
	
	// can't abort once we've departed the planet
	@Override
	public boolean canAbort() {
		return status == 0;
	}
	
	// clear queued action (if any)
	// since the queued action assumes we completed our travel, which we won't now
	@Override
	public void abort() {
		super.abort();
		CovertActionIntel next = agent.getNextAction();
		if (this == agent.getCurrentAction() && next != null) {
			next.abort();
			agent.removeActionFromQueue(next);
		}
	}
	
	protected MutableStat getTravelTime(MarketAPI one, MarketAPI two) {
		MutableStat stat = new MutableStat(0);
		float time = 0;
		String distStr = "??";
		if (one == null || two == null)
			time = 15;
		else {
			float dist = Misc.getDistanceLY(one.getLocationInHyperspace(), two.getLocationInHyperspace());
			time = dist * DAYS_PER_LY;
			distStr = String.format("%.1f", dist);
		}
		String desc = StringHelper.getStringAndSubstituteToken("nex_agentActions", 
				"travelTimeStatTravel", "$dist", distStr);
		stat.modifyFlat("distance", time, desc);
		
		return stat;
	}
	
	@Override
	public void setMarket(MarketAPI market) {
		super.setMarket(market);
		getTimeNeeded(true);
	}
	
	@Override
	public void advanceImpl(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		timeElapsed += days;
		if (status == 0 && timeElapsed > departTime.modified) {
			status = 1;
			agent.setMarket(null);
		}
		else if (status == 1 && timeElapsed > departTime.modified + travelTime.modified) {
			status = 2;
		}
		else if (status == 2 && timeElapsed > departTime.modified 
				+ travelTime.modified + arriveTime.modified) {
			status = 3;	// done
		}
		super.advanceImpl(amount);
	}
	
	@Override
	public CovertOpsManager.CovertActionResult execute() {
		result = CovertActionResult.SUCCESS;
		onSuccess();
		CovertOpsManager.reportAgentAction(this);
		return result;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		info.addPara(getString("intelBulletTarget"), 
				initPad, tc, market.getTextColorForFactionOrPlanet(), market.getName());
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getActionString("intelStatus_travel");
		if (started) {
			String statusStr = getString("intelStatus_travel" + status);		
			action = StringHelper.substituteToken(action, "$status", statusStr);
		} else {
			action = getActionString("intelStatus_travelShort");
		}
		action = StringHelper.substituteToken(action, "$market", market.getName());
		
		
		
		info.addPara(action, pad, market.getFaction().getBaseUIColor(), market.getName());
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_travelShort", true);
		action = StringHelper.substituteToken(action, "$market", market.getName());
		String daysLeft = Math.round(daysRemaining) + "";
		LabelAPI label = info.addPara(action, pad, color, Misc.getHighlightColor(), daysLeft);
		label.setHighlight(market.getName(), daysLeft);
		label.setHighlightColors(market.getTextColorForFactionOrPlanet(), Misc.getHighlightColor());
	}
	
	@Override
	public String getDefId() {
		return "travel";
	}
	
	@Override
	public boolean showSuccessChance() {
		return false;
	}

	@Override
	protected void onSuccess() {
		agent.setMarket(market);
		agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_ARRIVED, false);
		agent.notifyActionCompleted();
		CovertOpsManager.getRandom(market).nextFloat();	// change the next result
	}

	@Override
	protected void onFailure() {
		// do nothing
	}
	
	@Override
	public boolean allowOwnMarket() {
		return true;
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/intel/stars.png";
	}
}
