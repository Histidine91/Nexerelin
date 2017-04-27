package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.StringHelper;


public class NGCSetNumStartingOfficers extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		int num = (int)params.get(0).getFloat(memoryMap);
		setupData.numStartingOfficers = num;
		addOfficersGainText(num, dialog.getTextPanel());
		return true;
	}
	
	public static void addOfficersGainText(int num, TextPanelAPI text) {
		if (num <= 0) return;
		text.setFontSmallInsignia();
		String str = StringHelper.getStringAndSubstituteToken("exerelin_ngc", num == 1 ? "recruitedOfficer" : "recruitedOfficers", 
				"$numOfficers", num + "");
		str = Misc.ucFirst(str);
		text.addParagraph(str, Misc.getPositiveHighlightColor());
		text.highlightInLastPara(Misc.getHighlightColor(), num + "");
		text.setFontInsignia();
	}
}