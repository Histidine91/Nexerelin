package exerelin.campaign.ai.action.fleet;

import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.HasIndustryTarget;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.utilities.NexConfig;

public class RaidAction extends OffensiveFleetAction {

    @Override
    public boolean generate() {
        boolean success = super.generate();
        if (!success) return false;

        // if we're trying to kill a specific industry, notify the raid intel
        if (concern instanceof HasIndustryTarget && delegate instanceof NexRaidIntel) {
            HasIndustryTarget hit = (HasIndustryTarget)concern;
            ((NexRaidIntel)delegate).setPreferredIndustryTarget(
                    hit.getTargetIndustry() != null ? hit.getTargetIndustry().getId() : hit.getTargetIndustryId());
        }

        return true;
    }

    @Override
    public InvasionFleetManager.EventType getEventType() {
        return InvasionFleetManager.EventType.RAID;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        if (InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId()) < NexConfig.pointsRequiredForInvasionFleet * 0.75f)
            return false;
        return concern.getDef().hasTag("canRaid") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }
}
