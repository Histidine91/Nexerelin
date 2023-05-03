package exerelin.campaign.ai;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;

import java.awt.*;

public class DiplomaticAIModule extends StrategicAIModule {
    public DiplomaticAIModule(StrategicAI ai, StrategicDefManager.ModuleType module) {
        super(ai, module);
    }
	
	/*
        Things that should be in the report:
            - Strong factions/alliances in general
            - Strong factions/alliances that aren't friendly to us
			- Potential allies
			- Are we in trouble in terms of wars?
				- Enemies that we should make peace with
	
		Consult the existing diplomacy brain for most of this
     */

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder, float width) {
        float opad = 10;
        float weariness = DiplomacyManager.getWarWeariness(ai.getFactionId(), true);
        String wearinessStr = String.format("%.0f", weariness);

        String str = DiplomacyProfileIntel.getString("warWeariness", true) + ": " + wearinessStr;
        float colorProgress = Math.min(weariness, DiplomacyProfileIntel.WEARINESS_MAX_FOR_COLOR)/DiplomacyProfileIntel.WEARINESS_MAX_FOR_COLOR;
        if (colorProgress > 1) colorProgress = 1;
        if (colorProgress < 0) colorProgress = 0;

        Color wearinessColor = Misc.interpolateColor(Color.WHITE, Misc.getNegativeHighlightColor(), colorProgress);
        tooltip.addPara(str, opad, wearinessColor, wearinessStr);

        super.generateReport(tooltip, holder, width);
    }
}
