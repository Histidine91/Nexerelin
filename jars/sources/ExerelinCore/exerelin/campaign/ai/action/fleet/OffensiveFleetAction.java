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
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMath;
import lombok.extern.log4j.Log4j;

import java.util.List;

@Log4j
public abstract class OffensiveFleetAction extends BaseStrategicAction {

    public abstract InvasionFleetManager.EventType getEventType();

    @Override
    public boolean generate() {
        MarketAPI market = concern.getMarket();
        FactionAPI us = ai.getFaction();
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        List<MarketAPI> potentialTargets = getPotentialTargets();

        // pick a target market if the concern doesn't have one yet
        if (market == null) {
            //log.info("Picking random market");
            market = InvasionFleetManager.getManager().getTargetMarketForFleet(us, concern.getFaction(), null,
                    potentialTargets, getEventType());
        }
        if (market == null) {
            //log.info("No market found");
            return false;
        }

        // pick an origin market
        MarketAPI origin = InvasionFleetManager.getManager().getSourceMarketForFleet(us, market.getLocationInHyperspace(), allMarkets);
        if (origin == null) {
            //log.info("No origin found");
            return false;
        }

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
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        if (FleetPoolManager.USE_POOL) {

            float curPool = FleetPoolManager.getManager().getCurrentPool(ai.getFactionId());
            float max = FleetPoolManager.getManager().getMaxPool(ai.getFactionId());
            if (max < 1) max = 1;

            float ratio = Math.min(curPool/max, 1.25f);
            float proportion = NexUtilsMath.lerp(0.4f, 1f, ratio);

            if (max <= 0 || curPool <= 0) proportion = 0;
            if (ratio > 0.75f) proportion = Math.max(proportion, 1);

            priority.modifyMult("fleetPool", proportion, StrategicAI.getString("statFleetPool", true));
        }
    }

    public List<MarketAPI> getPotentialTargets() {
        List<MarketAPI> fromConcern = concern.getMarkets();
        if (fromConcern != null && !fromConcern.isEmpty()) return fromConcern;
        return Global.getSector().getEconomy().getMarketsCopy();
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (ai.getFaction().isPlayerFaction() && !NexConfig.followersInvasions)
            return false;

        return true;
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

    protected float getWantedFleetSizeForConcern(boolean countAllHostile) {
        if (concern.getMarket() == null) return 0;
        return InvasionFleetManager.getWantedFleetSize(ai.getFaction(), concern.getMarket(), 0, countAllHostile);
    }
}
