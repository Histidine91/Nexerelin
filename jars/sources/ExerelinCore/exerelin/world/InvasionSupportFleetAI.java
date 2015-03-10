package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class InvasionSupportFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(InvasionSupportFleetAI.class);
       
    private final InvasionFleetManager.InvasionFleetData data;
    private float daysTotal = 0.0F;
    private final CampaignFleetAPI fleet;
    private boolean orderedReturn = false;
  
    public InvasionSupportFleetAI(CampaignFleetAPI fleet, InvasionFleetManager.InvasionFleetData data)
    {
        this.fleet = fleet;
        this.data = data;
        giveInitialAssignment();
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
            
            if (orderedReturn)
                return;
        }
        else
        {
            MarketAPI market = data.targetMarket;
            StarSystemAPI system = market.getStarSystem();
            String locName = market.getPrimaryEntity().getContainingLocation().getName();
            if (system != null)
            {
                locName = system.getBaseName();
                Vector2f dest = Misc.getPointAtRadius(system.getLocation(), 1500.0F);
                LocationAPI loc = Global.getSector().getHyperspace();
                SectorEntityToken token = loc.createToken(dest.x, dest.y);
                if (system != this.fleet.getContainingLocation()) {
                  this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 1000.0F, "travelling to the " + system.getBaseName() + " star system");
                }
                if (Math.random() > 0.8D) {
                  this.fleet.addAssignment(FleetAssignment.RAID_SYSTEM, system.getHyperspaceAnchor(), 40.0F, "attacking around the " + system.getBaseName() + " star system");
                } else if (Math.random() > 0.5D) {
                  this.fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, market.getPrimaryEntity(), 40.0F, "attacking " + market.getName());
                } else {
                  this.fleet.addAssignment(FleetAssignment.RAID_SYSTEM, system.getStar(), 40.0F, "attacking the " + system.getBaseName() + " star system");
                }
            }
            else
            {
                this.fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, market.getPrimaryEntity(), 40.0F, "attacking " + market.getName());
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
        if (data.noWait) return;
        float daysToOrbit = getDaysToOrbit();
        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, daysToOrbit, "preparing for invasion at " + this.data.source.getName());
    }
  
    private void giveStandDownOrders()
    {
        if (!this.orderedReturn)
        {
            log.info("Invasion support fleet " + this.fleet.getNameWithFaction() + " standing down");
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

