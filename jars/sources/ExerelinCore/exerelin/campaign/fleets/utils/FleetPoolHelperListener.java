package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.PerseanLeagueBlockade;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.econ.ResourcePoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.NexRouteManager;
import exerelin.utilities.NexUtilsFleet;

import java.util.ArrayList;
import java.util.List;


/**
 * Implements the basic functionality for fleet pool integration with the route manager and other parts of the game.
 */
public class FleetPoolHelperListener extends BaseCampaignEventListener implements NexRouteManagerListener, EveryFrameScript {

    public static final String DATA_KEY_ESTIMATED_FP = "nex_estimatedFP";
    public static final float PATROL_POOL_RETURN_EFFICIENCY = 0.98f;
    public static final float ECONOMY_POOL_RETURN_EFFICIENCY = 0.95f;
    public static final String MEM_KEY_STARTING_FP = "$nex_fphl_startingFP";

    protected List<NexRouteManager.NexRouteData> toAdd = new ArrayList<>();

    public static void create(SectorAPI sector) {
        FleetPoolHelperListener fphelp = new FleetPoolHelperListener();
        sector.addTransientScript(fphelp);
        sector.addTransientListener(fphelp);
        sector.getListenerManager().addListener(fphelp, true);
    }

    public FleetPoolHelperListener() {
        super(false);
    }

    /**
     * Returns the route's fleet points to the fleet pool, if applicable.
     */
    public void returnToPool(NexRouteManager.NexRouteData route) {
        if (FleetPoolManager.hasRouteReturnedToPool(route)) return;
        float returnMult = FleetPoolManager.getRouteReturnEfficiency(route);
        if (returnMult <= 0) return;

        float fp = NexUtilsFleet.getRouteFPWithDamage(route);
        if (fp <= 0) return;
        float refund = fp * returnMult;
        if (refund < 0) refund = 0;
        if (refund <= 0) return;

        String factionId = FleetPoolManager.getRouteFactionId(route);
        if (factionId == null) factionId = route.getFactionId();
        FleetPoolManager.getManager().modifyPool(factionId, refund);
        FleetPoolManager.setRouteReturnedToPool(route, true);

        if (NexRouteManager.DEBUG_MODE) {
            var name = route.toString();
            if (route.getActiveFleet() != null) name = route.getActiveFleet().getNameWithFaction();
            float origFP = NexUtilsFleet.getRouteFP(route);
            Float damage = route.getExtra().damage;
            Global.getLogger(this.getClass()).info(String.format("Route/fleet %s returning %.1f of %.1f points to fleet pool, damage %.2f", name, refund, origFP, damage));
        }
    }

    /**
     * Delay route addition by one frame because some route users don't specify the route strength until after adding it.
     * @param route
     */
    public void addRoute(NexRouteManager.NexRouteData route) {
        ResourcePoolManager.RequisitionParams rp = new ResourcePoolManager.RequisitionParams();
        rp.amount = NexUtilsFleet.getRouteFPWithDamage(route);
        if (rp.amount <= 0) return;
        String factionId = FleetPoolManager.getRouteFactionId(route);

        float curr = FleetPoolManager.getManager().getCurrentPool(factionId);
        FleetPoolManager.getManager().drawFromPool(factionId, rp);

        if (NexRouteManager.DEBUG_MODE) {
            var name = route.toString();
            if (route.getActiveFleet() != null) name = route.getActiveFleet().getNameWithFaction();
            Global.getLogger(this.getClass()).info(String.format("%s drawing %.1f of %.1f points from fleet pool", name, rp.amountDrawn, curr));
        }

        if (route.getExtra().fp == null && !route.getDataStore().containsKey(DATA_KEY_ESTIMATED_FP)) {
            route.getDataStore().put(DATA_KEY_ESTIMATED_FP, NexUtilsFleet.getRouteFP(route));
        }
    }

    public void deductPoolForFleetSpawn(CampaignFleetAPI fleet, boolean useMarketFleetMultAdjustment) {
        float fp = getFleetFPForPool(fleet, useMarketFleetMultAdjustment);

        ResourcePoolManager.RequisitionParams rp = new ResourcePoolManager.RequisitionParams(fp, 0, null, 1);
        FleetPoolManager.getManager().drawFromPool(fleet.getFaction().getId(), rp);
    }

    public void returnFleetToPool(CampaignFleetAPI fleet, boolean useMarketFleetMultAdjustment) {
        float fp = getFleetFPForPool(fleet, useMarketFleetMultAdjustment);
        FleetPoolManager.getManager().modifyPool(fleet.getFaction().getId(), fp);
    }

    protected float getFleetFPForPool(CampaignFleetAPI fleet, boolean useMarketFleetMultAdjustment) {
        float fp = fleet.getFleetData().getFleetPointsUsed();
        fp /= InvasionFleetManager.getFactionDoctrineFleetSizeMult(fleet.getFaction());
        if (useMarketFleetMultAdjustment) {
            MarketAPI source = Misc.getSourceMarket(fleet);
            if (source != null) fp /= source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        }

        return fp;
    }

    public void preAddRoute(NexRouteManager.NexRouteData route, String factionId, float returnEfficiency) {
        FleetPoolManager.setRouteFactionId(route, factionId);
        FleetPoolManager.setRouteReturnEfficiency(route, returnEfficiency);
        toAdd.add(route);
    }

    @Override
    public void reportRouteAdded(RouteManager.RouteData route) {
        if (route instanceof NexRouteManager.NexRouteData nrd) {
            if (route.getCustom() instanceof MilitaryBase.PatrolFleetData) {
                preAddRoute(nrd, route.getFactionId(), PATROL_POOL_RETURN_EFFICIENCY);
            }
            else if (route.getCustom() instanceof EconomyFleetAssignmentAI.EconomyRouteData) {
                // don't think these guys have usable FP
                //addRoute(nrd, route.getFactionId(), ECONOMY_POOL_RETURN_EFFICIENCY);
            }
            else if (route.getSpawner() instanceof PerseanLeagueBlockade) {

            // was gonna add pirate RaidIntels here but does anything even use that anymore?
            //else if (route.getSpawner() instanceof RaidIntel && (route.getSpawner() instanceof OffensiveFleetIntel))


            }
        }
    }

    @Override
    public void reportRouteRemoved(RouteManager.RouteData route) {
        if (route.getSpawner() instanceof FleetGroupIntel) return;  // route manager approach is broadly incompatible with how FleetGroupIntel works
        if (route instanceof NexRouteManager.NexRouteData nrd) {
            returnToPool(nrd);
        }
    }

    @Override
    public void reportRouteFleetSpawned(CampaignFleetAPI fleet, RouteManager.RouteData route) {

    }

    @Override
    public void reportRouteFleetDespawned(CampaignFleetAPI fleet, RouteManager.RouteData route) {

    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        for (NexRouteManager.NexRouteData route : toAdd) {
            addRoute(route);
        }
        toAdd.clear();
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (!fleet.getMemoryWithoutUpdate().contains(MEM_KEY_STARTING_FP)) {
            fleet.getMemoryWithoutUpdate().set(MEM_KEY_STARTING_FP, fleet.getFleetData().getFleetPointsUsed());
        }
        if (fleet.getMemoryWithoutUpdate().contains("$dhafm_ID")) {
            // Colony Crisis harassment fleet
        }
    }
}
