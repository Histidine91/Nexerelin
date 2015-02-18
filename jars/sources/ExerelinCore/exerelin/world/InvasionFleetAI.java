package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import org.apache.log4j.Logger;

public class InvasionFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(InvasionFleetAI.class);
    
    private final InvasionFleetManager.InvasionFleetData data;
    private float daysTotal = 0.0F;
    private final CampaignFleetAPI fleet;
    private boolean orderedReturn = false;
  
    public InvasionFleetAI(CampaignFleetAPI fleet, InvasionFleetManager.InvasionFleetData data)
    {
        this.fleet = fleet;
        this.data = data;
        giveInitialAssignment();
    }
  
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
            if (orderedReturn)
                return;
            if(assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE && Misc.getDistance(data.target.getLocationInHyperspace(), data.fleet.getLocationInHyperspace()) < 300)
            {
                fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, 3f);
                //log.info("Invasion fleet " + this.fleet.getNameWithFaction() + " orbiting target");
            }
            // invade
            else if(assignment.getAssignment() == FleetAssignment.HOLD && Misc.getDistance(data.target.getLocationInHyperspace(), data.fleet.getLocationInHyperspace()) < 300 )
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
            if (system != null)
            {
                //log.info("Invasion fleet " + this.fleet.getNameWithFaction() + " en route to target");
                if (system != this.fleet.getContainingLocation()) {
                    this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 1000.0F, "travelling to the " + system.getBaseName() + " star system");
                }
                this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 3.0F, "invading " + market.getName());
                // once it reaches the "hold" part, that's our cue to actually run the invasion code
                this.fleet.addAssignment(FleetAssignment.HOLD, market.getPrimaryEntity(), 1.0F, "invading " + market.getName());
                // TODO actually tell the fleet to execute the invasion
            }
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

