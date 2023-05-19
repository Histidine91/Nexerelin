package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.hostileactivity.NexHostileActivityManager;

public class HACellInfiltratedFactor extends BaseOneTimeFactor {

    public HACellInfiltratedFactor(int points) {
        super(points);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return NexHostileActivityManager.getString("cellDisruptName");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(NexHostileActivityManager.getString("cellDisruptDesc"), 0f);
            }

        };
    }
}
