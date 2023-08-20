package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import lombok.NoArgsConstructor;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.Map;

@NoArgsConstructor
public class Injury extends CovertActionIntel {
		
	public Injury(AgentIntel agent, FactionAPI agentFaction, 
			FactionAPI targetFaction, boolean playerInvolved, Map<String, Object> params) {
		super(agent, agent.getMarket(), agentFaction, targetFaction, playerInvolved, params);
	}
	
	@Override
	public float getTimeNeeded() {
		float base = super.getTimeNeeded();
		return base * MathUtils.getRandomNumberInRange(0.8f, 1.1f);
	}
	
	@Override
	public boolean canAbort() {
		return false;
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
		String action = getString("intelStatus_injury");
		info.addPara(action, pad);
	}
	
	@Override
	public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
		String action = getActionString("intelStatus_injury", true);
		info.addPara(action, pad, color, Misc.getHighlightColor(), Math.round(daysRemaining) + "");
	}
	
	@Override
	public boolean showSuccessChance() {
		return false;
	}
	
	@Override
	public String getDefId() {
		return "injury";
	}

	@Override
	protected void onSuccess() {
		agent.sendUpdateIfPlayerHasIntel(AgentIntel.UPDATE_INJURY_RECOVERED, false);
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
	public boolean dialogCanShowAction(AgentOrdersDialog dialog) {
		return false;
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/intel/comms.png";
	}
}
