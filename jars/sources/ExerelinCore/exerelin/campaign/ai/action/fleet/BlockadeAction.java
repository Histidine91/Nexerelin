package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.Global;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;

public class BlockadeAction extends OffensiveFleetAction {

    @Override
    public InvasionFleetManager.EventType getEventType() {
        return InvasionFleetManager.EventType.RAID;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canBlockade") && !concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM))
            return false;

        float pointReq = NexConfig.pointsRequiredForInvasionFleet;
        float pointHave = InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId());

        if (pointHave < pointReq * 0.75f)
            return false;

        float pointCostEst = InvasionFleetManager.getInvasionPointCost(pointReq, getWantedFleetSizeForConcern(true), getEventType());
        if ((pointCostEst > pointReq * 2) && (pointCostEst > pointHave + pointReq)) {
            return false;
        }

        if (concern.getFaction() == Global.getSector().getFaction("nex_derelict")) return false;
        if (concern.getMarket() != null && concern.getMarket().getFaction().getId().equals("nex_derelict")) return false;

        return super.canUse(concern);
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
    }
}
