package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.impl.campaign.Tuning;
import exerelin.campaign.ai.SAIConstants;
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
        if (Tuning.getDaysSinceStart() < NexConfig.invasionGracePeriod) return false;
        if (!InvasionFleetManager.canSatBomb(ai.getFaction(), concern.getFaction())) return false;

        return concern.getDef().hasTag("canSatBomb");
    }
}
