package exerelin.campaign.ai.action.covert;

import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.RaiseRelations;

public class RaiseRelationsAction extends CovertAction {

    @Override
    public boolean generate() {
        CovertActionIntel intel = new RaiseRelations(null, pickTargetMarket(), getAgentFaction(), getTargetFaction(),
                null, false, null);
        return beginAction(intel);
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.RAISE_RELATIONS;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        return concern.getDef().hasTag("canDiplomacy") && concern.getDef().hasTag("diplomacy_positive");
    }
}
