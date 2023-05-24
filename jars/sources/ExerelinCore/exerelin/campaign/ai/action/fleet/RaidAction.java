package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.HasIndustryTarget;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.utilities.NexConfig;

import java.util.List;

public class RaidAction extends OffensiveFleetAction {

    @Override
    public boolean generate() {
        boolean success = super.generate();
        if (!success) return false;

        // if we're trying to kill a specific industry, notify the raid intel
        if (concern instanceof HasIndustryTarget && delegate instanceof NexRaidIntel) {
            HasIndustryTarget hit = (HasIndustryTarget)concern;
            List<Industry> targets = hit.getTargetIndustries();
            List<String> targetIds = hit.getTargetIndustryIds();
            if (targets != null && !targets.isEmpty()) {
                ((NexRaidIntel)delegate).setPreferredIndustryTarget(targets.get(0).getId());
            } else if (targetIds != null && !targetIds.isEmpty()) {
                ((NexRaidIntel)delegate).setPreferredIndustryTarget(targetIds.get(0));
            }
        }

        return true;
    }

    @Override
    public InvasionFleetManager.EventType getEventType() {
        return InvasionFleetManager.EventType.RAID;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canRaid") && !concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM))
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
}
