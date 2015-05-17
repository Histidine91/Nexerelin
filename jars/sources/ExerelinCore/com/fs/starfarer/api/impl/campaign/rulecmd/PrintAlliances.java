package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.AllianceManager.Alliance;
import java.util.Collections;
import java.util.Comparator;

public class PrintAlliances extends BaseCommandPlugin {
	
	public class AllianceComparator implements Comparator<Alliance>
	{
		@Override
		public int compare(Alliance alliance1, Alliance alliance2) {
		  
		int size1 = alliance1.members.size();
		int size2 = alliance2.members.size();

		if (size1 > size2) return -1;
		else if (size2 > size1) return 1;
		else return 0;
		}
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		List<Alliance> alliances = AllianceManager.getAllianceList();		
		Collections.sort(alliances, new AllianceComparator());
		TextPanelAPI text = dialog.getTextPanel();
		
		Color hl = Misc.getHighlightColor();

		text.addParagraph(Misc.ucFirst("There are " + alliances.size() + " alliances in the cluster"));
		text.highlightInLastPara(hl, "" + alliances.size());
		text.setFontSmallInsignia();
		text.addParagraph("-----------------------------------------------------------------------------");
		for (Alliance alliance : alliances)
		{
			String allianceName = alliance.name;
			String allianceString = alliance.getAllianceNameAndMembers();

			text.addParagraph(allianceString);
			text.highlightInLastPara(hl, allianceName);
		}
		text.addParagraph("-----------------------------------------------------------------------------");
		text.setFontInsignia();

		return true;
	}
}