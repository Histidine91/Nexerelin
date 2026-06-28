package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import lombok.Getter;
import org.magiclib.util.MagicTxt;

public class ReturnMarketFactor extends ConquerMarketFactor {

    @Getter protected FactionAPI recipient;

    public ReturnMarketFactor(MarketAPI market, FactionAPI prevOwner) {
        super(market, prevOwner, true);
        recipient = market.getFaction();
    }

    public void computeScore() {
        respectPoints = getMarketRogueValue(market);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return String.format(FactionRecognitionIntel.getString("factorDesc_returnMarket"), market.getName(), market.getSize());
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        FactionAPI faction = Misc.getCommissionFaction();
        if (faction == null) return null;
        return new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return TOOLTIP_WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                String str = String.format(FactionRecognitionIntel.getString("factorTooltip_returnMarket"), faction.getDisplayNameWithArticle());
                MagicTxt.addPara(tooltip, str, 0, Misc.getTextColor(), Misc.getHighlightColor());
                ((LabelAPI)tooltip.getPrev()).setHighlightColors(faction.getBaseUIColor(), Misc.getHighlightColor());
            }
        };
    }
}
