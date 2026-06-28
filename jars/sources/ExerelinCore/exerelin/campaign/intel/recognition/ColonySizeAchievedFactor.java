package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;
import lombok.Getter;

public class ColonySizeAchievedFactor extends BaseRecognitionEventFactor {

    public static final int[] POINTS_PER_SIZE = new int[] {
            0, 0, 0,
            50,    // size 3; low so you can't spam your way into recognition
            200,   // size 4;
            600,   // size 5;
            1500,  // size 6;
            3000,  // size 7;
            10000, // size 8 and up
    };

    @Getter protected MarketAPI market;
    @Getter protected int size;

    public ColonySizeAchievedFactor(MarketAPI market, int size) {
        this.market = market;
        this.size = size;
        computeScore();
    }

    public void computeScore() {
        int index = size;
        if (index >= POINTS_PER_SIZE.length) index = POINTS_PER_SIZE.length - 1;
        recogPoints = POINTS_PER_SIZE[size];
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return String.format(FactionRecognitionIntel.getString("factorDesc_colonySize"), market.getName(), size);
    }

    @Override
    public boolean isOneTime() {
        return true;
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return NexUtilsGUI.createSimpleTextTooltip(FactionRecognitionIntel.getString("factorTooltip_colonySize"), TOOLTIP_WIDTH);
    }
}
