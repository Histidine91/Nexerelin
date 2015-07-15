package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.AllianceManager.Alliance;
import exerelin.campaign.AllianceManager.AllianceComparator;
import java.util.Collections;

public class PrintAlliances extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		List<Alliance> alliances = AllianceManager.getAllianceList();		
		Collections.sort(alliances, new AllianceComparator());
		TextPanelAPI text = dialog.getTextPanel();
		
		AllianceManager.printAllianceList(text);

		return true;
	}
}