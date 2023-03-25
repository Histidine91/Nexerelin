package exerelin.campaign.ai.action.fleet;

import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;

public class RaidAction extends OffensiveFleetAction {
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
