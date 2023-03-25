package exerelin.campaign.ai.action.covert;

import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.DestabilizeMarket;
import exerelin.campaign.intel.agents.InstigateRebellion;

public class InstigateRebellionAction extends CovertAction {

    @Override
    public boolean generate() {
        CovertActionIntel intel = new InstigateRebellion(null, pickTargetMarket(), getAgentFaction(), getTargetFaction(),
                false, null);
        return beginAction(intel);
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.INSTIGATE_REBELLION;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        return concern.getDef().hasTag("canInvade") || concern.getDef().hasTag("canInstigateRebellion");
    }
}
