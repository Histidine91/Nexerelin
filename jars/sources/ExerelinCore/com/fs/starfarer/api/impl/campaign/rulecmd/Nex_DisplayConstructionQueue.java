package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ColonyManager;
import exerelin.utilities.StringHelper;
import java.util.LinkedList;


public class Nex_DisplayConstructionQueue extends BaseCommandPlugin {
	public static float COST_HEIGHT = 67;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		SectorEntityToken target = dialog.getInteractionTarget();
		MarketAPI market = target.getMarket();
		printQueue(dialog.getTextPanel(), market);
		return true;
	}
		
	public void printQueue(TextPanelAPI text, MarketAPI market)
	{
		Color hl = Misc.getHighlightColor();
		
		LinkedList<ColonyManager.QueuedIndustry> queue = ColonyManager.getManager().getConstructionQueue(market);
		
		text.setFontSmallInsignia();

		text.addParagraph(StringHelper.HR);
		
		for (Industry ind : market.getIndustries()) {
			if (ind.isBuilding() && !ind.isUpgrading()) {
				String name = ind.getCurrentName();
				int days = (int)((1 - ind.getBuildOrUpgradeProgress()) * ind.getBuildTime());
				String daysStr = "" + days;
				text.addPara("Now building: " + name + " (" + daysStr + " days remaining)", hl, name);
			}
		}
		
		if (queue == null || queue.isEmpty()) {
			text.addPara("No items in queue");
		}
		else {
			text.addPara("Queued IDs:");
			for (ColonyManager.QueuedIndustry ind : queue)
			{
				String name = ind.industry;
				text.addPara("  " + name + " (type: " + ind.type.toString() + ")");
			}
		}
 
		text.addParagraph(StringHelper.HR);
		text.setFontInsignia();
	}
}