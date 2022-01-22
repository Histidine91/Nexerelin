package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.eventide.DuelPanel;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.duel.Nex_DuelDialogDelegate;
import java.util.List;
import java.util.Map;

public class Nex_FencingDuel extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		
		switch(arg)
		{
			case "start":
			{
				boolean playerSkilled = false;
				boolean enemySkilled = false;
				if (params.size() >= 2) {
					playerSkilled = params.get(1).getBoolean(memoryMap);
				}
				if (params.size() >= 3) {
					enemySkilled = params.get(2).getBoolean(memoryMap);
				}
				final DuelPanel duelPanel = DuelPanel.createDefault(playerSkilled, enemySkilled, "soe_ambience");
				dialog.showCustomVisualDialog(1024, 700, new Nex_DuelDialogDelegate(null, duelPanel, dialog, memoryMap, false));
				return true;
			}
			case "tutorial":
			{
				boolean playerSkilled = false;
				if (params.size() >= 2) {
					playerSkilled = params.get(1).getBoolean(memoryMap);
				}
				final DuelPanel duelPanel = DuelPanel.createTutorial(playerSkilled, "soe_ambience");
				dialog.showCustomVisualDialog(1024, 700, new Nex_DuelDialogDelegate(null, duelPanel, dialog, memoryMap, true));
				return true;
			}
		}
		
		return false;
	}
}
