package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
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

public class Nex_NGCAddLevel extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		int level = (int)params.get(0).getFloat(memoryMap);
		CharacterCreationData data = (CharacterCreationData) memoryMap.get(MemKeys.LOCAL).get("$characterData");
		
		long xp = Global.getSettings().getLevelupPlugin().getXPForLevel(level);
		data.getPerson().getStats().addXP(xp);
		addXPGainText(xp, dialog.getTextPanel());
		
		int storyPoints = (level - 1) * Global.getSettings().getInt("storyPointsPerLevel");
		data.getPerson().getStats().addStoryPoints(storyPoints, dialog.getTextPanel(), false);
		
		return true;
	}
	
	public static void addXPGainText(long xp, TextPanelAPI text) {
		if (xp == 0) return;
		text.setFontSmallInsignia();
		String str = StringHelper.getStringAndSubstituteToken("exerelin_ngc", "gainedXP", "$xp", Misc.getWithDGS(xp));
		str = Misc.ucFirst(str);
		text.addParagraph(str, Misc.getPositiveHighlightColor());
		text.highlightInLastPara(Misc.getHighlightColor(), Misc.getWithDGS(xp) + "");
		text.setFontInsignia();
	}
}