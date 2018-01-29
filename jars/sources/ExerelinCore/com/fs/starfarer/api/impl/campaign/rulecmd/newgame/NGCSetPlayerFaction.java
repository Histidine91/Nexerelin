package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import exerelin.campaign.PlayerFactionStore;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;
import java.util.Random;


public class NGCSetPlayerFaction extends BaseCommandPlugin {
	
	protected static Random random = new Random();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		if ("random".equals(factionId))
		{
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
			List<String> factions = ExerelinConfig.getFactions(false, true);
			picker.addAll(factions);
			factionId = picker.pick();
			FactionAPI faction = Global.getSector().getFaction(factionId);
			String name = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
			dialog.getTextPanel().addParagraph(Misc.ucFirst(StringHelper.getStringAndSubstituteToken(
					"exerelin_ngc", "joinedFaction", "$faction", name)), Misc.getHighlightColor());
			dialog.getTextPanel().highlightFirstInLastPara(name, faction.getBaseUIColor());
		}
		
		PlayerFactionStore.setPlayerFactionIdNGC(factionId);
		if (!factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
			ExerelinSetupData.getInstance().freeStart = conf.freeStart;
		}
		return true;
	}
}