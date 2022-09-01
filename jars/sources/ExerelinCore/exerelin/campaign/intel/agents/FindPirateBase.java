package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivity;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.utilities.NexUtilsMarket;
import java.awt.Color;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class FindPirateBase extends CovertActionIntel {
		
	public FindPirateBase(AgentIntel agent, MarketAPI market, FactionAPI agentFaction, 
			boolean playerInvolved, Map<String, Object> params) {
		super(agent, market, agentFaction, agentFaction, playerInvolved, params);
	}
	
	@Override
	public float getTimeNeeded() {
		float base = super.getTimeNeeded();
		return base * MathUtils.getRandomNumberInRange(0.8f, 1.1f);
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
									Color tc, float initPad) {
		info.addPara(getString("intelBulletTarget"), 
				initPad, tc, market.getTextColorForFactionOrPlanet(), market.getName());
	}
	
	@Override
	public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
		String action = getString("intelStatus_findPirateBase");
		info.addPara(action, pad);
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_findPirateBase", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}
	
	@Override
	public boolean showSuccessChance() {
		return false;
	}
	
	@Override
	public String getDefId() {
		return "findPirateBase";
	}

	@Override
	protected void onSuccess() {
		try {
			PirateActivity activity = (PirateActivity)market.getCondition(Conditions.PIRATE_ACTIVITY).getPlugin();
			PirateBaseIntel intel = activity.getIntel();
			intel.makeKnown();
			intel.sendUpdateIfPlayerHasIntel(PirateBaseIntel.DISCOVERED_PARAM, false);
		} catch (Exception ex) {
			Global.getLogger(this.getClass()).error("Failed to find and make base known from pirate activity condition");
		}
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
		return Global.getSettings().getSpriteName("intel", "pirate_base");
	}
}
