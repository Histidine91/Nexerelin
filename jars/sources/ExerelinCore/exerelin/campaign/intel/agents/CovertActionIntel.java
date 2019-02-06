package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.covertops.CovertOpsAction;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;

public class CovertActionIntel extends BaseIntelPlugin {
	
	public static final String[] EVENT_ICONS = new String[]{
		"graphics/exerelin/icons/intel/spy4.png",
		"graphics/exerelin/icons/intel/spy4_amber.png",
		"graphics/exerelin/icons/intel/spy4_red.png"
	};	
	
	protected CovertOpsAction action;
	protected AgentIntel agent;
	protected String actionDefId;
	
	public CovertActionIntel(CovertOpsAction action, AgentIntel agent) {
		this.action = action;
		this.agent = agent;
		actionDefId = action.getActionDefId();
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);

		info.addPara(getName(), c, 0);

		Color tc = getBulletColorForMode(mode);
		Color hl = Misc.getHighlightColor();
		float initPad = 0;
		float pad = 3;
		
		bullet(info);
		
		// TODO: add information
		ExerelinUtilsFaction.addFactionNamePara(info, initPad, tc, action.getAgentFaction());
		ExerelinUtilsFaction.addFactionNamePara(info, pad, tc, action.getTargetFaction());
	}
	
	protected String getName() {
		String str = action.getDef().name;
		if (action.getResult() != null) { 
			if (action.getResult().isSucessful())
				str += " - " + StringHelper.getString("nex_agents", "verbSuccess");
			else
				str += " - " + StringHelper.getString("nex_agents", "verbFailed");
		}
		
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		
		addMainDescPara(info);
	}
	
	// subclasses should override this
	public void addMainDescPara(TooltipMakerAPI info) {
		
	}
	
	public void addPara(TooltipMakerAPI info, String stringId, Map<String, String> sub, 
			String[] highlights, Color[] highlightColors) {
		
	}
	
	@Override
	public String getIcon() {
		int significance = 0;
		CovertActionResult result = action.getResult();
		ExerelinReputationAdjustmentResult repResult = action.getReputationResult();
		if (result != null) {
			if (!result.isSucessful() || result.isDetected()) significance = 1;
			if (repResult.wasHostile && !repResult.isHostile) significance = 1;
			if (repResult.isHostile && !repResult.wasHostile) significance = 2;
		}		
		return EVENT_ICONS[significance];
	}
}
