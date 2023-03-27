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
    public boolean canUseForConcern(StrategicConcern concern) {
        return concern.getDef().hasTag("canSatBomb");
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        priority.modifyFlat("base", 75, StrategicAI.getString("statBase", true));
    }

    @Override
    public boolean isValid() {
        if (!NexConfig.allowNPCSatBomb) return false;
        if (Tuning.getDaysSinceStart() < NexConfig.invasionGracePeriod) return false;
        if (!InvasionFleetManager.canSatBomb(ai.getFaction(), concern.getFaction())) return false;
        if (status == null && InvasionFleetManager.getManager().getSpawnCounter(ai.getFactionId()) < NexConfig.pointsRequiredForInvasionFleet)
            return false;

        return true;
    }
}
