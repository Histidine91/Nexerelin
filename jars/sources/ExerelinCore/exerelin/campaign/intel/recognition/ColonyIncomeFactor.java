package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsGUI;

public class ColonyIncomeFactor extends BasePerColonyRecognitionFactor {

    public static final float INCOME_PER_SCORE = 2000;

    @Override
    int computeRespectPoints() {
        return 0;
    }

    @Override
    int computeRecognitionPoints() {
        float income = getMarketsContribution();
        if (income < 0) income = 0;
        this.recogPoints = (int)(income/INCOME_PER_SCORE);
        return recogPoints;
    }

    @Override
    float getMarketContribution(MarketAPI market) {
        return market.getNetIncome();
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return FactionRecognitionIntel.getString("factorDesc_colonyIncome");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        String credits = Misc.getWithDGS(INCOME_PER_SCORE);
        String str = String.format(FactionRecognitionIntel.getString("factorTooltip_colonyIncome"), credits);
        return NexUtilsGUI.createSimpleTextTooltip(str, TOOLTIP_WIDTH);
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        if (intel instanceof FactionRecognitionIntel fri) {
            return !fri.forRespectValues;
        }
        return super.shouldShow(intel);
    }
}
