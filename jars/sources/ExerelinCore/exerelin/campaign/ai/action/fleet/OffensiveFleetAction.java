package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;

import java.util.List;

public abstract class OffensiveFleetAction extends BaseStrategicAction {

    public abstract InvasionFleetManager.EventType getEventType();

    @Override
    public boolean generate() {
        MarketAPI market = concern.getMarket();
        FactionAPI us = ai.getFaction();
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();

        // pick a target market if the concern doesn't have one yet
        if (market != null) {
            market = InvasionFleetManager.getManager().getTargetMarketForFleet(us, concern.getFaction(), null,
                    allMarkets, getEventType());
        }

        // pick an origin market
        MarketAPI origin = InvasionFleetManager.getManager().getSourceMarketForFleet(us, market.getLocationInHyperspace(), allMarkets);

        OffensiveFleetIntel intel = InvasionFleetManager.getManager().generateInvasionOrRaidFleet(origin, market,
                getEventType(), 1, new FleetPoolManager.RequisitionParams());
        if (intel != null)
        {
            InvasionFleetManager.getManager().modifySpawnCounterV2(ai.getFactionId(), -InvasionFleetManager.getInvasionPointCost(intel));
            setDelegate(intel);
            return true;
        }

        return false;
    }

    @Override
    public void updatePriority() {
        super.updatePriority();
        if (FleetPoolManager.USE_POOL) {
            float curPool = FleetPoolManager.getManager().getCurrentPool(ai.getFactionId());
            float max = FleetPoolManager.getManager().getMaxPool(ai.getFactionId());
            float proportion = curPool/max;
            if (max <= 0) proportion = 0;
            priority.modifyFlat("fleetPool", proportion, StrategicAI.getString("statFleetPool", true));
        }
    }

    public OffensiveFleetIntel getIntel() {
        return (OffensiveFleetIntel)delegate;
    }

    @Override
    public String getName() {
        if (getIntel() != null) return getIntel().getName();
        return getDef().id;
    }

    @Override
    public String getIcon() {
        if (getIntel() != null)  return getIntel().getIcon();
        return null;
    }
}
