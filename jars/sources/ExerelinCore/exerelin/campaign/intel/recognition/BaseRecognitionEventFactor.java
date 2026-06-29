package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import lombok.Getter;

import java.awt.*;

public abstract class BaseRecognitionEventFactor extends BaseOneTimeFactor {

    // For one-off factors, these will be the values used
    // For monthly factors, these... serve as a cache that is updated with an undefined frequency and isn't read
    @Getter protected int recogPoints;
    @Getter protected int respectPoints;
    @Getter protected boolean infoMode;

    public BaseRecognitionEventFactor() {
        super(0);
    }

    public int getProgress(BaseEventIntel intel) {
        if (infoMode) return 0;
        if (intel instanceof FactionRecognitionIntel fri) {
            if (fri.forRespectValues) return Math.min(respectPoints, FactionRecognitionIntel.PROGRESS_MAX);
        }
        return Math.min(recogPoints, FactionRecognitionIntel.PROGRESS_MAX);
    }

    @Override
    public boolean isOneTime() {
        return false;
    }

    @Override
    public boolean isExpired() {
        if (!isOneTime() || infoMode) return false;
        return super.isExpired();
    }

    public void setInfoMode(boolean infoMode) {
        this.infoMode = infoMode;
        if (infoMode) timestamp = 0;
        else timestamp = Global.getSector().getClock().getTimestamp();
    }

    public boolean ignoreSuspension() {
        return false;
    }

    @Override
    public void addBulletPointForOneTimeFactor(BaseEventIntel intel, TooltipMakerAPI info, Color tc, float initPad) {
        String text = getBulletPointText(intel);
        if (text == null) text = getDesc(intel);
        if (text != null) {
            info.addPara(text, initPad, tc);
            if (recogPoints > 0) {
                info.addPara("%s " + FactionRecognitionIntel.getString("recognition"), 0, tc, Misc.getPositiveHighlightColor(),
                        getProgressStr(recogPoints));
            }
            if (respectPoints != 0) {
                info.addPara("%s " + FactionRecognitionIntel.getString("respect"), 0, tc,
                        respectPoints > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                        getProgressStr(respectPoints));
            }
        }
    }

    public String getProgressStr(int p) {
        if (p <= 0) return "" + p;
        return "+" + p;
    }

    public static boolean isOutlawFaction(FactionAPI faction) {
        NexFactionConfig conf = NexConfig.getFactionConfig(faction.getId());
        return conf.pirateFaction || conf.hostileToAll > 0 || DiplomacyTraits.hasTrait(faction.getId(), DiplomacyTraits.TraitIds.MONSTROUS);
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        if (infoMode) return !hasOtherFactorsOfClass(intel, this.getClass());
        return super.shouldShow(intel);
    }
}
