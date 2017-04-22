package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.ResponseFleetManager;
import java.awt.Color;

public class AgentGatherIntel extends AgentActionBase {

	protected Color getColorFromScale(float number, float max, boolean reverse)
	{
		float proportion = number/max;
		if (proportion > 1) proportion = 1;
		else if (proportion < 0) proportion = 0;
		
		int r = (int)(255*(1-proportion));
		int g = (int)(255*proportion);
		int b = 96;
		if (reverse)
		{
		r = 255 - r;
		g = 255 - g;
		}
		return new Color(r, g, b);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		boolean superResult = useSpecialPerson("agent", 1);
		if (superResult == false)
			return false;
		
		SectorEntityToken target = (SectorEntityToken) dialog.getInteractionTarget();
		MarketAPI market = target.getMarket();
		
		TextPanelAPI text = dialog.getTextPanel();
		text.addParagraph(market.getName() + " intel report");
		text.setFontSmallInsignia();
		text.addParagraph("-----------------------------------------------------------------------------");
		
		float stability = market.getStabilityValue();
		text.addParagraph(Misc.ucFirst(StringHelper.getString("stability")) + ": " + stability);
		text.highlightInLastPara(getColorFromScale(stability, 10, false), "" + stability);
		
		int alertLevel = Math.round(CovertOpsManager.getAlertLevel(market) * 100);
		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_agents", "alertLevel")) + ": " + alertLevel + "%");
		text.highlightInLastPara(getColorFromScale(alertLevel, 100, true), "" + alertLevel);
		
		float reserveSize = ResponseFleetManager.getReserveSize(market);
		text.addParagraph(Misc.ucFirst(StringHelper.getString("exerelin_agents", "reserveSize")) + ": " + reserveSize);
		text.highlightInLastPara(Misc.getHighlightColor(), "" + reserveSize);
				
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();
                
		StatsTracker.getStatsTracker().notifyAgentsUsed(1);
		return super.execute(ruleId, dialog, params, memoryMap);
	}
}
