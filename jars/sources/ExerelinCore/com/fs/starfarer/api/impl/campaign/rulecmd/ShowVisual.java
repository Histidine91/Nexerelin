package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

public class ShowVisual extends BaseCommandPlugin {

        @Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		
		SectorEntityToken target = dialog.getInteractionTarget();
		String image = params.get(0).getString(memoryMap);
                // FIXME
		dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(image, 640, 400));
		
		return true;
	}

}
