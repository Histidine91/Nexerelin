package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class RespawnFleetAI extends InvasionFleetAI
{
    public static Logger log = Global.getLogger(RespawnFleetAI.class);
    
    public RespawnFleetAI(CampaignFleetAPI fleet, InvasionFleetManager.InvasionFleetData data)
    {
        super(fleet, data);
    }
        
    @Override
    protected void giveInitialAssignment()
    {
        // do nothing
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
        
        interval += days;
        if (interval > 0.25f) interval -= 0.25f;
        else return;
        
        FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
        if (assignment != null)
        {
            float fp = this.fleet.getFleetPoints();
            if (fp < this.data.startingFleetPoints / 2.0F) {
                giveStandDownOrders();
            }
            int marines = this.fleet.getCargo().getMarines();
            if (marines < data.marineCount * 0.4f) {
                // we lost over 60% of our marines, no more invading
                giveStandDownOrders();
            }
            
            if (data.targetMarket.getFaction() == fleet.getFaction())
            {
                // we already own this market
                giveStandDownOrders();
            }
            
            if (orderedReturn)
                return;
            
            if(assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE && data.target.getContainingLocation() == data.fleet.getContainingLocation()
                    && Misc.getDistance(data.target.getLocation(), data.fleet.getLocation()) < 600f)
            {
                fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, INVADE_ORBIT_TIME);
                
                if (!fleet.getFaction().isHostileTo(data.targetMarket.getFactionId()))
                    fleet.getFaction().setRelationship(data.targetMarket.getFactionId(), RepLevel.HOSTILE);
                
                if (!responseFleetRequested)
                {
                    ResponseFleetManager.requestResponseFleet(data.targetMarket, data.fleet);
                    broadcastHostile();
                    //responseFleetRequested = true;
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
            this.fleet.addAssignment(FleetAssignment.HOLD, market.getPrimaryEntity(), 2.0F, "invading " + market.getName());
        }
    }
    
    @Override
    protected void giveStandDownOrders()
    {
        // failed capture, reset relationship
        if (data.targetMarket.getFaction() != fleet.getFaction() && fleet.getFaction().isHostileTo(data.targetMarket.getFactionId()))
            fleet.getFaction().setRelationship(data.targetMarket.getFactionId(), 0);
        
        super.giveStandDownOrders();
    }
}

