package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;

public class SatBombFactor extends ConquerMarketFactor {

    MarketCMD.TempData actionData;

    public SatBombFactor(MarketAPI market, FactionAPI prevOwner, MarketCMD.TempData actionData) {
        super(market, prevOwner, false);
        this.actionData = actionData;
        computeScore();
    }

    public void computeScore() {
        if (actionData == null) return; // wait for constructor to finish

        int size = market.getSize();
        boolean mitigated = false;
        if (actionData != null && actionData instanceof Nex_MarketCMD.NexTempData actionData2) {
            size = actionData2.sizeBeforeBombardment;
            mitigated = actionData2.satBombExcuse == Nex_MarketCMD.SatBombExcuse.SAT_BOMBER;    // also monstrous and hidden, but those are handled elsewhere
        }
        double baseEffect = Math.pow(10, size-2);
        recogPoints = (int)(baseEffect * RECOG_SCORE_MULT);

        if (isOutlawFaction(prevOwner) || mitigated) return;
        respectPoints = (int)(-baseEffect * ROGUE_SCORE_MULT);
    }

    public int getSize() {
        int size = market.getSize();
        if (actionData != null && actionData instanceof Nex_MarketCMD.NexTempData actionData2) {
            size = actionData2.sizeBeforeBombardment;
        }
        return size;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return String.format(FactionRecognitionIntel.getString("factorDesc_satBomb"), market.getName(), getSize());
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        String str = FactionRecognitionIntel.getString("factorTooltip_satBomb");
        return NexUtilsGUI.createSimpleTextTooltip(str, TOOLTIP_WIDTH);
    }

    public boolean ignoreSuspension() {
        return true;
    }
}
