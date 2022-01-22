package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AgentActionBase extends BaseCommandPlugin {
	
	protected static final String STRING_CATEGORY = "exerelin_agents";
	protected static final Map<CovertActionResult, String> RESULT_STRINGS = new HashMap<>(); 
	protected String agentType = "agent";
	protected CovertActionResult result = null;
	
	static {
		RESULT_STRINGS.put(CovertActionResult.SUCCESS, "resultSuccess");
		RESULT_STRINGS.put(CovertActionResult.SUCCESS_DETECTED, "resultSuccessDetected");
		RESULT_STRINGS.put(CovertActionResult.FAILURE, "resultFailure");
		RESULT_STRINGS.put(CovertActionResult.FAILURE_DETECTED, "resultFailureDetected");
	}
	
	protected boolean useSpecialPerson(String typeId, int count) {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		if (cargo.getCommodityQuantity(typeId) < count) return false;
		cargo.removeCommodity(typeId, count);
		agentType = typeId;
		return true;
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		MemoryAPI memory = memoryMap.get(MemKeys.MARKET);
		memory.set("$alertLevel", CovertOpsManager.getAlertLevel(dialog.getInteractionTarget().getMarket()), 0);
		
		if (result != null)
		{
			String str = StringHelper.getString(STRING_CATEGORY, RESULT_STRINGS.get(result));
			str = StringHelper.substituteToken(str, "$agentType", StringHelper.getString(STRING_CATEGORY, agentType));
			String verb = StringHelper.getString(STRING_CATEGORY, "verbSuccess");
			Color color = Misc.getHighlightColor();
			if (!result.isSuccessful())
			{
				verb = StringHelper.getString(STRING_CATEGORY, "verbFailed");
				color = Misc.getNegativeHighlightColor();
			}
			
			str = StringHelper.substituteToken(str, "$resultVerb", verb);
			
			TextPanelAPI text = dialog.getTextPanel();
			text.addParagraph(str);
			text.highlightInLastPara(color, verb);
		}
		
		return true;
	}
}
