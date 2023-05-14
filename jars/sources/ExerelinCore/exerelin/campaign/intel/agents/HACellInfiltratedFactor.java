package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.StringHelper;

public class HACellInfiltratedFactor extends BaseOneTimeFactor {

    public HACellInfiltratedFactor(int points) {
        super(points);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return StringHelper.getString("nex_hostileActivity", "cellDisruptName");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(StringHelper.getString("nex_hostileActivity", "cellDisruptDesc"),
                        0f);
            }

        };
    }
}
