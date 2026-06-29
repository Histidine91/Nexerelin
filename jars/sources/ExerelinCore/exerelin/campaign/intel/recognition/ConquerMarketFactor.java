package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;
import lombok.Getter;

public class ConquerMarketFactor extends BaseRecognitionEventFactor {

    public static final float RECOG_SCORE_MULT = 2;
    public static final float ROGUE_SCORE_MULT = 2;

    @Getter protected MarketAPI market;
    @Getter protected FactionAPI prevOwner;
    @Getter protected boolean conqueringFromOrigOwner;

    public ConquerMarketFactor() {
        setInfoMode(true);
    }

    public ConquerMarketFactor(MarketAPI market, FactionAPI prevOwner, boolean conqueringFromOrigOwner) {
        this.market = market;
        this.prevOwner = prevOwner;
        this.conqueringFromOrigOwner = conqueringFromOrigOwner;
        computeScore();
    }

    public void computeScore() {
        if (market == null) return;
        int size = market.getSize();
        recogPoints = (int)(RECOG_SCORE_MULT * size * size * size);

        if (isOutlawFaction(prevOwner) || !conqueringFromOrigOwner) return;
        respectPoints = -getMarketRogueValue(market);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        if (market == null) return FactionRecognitionIntel.getString("factorDesc_conquerMarketInfo");
        return String.format(FactionRecognitionIntel.getString("factorDesc_conquerMarket"), market.getName(), market.getSize());
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        boolean aggr = infoMode || conqueringFromOrigOwner;
        String str = FactionRecognitionIntel.getString(aggr ? "factorTooltip_conquerMarketAggressive" : "factorTooltip_conquerMarket");
        return NexUtilsGUI.createSimpleTextTooltip(str, TOOLTIP_WIDTH);
    }

    public static int getMarketRogueValue(MarketAPI market) {
        int size = market.getSize();
        return (int)(ROGUE_SCORE_MULT * size * size * size);
    }

    @Override
    public boolean isOneTime() {
        return true;
    }

    @Override
    public boolean ignoreSuspension() {
        return true;
    }
}
