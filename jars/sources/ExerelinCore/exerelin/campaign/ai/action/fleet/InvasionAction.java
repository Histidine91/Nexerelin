package exerelin.campaign.ai.action.fleet;

import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.ai.concern.VulnerableEnemyTargetConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
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

        if (NexUtils.getTrueDaysSinceStart() < NexConfig.invasionGracePeriod) {
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

        return super.canUse(concern);
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        // doesn't need the priority penalty relative to raids
        //priority.modifyFlat("base", 75, StrategicAI.getString("statBase", true));

        if (concern instanceof VulnerableEnemyTargetConcern) {
            priority.modifyMult("vulnerableTarget", 1.2f, StrategicAI.getString("statConcernType", true));
        }
    }
}
