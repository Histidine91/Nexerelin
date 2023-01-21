package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;


public class Nex_NGCSetFactionFromJoinOption extends BaseCommandPlugin {
	static final int length = Nex_NGCListFactions.JOIN_FACTION_OPTION_PREFIX.length();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
		//if (option == null) throw new IllegalStateException("No $option set");
		String factionId = option.substring(length);
		//dialog.getTextPanel().addParagraph(factionId);
		
		new NGCSetPlayerFaction().setFaction(factionId, dialog, memoryMap);
			
		return true;
	}
}