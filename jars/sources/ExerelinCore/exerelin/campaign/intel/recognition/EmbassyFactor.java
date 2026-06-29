package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;

public class EmbassyFactor extends BasePerColonyRecognitionFactor {

    public static final String INDUSTRY_ID = "IndEvo_embassy";
    public static final int RECOG_PER_EMBASSY = 10;
    public static final int RESPECT_PER_EMBASSY = 10;

    @Override
    int computeRespectPoints() {
        respectPoints = (int)(getMarketsContribution() * RESPECT_PER_EMBASSY);
        return respectPoints;
    }

    @Override
    int computeRecognitionPoints() {
        recogPoints = (int)(getMarketsContribution() * RECOG_PER_EMBASSY);
        return recogPoints;
    }

    @Override
    float getMarketContribution(MarketAPI market) {
        Industry embassy = market.getIndustry(INDUSTRY_ID);
        if (embassy == null || embassy.getSpecialItem() == null) return 0;
        return 1;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return FactionRecognitionIntel.getString("factorDesc_embassies");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return NexUtilsGUI.createSimpleTextTooltip(FactionRecognitionIntel.getString("factorTooltip_embassies"), TOOLTIP_WIDTH);
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        return super.shouldShow(intel) || Global.getSettings().getModManager().isModEnabled("IndEvo");
    }
}
