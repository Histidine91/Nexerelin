package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.NexUtils;

import java.util.*;

public class ExerelinShowDefaultVisual extends ShowDefaultVisual {
	
	public static final Map<String, String> CUSTOM_MARKET_IMAGES = new HashMap<>();
	public static final Map<String, String> CUSTOM_MARKET_TAG_IMAGES = new HashMap<>();
	public static final Set<String> CUSTOM_MARKET_TAGS = new HashSet<>();
	
	static {
		//CUSTOM_MARKET_IMAGES.put("tiandong_shanghai", "vacuum_colony");
		//CUSTOM_MARKET_IMAGES.put("tiandong_macau", "orbital");
		//CUSTOM_MARKET_IMAGES.put("tiandong_fuxin", "orbital");
		//CUSTOM_MARKET_IMAGES.put("tiandong_chaozhou", "orbital");
		//CUSTOM_MARKET_IMAGES.put("tiandong_mogui", "orbital");
		
		//CUSTOM_MARKET_TAGS.add("shanghai");
		
		//CUSTOM_MARKET_TAG_IMAGES.put("shanghai", "vacuum_colony");
	}

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {

		// Illustrated Entities compatibility; delegate to that mod's ShowDefaultVisual method
		if (Global.getSettings().getModManager().isModEnabled("illustrated_entities")) {
			BaseCommandPlugin ieVisual = (BaseCommandPlugin)NexUtils.instantiateClassByName("illustratedEntities.dialogue.rules.ShowDefaultVisualWithSelector");
			ieVisual.execute(ruleId, dialog, params, memoryMap);
			return true;
		}
		
		SectorEntityToken target = dialog.getInteractionTarget();
		String entityId = target.getId();
		if (CUSTOM_MARKET_IMAGES.containsKey(entityId)) {
			dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("illustrations", CUSTOM_MARKET_IMAGES.get(entityId), 640, 400));
		}
		else {
			super.execute(ruleId, dialog, params, memoryMap);
		}
	
		return true;
	}
}