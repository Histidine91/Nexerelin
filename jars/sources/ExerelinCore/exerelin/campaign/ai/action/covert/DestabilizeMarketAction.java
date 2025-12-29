package exerelin.campaign.ai.action.covert;

import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.DestabilizeMarket;

public class DestabilizeMarketAction extends CovertAction {

    @Override
    public boolean generate() {

        CovertActionIntel intel = new DestabilizeMarket(null, pickTargetMarket(), getAgentFaction(), getTargetFaction(),
                false, null);
        return beginAction(intel);
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.DESTABILIZE_MARKET;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!super.canUse(concern)) return false;
        return concern.getDef().hasTag("canDestabilize") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }
}
