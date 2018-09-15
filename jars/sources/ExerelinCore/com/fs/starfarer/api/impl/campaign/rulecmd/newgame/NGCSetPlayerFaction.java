package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import exerelin.campaign.PlayerFactionStore;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;


public class NGCSetPlayerFaction extends BaseCommandPlugin {
	
	protected static Random random = new Random();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		if ("random".equals(factionId))
		{
			
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
			Set<String> factions = new HashSet<>();
			if (ExerelinSetupData.getInstance().corvusMode)
			{
				factions.addAll(ExerelinConfig.getFactions(false, true));
			}
			else
			{
				// randomly pick between one of the enabled factions (except independent)
				for (Map.Entry<String, Boolean> tmp : ExerelinSetupData.getInstance().factions.entrySet())
				{
					String randFactionId = tmp.getKey();
					if (randFactionId.equals(Factions.INDEPENDENT)) continue;
					if (tmp.getValue() == false) continue;
					factions.add(randFactionId);
				}
				// followers can be picked too
				factions.add(ExerelinConstants.PLAYER_NPC_ID);
			}
			picker.addAll(factions);
			
			// pick a random faction
			factionId = picker.pick();
			FactionAPI faction = Global.getSector().getFaction(factionId);
			String name = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
			// if we picked followers, have a 50% chance for it to be free start
			if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
			{
				boolean freeStart = Math.random() < 0.5f;
				ExerelinSetupData.getInstance().freeStart = freeStart;
				if (freeStart) name += " (" + StringHelper.getString("exerelin_ngc", "freeStart") + ")";
			}
			
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