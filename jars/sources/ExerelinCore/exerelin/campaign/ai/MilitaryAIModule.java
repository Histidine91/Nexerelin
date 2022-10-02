package exerelin.campaign.ai;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.concern.StrategicConcern;

public class MilitaryAIModule extends StrategicAIModule {
    public MilitaryAIModule(StrategicAI ai, StrategicDefManager.ModuleType module) {
        super(ai, module);
    }
	
	/*
        Things that should be in the report:
            - Our fleet pool
				- Fleet pool generation compared to potential adversaries
			- Threatening presences in our systems
			- Markets with poor defense/value ratio
			- Recent military actions by/against us (add a listener for this?)
			- Priority targets
				- Revanchist claims
				- Strategic targets
				- Retaliation
     */

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder) {
        float pad = 3;
        float opad = 10;
        //tooltip.addPara("TBD", opad);
        for (StrategicConcern concern : currentConcerns) {
            concern.createTooltip(tooltip, holder, pad);
        }
    }
}
