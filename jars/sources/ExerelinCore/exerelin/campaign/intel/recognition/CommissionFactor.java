package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class CommissionFactor extends BaseRecognitionEventFactor {

    public static final int OUTLAW_SCORE = -5;

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (intel instanceof FactionRecognitionIntel fri) {
            if (fri.forRespectValues) {
                return computeRespectPoints();
            }
        }
        return 0;
    }

    public int computeRespectPoints() {
        FactionAPI comm = Misc.getCommissionFaction();
        if (comm == null) return 0;
        if (!isOutlawFaction(comm)) return 0;

        respectPoints = OUTLAW_SCORE;
        return respectPoints;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return String.format(FactionRecognitionIntel.getString("factorDesc_commission"), Misc.ucFirst(Misc.getCommissionFaction().getPersonNamePrefix()));
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
                tooltip.addPara(FactionRecognitionIntel.getString("factorTooltip_commission"), 0, faction.getBaseUIColor(), faction.getDisplayName());
            }
        };
    }
}
