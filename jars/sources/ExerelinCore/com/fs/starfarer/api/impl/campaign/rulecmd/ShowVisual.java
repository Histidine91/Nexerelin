package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

public class ShowVisual extends BaseCommandPlugin {

	public static final float DEFAULT_WIDTH = 640;
	public static final float DEFAULT_HEIGHT = 400;

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		
		String image = params.get(0).getString(memoryMap);
		float width = DEFAULT_WIDTH;
		if (params.size() > 1) {
			width = params.get(1).getFloat(memoryMap);
		}
		float height = DEFAULT_HEIGHT * width/DEFAULT_WIDTH;
		if (params.size() > 2) {
			height = params.get(2).getFloat(memoryMap);
		}

		dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(image, width, height));
		
		return true;
	}
}
