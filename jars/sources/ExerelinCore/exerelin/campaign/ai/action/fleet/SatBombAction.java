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

        if (!InvasionFleetManager.canSatBomb(ai.getFaction(), concern.getFaction())) return false;
        if (status == null && InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId()) < NexConfig.pointsRequiredForInvasionFleet)
            return false;

        return true;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        priority.modifyFlat("base", 75, StrategicAI.getString("statBase", true));
    }
}
