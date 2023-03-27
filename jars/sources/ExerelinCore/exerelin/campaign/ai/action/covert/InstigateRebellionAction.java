package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.InstigateRebellion;
import exerelin.utilities.NexConfig;

public class InstigateRebellionAction extends CovertAction {

    @Override
    public boolean generate() {
        if (concern.getMarket() != null && !CovertOpsManager.canInstigateRebellion(concern.getMarket())) {
            return false;
        }

        MarketAPI target = pickTargetMarket();
        if (target == null) return false;

        if (isInstigateActionOngoing(target)) return false;

        CovertActionIntel intel = new InstigateRebellion(null, target, getAgentFaction(), getTargetFaction(),
                false, null);
        return beginAction(intel);
    }

    protected boolean isInstigateActionOngoing(MarketAPI market) {
        for (CovertActionIntel intel : CovertOpsManager.getManager().getOngoingCovertActionsOfType(InstigateRebellion.class)) {
            InstigateRebellion ir = (InstigateRebellion)intel;
            if (ir.getMarket() == market) return true;
        }
        return false;
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.INSTIGATE_REBELLION;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canInvade") && !concern.getDef().hasTag("canInstigateRebellion"))
            return false;

        if (concern.getMarket() != null) {
            return CovertOpsManager.canInstigateRebellion(concern.getMarket());
        }

        return true;
    }

    @Override
    public boolean isValid() {
        return NexConfig.enableInvasions;
    }
}
