package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.HasCommodityTarget;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.DestabilizeMarket;
import exerelin.campaign.intel.agents.DestroyCommodityStocks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        return concern.getDef().hasTag("canDestabilize") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }
}
