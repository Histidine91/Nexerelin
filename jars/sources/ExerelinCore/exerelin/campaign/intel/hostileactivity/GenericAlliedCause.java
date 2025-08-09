package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.missions.remnant.RemnantQuestUtils;

public class GenericAlliedCause extends BaseHostileActivityCause2 {

    protected String factionId;
    protected Class factorClass;

    public GenericAlliedCause(HostileActivityEventIntel intel, String factionId, Class factorClass) {
        super(intel);
        this.factionId = factionId;
        this.factorClass = factorClass;
    }

    public BaseHostileActivityFactor getFactor() {
        return (BaseHostileActivityFactor)intel.getFactorOfClass(factorClass);
    }

    public boolean isActive() {
        return AllianceManager.areFactionsAllied(factionId, PlayerFactionStore.getPlayerFactionId());
    }

    @Override
    public boolean shouldShow() {
        return isActive();
    }

    // TODO
    @Override
    public String getDesc() {
        return NexHostileActivityManager.getString("generic_allianceCauseName");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                String midnightName = RemnantQuestUtils.getDissonant().getNameString();
                String str = String.format(NexHostileActivityManager.getString("generic_allianceCauseTooltip"),
                        midnightName);
                tooltip.addPara(str, 0f);
            }
        };
    }

    @Override
    public int getProgress() {
        if (!isActive()) return 0;
        BaseHostileActivityFactor fac = getFactor();
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
        BaseHostileActivityFactor fac = getFactor();
        if (fac == null) return 0;
        float otherMag = 0;
        for (HostileActivityCause2 cause : fac.getCauses()) {
            if (cause == this) continue;
            otherMag += cause.getMagnitudeContribution(system);
        }

        return otherMag * getEffectMultiplier();
    }

    public float getEffectMultiplier() {
        return -1;
    }
}
