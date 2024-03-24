package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.missions.remnant.RemnantQuestUtils;

public class RemnantFriendlyCause extends BaseHostileActivityCause2 {

    public RemnantFriendlyCause(HostileActivityEventIntel intel) {
        super(intel);
    }

    public RemnantHostileActivityFactor getFactor() {
        return (RemnantHostileActivityFactor)intel.getFactorOfClass(RemnantHostileActivityFactor.class);
    }

    public boolean isActive() {
        PersonAPI midnight = RemnantQuestUtils.getDissonant();
        return midnight != null && ContactIntel.getContactIntel(midnight) != null;
    }

    @Override
    public boolean shouldShow() {
        return isActive();
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                String midnightName = RemnantQuestUtils.getDissonant().getNameString();
                String str = String.format("[temp] Your association with the Remnants via %s leads to them grudgingly accepting your status as an uninvited neighbour.",
                        midnightName);
                tooltip.addPara(str, 0f);
            }
        };
    }

    @Override
    public int getProgress() {
        if (!isActive()) return 0;
        RemnantHostileActivityFactor fac = getFactor();
        if (fac == null) return 0;
        float otherProg = 0;
        for (HostileActivityCause2 cause : fac.getCauses()) {
            if (cause == this) continue;
            otherProg += cause.getProgress();
        }

        return Math.round(otherProg * getEffectMultiplier());
    }

    public float getMagnitudeContribution(StarSystemAPI system) {
        if (!isActive()) return 0;
        RemnantHostileActivityFactor fac = getFactor();
        if (fac == null) return 0;
        float otherMag = 0;
        for (HostileActivityCause2 cause : fac.getCauses()) {
            if (cause == this) continue;
            otherMag += cause.getMagnitudeContribution(system);
        }

        return otherMag * getEffectMultiplier();
    }

    public float getEffectMultiplier() {
        return -1f;
    }
}
