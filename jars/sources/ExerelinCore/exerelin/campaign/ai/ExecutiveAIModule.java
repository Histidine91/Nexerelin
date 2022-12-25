package exerelin.campaign.ai;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class ExecutiveAIModule extends StrategicAIModule {

    public ExecutiveAIModule(StrategicAI ai) {
        super(ai, null);
    }

	/*
        This should make the actual decisions
     */

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder, float width) {
    }
}
