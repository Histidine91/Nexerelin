package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BroadcastPlayerAction;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import java.util.List;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class InvasionFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(InvasionFleetAI.class);
    
    private static final float INVADE_ORBIT_TIME = 3f;
    private static final float INVADE_RESPONSE_DISTANCE = 1500f;
    
    private final InvasionFleetManager.InvasionFleetData data;
    private float daysTotal = 0.0F;
    private final CampaignFleetAPI fleet;
    private boolean orderedReturn = false;
    private boolean responseFleetRequested = false;
    private EveryFrameScript broadcastScript;
  
    public InvasionFleetAI(CampaignFleetAPI fleet, InvasionFleetManager.InvasionFleetData data)
    {
        this.fleet = fleet;
        this.data = data;
        giveInitialAssignment();
    }
    
    // TODO fix + test
    public void broadcastHostile()
    {
            broadcastScript = new EveryFrameScript() {
                    private float timeElapsed = 0;
                    private final IntervalUtil tracker = new IntervalUtil(0.05f, 0.15f);
                    private final CampaignFleetAPI ourFleet = data.fleet;
                    private boolean done = false;
                    public boolean runWhilePaused() {
                            return false;
                    }
                    public boolean isDone() {
                            return done;
                    }
                    public void advance(float amount) {
                            CampaignClockAPI clock = Global.getSector().getClock();

                            float days = clock.convertToDays(amount);
                            timeElapsed = timeElapsed + days;

                            if (tracker.intervalElapsed() && !done) {
                                    if (timeElapsed > INVADE_ORBIT_TIME+ 1f)
                                    {
                                            done = true;
                                            return;
                                    }
                                    BroadcastPlayerAction.broadcast(ActionType.HOSTILE, 750, "$exerelinRespondingToInvasion", data.fleet, data.target);
                                    List<CampaignFleetAPI> fleets = ourFleet.getContainingLocation().getFleets();
                                    for (CampaignFleetAPI fleet : fleets) {
                                            if (fleet == ourFleet) continue;
                                            if (fleet.getAI() instanceof CampaignFleetAIAPI 
                                                    && fleet.getFaction().isHostileTo(ourFleet.getFaction())) {
                                                    float dist = Misc.getDistance(ourFleet.getLocation(), fleet.getLocation());
                                                    if (dist <= INVADE_RESPONSE_DISTANCE) {
                                                            CampaignFleetAIAPI ai = (CampaignFleetAIAPI) fleet.getAI();
                                                            ai.addAssignmentAtStart(FleetAssignment.INTERCEPT, ourFleet, 3f, "intercepting " + ourFleet.getName(), null);
                                                    }
                                            }
                                    }
                            }
                    }
            };

            data.fleet.addScript(broadcastScript);
    }
  
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
        this.daysTotal += days;
        if (this.daysTotal > 150.0F)
        {
            giveStandDownOrders();
            return;
        }
        FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
        if (assignment != null)
        {
            float fp = this.fleet.getFleetPoints();
            if (fp < this.data.startingFleetPoints / 2.0F) {
                giveStandDownOrders();
            }
            int marines = this.fleet.getCargo().getMarines();
            if (marines < data.marineCount * 0.6f) {
                // we lost over 40% of our marines, no more invading
                giveStandDownOrders();
            }
            if(!data.target.getFaction().isHostileTo(fleet.getFaction()))
                    giveStandDownOrders();  // market is no longer hostile; abort invasion
            
            if (orderedReturn)
                return;
            
            if(assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE && data.target.getContainingLocation() == data.fleet.getContainingLocation()
                    && Misc.getDistance(data.target.getLocation(), data.fleet.getLocation()) < 600f)
            {
                fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, INVADE_ORBIT_TIME);
                if (!responseFleetRequested)
                {
                    ResponseFleetManager.requestResponseFleet(data.targetMarket, data.fleet);
                    //broadcastHostile();   // TODO
                    responseFleetRequested = true;
                }
            }
            // invade
            else if(assignment.getAssignment() == FleetAssignment.HOLD && data.target.getContainingLocation() == data.fleet.getContainingLocation()
                    && Misc.getDistance(data.target.getLocation(), data.fleet.getLocation()) < 600f)
            {
                // market is no longer hostile; abort invasion
                if(!data.target.getFaction().isHostileTo(fleet.getFaction()))
                    giveStandDownOrders();
                else
                    InvasionRound.AttackMarket(fleet, data.target, false);
            }
        }
        else
        {
            MarketAPI market = data.targetMarket;
            StarSystemAPI system = market.getStarSystem();
            String locName = market.getPrimaryEntity().getContainingLocation().getName();
            
            if (system != null)
            {
                locName = "the " + system.getName();
            }
            if (system != null && system != this.fleet.getContainingLocation()) {
                LocationAPI hyper = Global.getSector().getHyperspace();
                Vector2f dest = Misc.getPointAtRadius(system.getLocation(), 1500.0F);
                SectorEntityToken token = hyper.createToken(dest.x, dest.y);
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 1000.0F, "travelling to " + locName);
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 1000.0F, "travelling to " + market.getName());
            }
            else {
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 1000.0F, "travelling to " + market.getName());
            }
            this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), INVADE_ORBIT_TIME, "beginning invasion of " + market.getName());
            // once it reaches the "hold" part, that's our cue to actually run the invasion code
            this.fleet.addAssignment(FleetAssignment.HOLD, market.getPrimaryEntity(), 1.0F, "invading " + market.getName());

        }
    }
  
    @Override
    public boolean isDone()
    {
        return !this.fleet.isAlive();
    }
  
    @Override
    public boolean runWhilePaused()
    {
        return false;
    }
  
    private float getDaysToOrbit()
    {
        float daysToOrbit = 0.0F;
        if (this.fleet.getFleetPoints() <= 50.0F) {
            daysToOrbit += 2.0F;
        } else if (this.fleet.getFleetPoints() <= 100.0F) {
            daysToOrbit += 4.0F;
        } else if (this.fleet.getFleetPoints() <= 150.0F) {
            daysToOrbit += 6.0F;
        } else {
            daysToOrbit += 8.0F;
        }
        daysToOrbit *= (0.5F + (float)Math.random() * 0.5F);
        return daysToOrbit;
    }
  
    private void giveInitialAssignment()
    {
        if (data.noWait) return;
        float daysToOrbit = getDaysToOrbit();
        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, daysToOrbit, "preparing for invasion at " + this.data.source.getName());
    }
  
    private void giveStandDownOrders()
    {
        if (!this.orderedReturn)
        {
            log.info("Invasion fleet " + this.fleet.getNameWithFaction() + " standing down");
            this.orderedReturn = true;
            this.fleet.clearAssignments();
            
            SectorEntityToken destination = data.source;
            if (data.target.getFaction() == data.fleet.getFaction())
            {
                // our faction controls the original target, perhaps we captured it?
                // anyway, go ahead and despawn there if it's closer
                float distToSource = Misc.getDistance(data.fleet.getLocationInHyperspace(), data.source.getLocationInHyperspace());
                float distToTarget = Misc.getDistance(data.fleet.getLocationInHyperspace(), data.target.getLocationInHyperspace());
                if (distToSource > distToTarget)
                    destination = data.target;
            }
            
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination, 1000.0F, "returning to " + destination.getName());
            this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, getDaysToOrbit(), "ending mission at " + destination.getName());
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
        }
    }
}

