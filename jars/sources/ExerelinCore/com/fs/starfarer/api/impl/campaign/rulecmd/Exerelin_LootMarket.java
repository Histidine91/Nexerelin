package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import java.util.List;
import java.util.Map;
import org.lwjgl.util.vector.Vector2f;

public class Exerelin_LootMarket extends BaseCommandPlugin {
	
	public static final Vector2f POS = new Vector2f(100, 100);
	public static final Vector2f SIZE = new Vector2f(240, 120);
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean isRaid = params.get(0).getBoolean(memoryMap);
		Map<String, Float> lootContents = (Map<String, Float>)memoryMap.get(MemKeys.MARKET).get(InvasionRound.LOOT_MEMORY_KEY);
		
		CargoAPI loot = Global.getFactory().createCargo(true);
		for (Map.Entry<String, Float> tmp : lootContents.entrySet())
		{
			loot.addCommodity(tmp.getKey(), (float)Math.floor(tmp.getValue()));
		}
		
		OptionPanelAPI options = dialog.getOptionPanel();
		
		if (!loot.isEmpty()) {
			final InteractionDialogAPI thisDialog = dialog;
			
			thisDialog.getVisualPanel().showLoot("Looted", loot, false, true, true, new CoreInteractionListener() {
				public void coreUIDismissed() {
					thisDialog.dismiss();
					thisDialog.hideTextPanel();
					thisDialog.hideVisualPanel();
					Global.getSector().setPaused(true);
				}
			});
			options.clearOptions();
			dialog.setPromptText("");
		} else {
		}
		
		return true;
	}
}
