package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

public class ResponseFleetAI implements EveryFrameScript
{
    public static final float RESERVE_RESTORE_EFFICIENCY = 0.75f;
    public static Logger log = Global.getLogger(ResponseFleetAI.class);
    
    protected final ResponseFleetManager.ResponseFleetData data;
    protected float daysTotal = 0.0F;
    protected final CampaignFleetAPI fleet;
    protected boolean orderedReturn = false;
  
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
  
    protected void giveInitialAssignment()
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
            
            Script despawnScript = new Script() {
                @Override
                public void run() {
                    float points = fleet.getFleetPoints() * RESERVE_RESTORE_EFFICIENCY;
                    log.info("Response fleet despawning at base " + data.source.getName() + "; can restore " + points + " points");
                    ResponseFleetManager.modifyReserveSize(data.sourceMarket, points);
                }
            };
            
            SectorEntityToken destination = data.source;          
            this.fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
            this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, ExerelinUtilsFleet.getDaysToOrbit(fleet), StringHelper.getFleetAssignmentString("standingDown", null, "missionPatrol"), despawnScript);
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
        }
    }
}

