package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;


public class NGCAddXP extends BaseCommandPlugin {
	
    @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		float xp = params.get(0).getFloat(memoryMap);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");

		data.getPerson().getStats().addXP((long)xp);
		addXPGainText((int)xp, dialog.getTextPanel());
        
		return true;
	}
	
	public static void addXPGainText(int xp, TextPanelAPI text) {
		text.setFontSmallInsignia();
		String str = StringHelper.getStringAndSubstituteToken("exerelin_ngc", "gainedXP", "$xp", Misc.getWithDGS(xp));
		str = Misc.ucFirst(str);
		text.addParagraph(str, Misc.getPositiveHighlightColor());
		text.highlightInLastPara(Misc.getHighlightColor(), Misc.getWithDGS(xp) + "");
		text.setFontInsignia();
	}
}