package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.missions.remnant.RemnantQuestUtils;

public class RemnantFriendlyCause extends GenericAlliedCause {

    public RemnantFriendlyCause(HostileActivityEventIntel intel) {
        super(intel, Factions.REMNANTS, RemnantHostileActivityFactor.class);
    }

    public boolean isActive() {
        PersonAPI midnight = RemnantQuestUtils.getDissonant();
        if (midnight == null) return false;
        ContactIntel ci = ContactIntel.getContactIntel(midnight);
        return ci != null && !ci.isEnding() && !ci.isEnded();
    }

    @Override
    public String getDesc() {
        return NexHostileActivityManager.getString("remnant_friendlyActivityCauseName");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                String midnightName = RemnantQuestUtils.getDissonant().getNameString();
                String str = String.format(NexHostileActivityManager.getString("remnant_friendlyActivityCauseTooltip"),
                        midnightName);
                tooltip.addPara(str, 0f);
            }
        };
    }
}
