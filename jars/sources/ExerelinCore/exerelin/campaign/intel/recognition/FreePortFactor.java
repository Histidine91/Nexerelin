package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;

public class FreePortFactor extends BasePerColonyRecognitionFactor {

    public static final int RESPECT_MULT = -1;

    @Override
    int computeRespectPoints() {
        respectPoints = (int)(getMarketsContribution() * RESPECT_MULT);
        return respectPoints;
    }

    @Override
    int computeRecognitionPoints() {
        return 0;
    }

    @Override
    float getMarketContribution(MarketAPI market) {
        if (!market.isFreePort()) return 0;
        int score = market.getSize() - 2;
        score += market.getCommodityData(Commodities.DRUGS).getMaxSupply();
        score += market.getCommodityData(Commodities.HAND_WEAPONS).getMaxSupply();
        return score;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return FactionRecognitionIntel.getString("factorDesc_freePorts");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return NexUtilsGUI.createSimpleTextTooltip(FactionRecognitionIntel.getString("factorTooltip_freePorts"), TOOLTIP_WIDTH);
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        if (intel instanceof FactionRecognitionIntel fri) {
            return fri.forRespectValues;
        }
        return super.shouldShow(intel);
    }
}
