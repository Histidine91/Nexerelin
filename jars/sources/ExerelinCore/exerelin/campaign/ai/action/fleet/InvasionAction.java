package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.impl.campaign.Tuning;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
import lombok.extern.log4j.Log4j;

@Log4j
public class InvasionAction extends OffensiveFleetAction {
    @Override
    public InvasionFleetManager.EventType getEventType() {
        return InvasionFleetManager.EventType.INVASION;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!NexConfig.enableInvasions) return false;

        if (Tuning.getDaysSinceStart() < NexConfig.invasionGracePeriod) {
            //log.info("Too early: " + Tuning.getDaysSinceStart());
            return false;
        }
        if (status == null && InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId()) < NexConfig.pointsRequiredForInvasionFleet)
            return false;

        return concern.getDef().hasTag("canInvade") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        // doesn't need the priority penalty relative to raids
        //priority.modifyFlat("base", 75, StrategicAI.getString("statBase", true));
    }
}
