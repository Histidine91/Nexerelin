package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.concern.ImportDependencyConcern;
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

        MarketAPI target = pickTargetMarket();
        if (target == null) {
            //log.info("No target found");
            return false;
        }

        MarketAPI origin = pickOriginMarket(target);
        if (origin == null) {
            //log.info("No origin found");
            return false;
        }

        InvasionFleetManager.EventType type = getEventType();
        // always invade rather than raid derelicts
        if (target.getFactionId().equals("nex_derelict")
                && (type == InvasionFleetManager.EventType.RAID || type == InvasionFleetManager.EventType.SAT_BOMB)) {
            if (this instanceof PirateRaidAction) return false; // don't let our proxies invade derelicts
            type = InvasionFleetManager.EventType.INVASION;
        }


        OffensiveFleetIntel intel = InvasionFleetManager.getManager().generateInvasionOrRaidFleet(origin, target,
                type, getSizeMult(), getFleetPoolRequisitionParams());
        if (intel != null)
        {
            float cost = InvasionFleetManager.getInvasionPointCost(intel);
            InvasionFleetManager.getManager().modifySpawnCounterV2(ai.getFactionId(), -cost);
            intel.setInvPointsSpent((int)cost);
            setDelegate(intel);
            return true;
        }

        return false;
    }

    public FleetPoolManager.RequisitionParams getFleetPoolRequisitionParams() {
        return new FleetPoolManager.RequisitionParams();
    }

    public MarketAPI pickTargetMarket() {
        MarketAPI market = concern.getMarket();

        // check if the market specified by concern is hostile (it may not be, e.g. retaliation concern sometimes targets markets that have already been taken)
        boolean blockedByNonHostile = false;
        if (market != null) {
            if (ai.getFaction() == market.getFaction()) blockedByNonHostile = false;
            else if (ai.getFaction().getRelationshipLevel(market.getFaction()).ordinal() > getMaxRelToTarget(market.getFaction()).ordinal())
                blockedByNonHostile = true;
        }

        if (market != null && !blockedByNonHostile && !InvasionFleetManager.getManager().isValidInvasionOrRaidTarget(
                ai.getFaction(), null, market, this.getEventType(), false)) {
            return market;
        }
        return InvasionFleetManager.getManager().getTargetMarketForFleet(ai.getFaction(), faction, null,
                getPotentialTargets(), getEventType());
    }

    public MarketAPI pickOriginMarket(MarketAPI target) {
        return InvasionFleetManager.getManager().getSourceMarketForFleet(ai.getFaction(), target.getLocationInHyperspace(),
                Global.getSector().getEconomy().getMarketsCopy());
    }

    public float getSizeMult() {
        return 1;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();

        String aifid = ai.getFactionId();

        // modify priority based on fleet pool/invasion points available
        if (FleetPoolManager.USE_POOL) {
            float curPool = FleetPoolManager.getManager().getCurrentPool(aifid);
            float max = FleetPoolManager.getManager().getMaxPool(aifid);
            if (max < 1) max = 1;

            float ratio = Math.min(curPool/max, 1.25f);
            float proportion = NexUtilsMath.lerp(0.4f, 1f, ratio);

            if (max <= 0 || curPool <= 0) proportion = 0;
            if (ratio > 0.75f) proportion = Math.max(proportion, 1);

            priority.modifyMult("fleetPool", proportion, StrategicAI.getString("statFleetPool", true));
        } else {
            float invPoints = InvasionFleetManager.getManager().getSpawnCounter(aifid);
            float baseline = NexConfig.pointsRequiredForInvasionFleet;

            float mult = invPoints/baseline * 0.5f + 0.5f;
            if (mult < 0.5f) mult = 0.5f;
            else if (mult > 1.5f) mult = 1.5f;
            priority.modifyMult("invPoints", mult, StrategicAI.getString("statInvPoints", true));

        }
    }

    public List<MarketAPI> getPotentialTargets() {
        List<MarketAPI> fromConcern = concern.getMarkets();
        if (fromConcern != null && !fromConcern.isEmpty()) return fromConcern;
        return Global.getSector().getEconomy().getMarketsCopy();
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!NexConfig.enableHostileFleetEvents) return false;

        if (ai.getFaction().isPlayerFaction() && !NexConfig.followersInvasions)
            return false;

        // don't try to fix an import dependency with this if none of the targets actually produce the thing
        if (concern instanceof ImportDependencyConcern) {
            if (concern.getMarkets().isEmpty()) {
                return false;
            }
        }

        if (concern.getMarket() != null) {
            MarketAPI market = concern.getMarket();
            if (!InvasionFleetManager.getManager().isValidInvasionOrRaidTarget(
                    ai.getFaction(), null, market, this.getEventType(), false)) return false;
        }

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
