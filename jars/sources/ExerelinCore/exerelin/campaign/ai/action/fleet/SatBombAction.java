package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.impl.campaign.Tuning;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;

public class SatBombAction extends OffensiveFleetAction {
    @Override
    public InvasionFleetManager.EventType getEventType() {
        return InvasionFleetManager.EventType.SAT_BOMB;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!NexConfig.allowNPCSatBomb) return false;
        if (Tuning.getDaysSinceStart() < NexConfig.invasionGracePeriod) return false;
        if (!concern.getDef().hasTag("canSatBomb")) return false;

        if (!InvasionFleetManager.canSatBomb(ai.getFaction(), faction)) return false;

        float pointReq = NexConfig.pointsRequiredForInvasionFleet;
        float pointHave = InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId());

        if (pointHave < pointReq)
            return false;

        float pointCostEst = getWantedFleetSizeForConcern(false);
        if ((pointCostEst > pointReq * 2) && (pointCostEst > pointHave + pointReq)) {
            return false;
        }

        return super.canUse(concern);
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        priority.modifyFlat("base", 75, StrategicAI.getString("statBase", true));
    }
}
