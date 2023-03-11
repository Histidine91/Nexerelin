package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import exerelin.campaign.PlayerFactionStore;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import java.util.Random;

public class NGCSetPlayerFaction extends BaseCommandPlugin {
	
	protected static Random random = new Random();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String factionId = params.get(0).getString(memoryMap);
		setFaction(factionId, dialog, memoryMap);
		return true;
	}
	
	public void setFaction(String factionId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		if ("random".equals(factionId))
		{
			
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
			if (ExerelinSetupData.getInstance().corvusMode)
			{
				picker.addAll(NexConfig.getFactions(false, true));
			}
			else
			{
				// randomly pick between one of the enabled factions (except independent)
				Map<String, Boolean> enabledFactions = ExerelinSetupData.getInstance().factions;
				for (String pickable : NexConfig.getFactions(false, true))
				{
					// assume faction is enabled if the map doesn't contain it
					if (!enabledFactions.containsKey(pickable) || enabledFactions.get(pickable))
						picker.add(pickable);
				}
				// followers can be picked too
				picker.add(Factions.PLAYER);
			}			
			// pick a random faction
			factionId = picker.pick();
			FactionAPI faction = Global.getSector().getFaction(factionId);
			String name = Nex_FactionDirectoryHelper.getFactionDisplayName(faction);
			// if we picked followers, have a 50% chance for it to be free start
			if (factionId.equals(Factions.PLAYER))
			{
				name = StringHelper.getString("exerelin_ngc", "ownFaction");
				
				boolean freeStart = Math.random() < 0.5f;
				ExerelinSetupData.getInstance().freeStart = freeStart;
				if (freeStart) name += " (" + StringHelper.getString("exerelin_ngc", "freeStart") + ")";
			}
			
			dialog.getTextPanel().addParagraph(Misc.ucFirst(StringHelper.getStringAndSubstituteToken(
					"exerelin_ngc", "joinedFaction", "$faction", name)), Misc.getHighlightColor());
			dialog.getTextPanel().highlightFirstInLastPara(name, faction.getBaseUIColor());
		}
		
		memoryMap.get(MemKeys.LOCAL).set("$playerFaction", factionId, 0);
		memoryMap.get(MemKeys.LOCAL).unset(Nex_NGCCustomStartFleet.MEMORY_KEY_SHIP_MAP);
		PlayerFactionStore.setPlayerFactionIdNGC(factionId);
		if (!factionId.equals(Factions.PLAYER))
		{
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			ExerelinSetupData.getInstance().freeStart = conf.freeStart;
		}
	}
}