package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

public class ResponseFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(ResponseFleetAI.class);
    
    private final ResponseFleetManager.ResponseFleetData data;
    private float daysTotal = 0.0F;
    private final CampaignFleetAPI fleet;
    private boolean orderedReturn = false;
  
    public ResponseFleetAI(CampaignFleetAPI fleet, ResponseFleetManager.ResponseFleetData data)
    {
        this.fleet = fleet;
        this.data = data;
        giveInitialAssignment();
    }
    
    float interval = 0;
  
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
        this.daysTotal += days;
        if (this.daysTotal > 60.0F)
        {
            giveStandDownOrders();
            return;
        }
        
        interval += days;
        if (interval >= 0.25f) interval -= 0.25f;
        else return;
        
        FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
        float fp = this.fleet.getFleetPoints();
        boolean tooWeak = true;
        SectorEntityToken target = this.data.target;
        if (target != null && target.isAlive())
        {
            CampaignFleetAPI targetFleet = (CampaignFleetAPI)this.data.target;
            if (targetFleet.getFleetPoints() < fp * 2) tooWeak = false;
        }
        if (fp < this.data.startingFleetPoints / 2.0F && tooWeak) {
            giveStandDownOrders();
        }
        else
        {
            MarketAPI market = data.sourceMarket;
            StarSystemAPI system = market.getStarSystem();
            if (system != null)
            {
                if (system != this.fleet.getContainingLocation()) {
                    this.fleet.addAssignment(FleetAssignment.DELIVER_SUPPLIES, market.getPrimaryEntity(), 1000.0F, "travelling to the " + system.getBaseName() + " star system");
                }
                this.fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, market.getPrimaryEntity(), 1000.0F, "defending " + market.getName());
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
        String targetName = this.data.target.getName();
        if (this.data.target == Global.getSector().getPlayerFleet())
            targetName = "your fleet";
        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, 0.1f, StringHelper.getFleetAssignmentString("scramblingFrom", this.data.sourceMarket.getName()));
        this.fleet.addAssignment(FleetAssignment.INTERCEPT, this.data.target, 3f, StringHelper.getFleetAssignmentString("intercepting", targetName));
    }
  
    protected void giveStandDownOrders()
    {
        if (!this.orderedReturn)
        {
            //log.info("Response fleet " + this.fleet.getNameWithFaction() + " standing down");
            this.orderedReturn = true;
            this.fleet.clearAssignments();
            
            SectorEntityToken destination = data.source;          
            this.fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
            //this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, getDaysToOrbit(), StringHelper.getFleetAssignmentString("standingDown", null, "missionPatrol"));
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
        }
    }
}

