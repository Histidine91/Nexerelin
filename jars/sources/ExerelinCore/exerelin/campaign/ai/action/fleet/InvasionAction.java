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

        if (!concern.getDef().hasTag("canInvade") && !concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM)) {
            return false;
        }

        float pointReq = NexConfig.pointsRequiredForInvasionFleet;
        float pointHave = InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId());

        if (pointHave < pointReq)
            return false;

        float pointCostEst = InvasionFleetManager.getInvasionPointCost(pointReq, getWantedFleetSizeForConcern(false), getEventType());
        if ((pointCostEst > pointReq * 2) && (pointCostEst > pointHave + pointReq)) {
            return false;
        }

        return true;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        // doesn't need the priority penalty relative to raids
        //priority.modifyFlat("base", 75, StrategicAI.getString("statBase", true));
    }
}
