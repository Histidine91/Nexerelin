package exerelin.campaign.ai;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.concern.StrategicConcern;

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
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder) {
        float pad = 3;
        float opad = 10;
        //tooltip.addPara("TBD", opad);
        for (StrategicConcern concern : currentConcerns) {
            concern.createTooltip(tooltip, holder, pad);
        }
    }
}
