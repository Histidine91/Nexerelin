package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import data.scripts.world.exigency.Tasserus;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.List;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class InvasionFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(InvasionFleetAI.class);
    
    public static final float INVADE_ORBIT_TIME = 2.5f;
    public static final float INVADE_RESPONSE_DISTANCE = 1500f;
    public static final float RESERVE_CONVERSION_EFFICIENCY = 0.8f;
    
    protected final InvasionFleetManager.InvasionFleetData data;
    protected float daysTotal = 0.0F;
    protected final CampaignFleetAPI fleet;
    protected boolean orderedReturn = false;
    protected boolean responseFleetRequested = false;
    //protected EveryFrameScript broadcastScript;
  
    public InvasionFleetAI(CampaignFleetAPI fleet, InvasionFleetManager.InvasionFleetData data)
    {
        this.fleet = fleet;
        this.data = data;
        giveInitialAssignment();
    }
    
    
    protected void broadcastHostile()
    {
        if (orderedReturn) return;
        
        List<CampaignFleetAPI> fleets = fleet.getContainingLocation().getFleets();
        for (CampaignFleetAPI otherFleet : fleets) {
            if (otherFleet == fleet) continue;
            if (otherFleet.getAI() instanceof CampaignFleetAIAPI && fleet.getFaction().isHostileTo(otherFleet.getFaction())) 
            {
                float dist = Misc.getDistance(otherFleet.getLocation(), fleet.getLocation());
                //log.info("Distance of fleet " + otherFleet.getName() + ": " + dist);
                if (dist <= INVADE_RESPONSE_DISTANCE) {
                    CampaignFleetAIAPI ai = (CampaignFleetAIAPI) otherFleet.getAI();
                    EncounterOption option = ai.pickEncounterOption(null, fleet);
                    //log.info("Response type of fleet " + otherFleet.getName() + ": " + option.name());
                    if (option == EncounterOption.ENGAGE || option == EncounterOption.HOLD)
                    {
                        ai.addAssignmentAtStart(FleetAssignment.INTERCEPT, fleet, 2f, 
                                StringHelper.getFleetAssignmentString("intercepting", fleet.getName()), null);
                        //log.info("Responding to invasion: " + otherFleet.getName() + ", " + fleet.getName());
                    }
                }
            }
        }
    }
  
    float interval = 0;
    
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
			if(!data.target.getFaction().isHostileTo(fleet.getFaction()))
            {
                if (data.event != null)
                {
                    if (data.target.getFaction() == fleet.getFaction())
                        data.event.endEvent(InvasionFleetEvent.FleetReturnReason.ALREADY_CAPTURED, data.target);
                    else
                        data.event.endEvent(InvasionFleetEvent.FleetReturnReason.NO_LONGER_HOSTILE, data.target);
                }
                giveStandDownOrders();  // market is no longer hostile; abort invasion
            }
			
            float fp = this.fleet.getFleetPoints();
            if (fp < this.data.startingFleetPoints / 2.0F) {
                if (data.event != null) data.event.endEvent(InvasionFleetEvent.FleetReturnReason.SHIP_LOSSES, data.fleet);
                giveStandDownOrders();
            }
            int marines = this.fleet.getCargo().getMarines();
            if (marines < data.marineCount * 0.4f) {
                // we lost over 60% of our marines, no more invading
                if (data.event != null) data.event.endEvent(InvasionFleetEvent.FleetReturnReason.MARINE_LOSSES, data.target);
                giveStandDownOrders();
            }
            
            if (orderedReturn)
                return;
            
            if(assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE && data.target.getContainingLocation() == data.fleet.getContainingLocation()
                    && Misc.getDistance(data.target.getLocation(), data.fleet.getLocation()) < 600f)
            {
                fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, INVADE_ORBIT_TIME);
                if (!responseFleetRequested)
                {
                    ResponseFleetManager.requestResponseFleet(data.targetMarket, data.fleet);
                    broadcastHostile();
                    //responseFleetRequested = true;
                }
				data.targetMarket.getMemoryWithoutUpdate().set("$beingInvaded", true, INVADE_ORBIT_TIME);
            }
            // invade
            else if(assignment.getAssignment() == FleetAssignment.HOLD && data.target.getContainingLocation() == data.fleet.getContainingLocation()
                    && Misc.getDistance(data.target.getLocation(), data.fleet.getLocation()) < 600f)
            {
                // market is no longer hostile; abort invasion
                if(!data.target.getFaction().isHostileTo(fleet.getFaction()))
                    giveStandDownOrders();
                else
				{
                    InvasionRound.InvasionRoundResult result = InvasionRound.AttackMarket(fleet, data.target, false);
					if (result.success)
					{
						if (data.event != null)
							data.event.endEvent(InvasionFleetEvent.FleetReturnReason.MISSION_COMPLETE, data.target);
						data.targetMarket.getMemoryWithoutUpdate().unset("$beingInvaded");
					}
				}
            }
        }
        else
        {
            MarketAPI market = data.targetMarket;
            StarSystemAPI system = market.getStarSystem();
            String locName = market.getPrimaryEntity().getContainingLocation().getName();
            String marketName = market.getName();
            
            if (system != null)
            {
                locName = "the " + system.getName();
            }
            if (system != null && system != this.fleet.getContainingLocation()) {
                LocationAPI hyper = Global.getSector().getHyperspace();
                Vector2f dest = Misc.getPointAtRadius(system.getLocation(), 1500.0F);
                SectorEntityToken token = hyper.createToken(dest.x, dest.y);
                //this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", locName));
                //this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", marketName));
                this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", locName));
                this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, market.getPrimaryEntity(), 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", marketName));
            }
            else {
                //this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", marketName));
                this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, market.getPrimaryEntity(), 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", marketName));
            }
            this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), INVADE_ORBIT_TIME, StringHelper.getFleetAssignmentString("beginningInvasion", marketName));
            // once it reaches the "hold" part, that's our cue to actually run the invasion code
            this.fleet.addAssignment(FleetAssignment.HOLD, market.getPrimaryEntity(), 2.0F, StringHelper.getFleetAssignmentString("invading", marketName));

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
        if (data.noWait) return;
        float daysToOrbit = ExerelinUtilsFleet.getDaysToOrbit(fleet);
        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, data.source, daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionInvasion"));
    }
  
    protected void giveStandDownOrders()
    {
        if (!this.orderedReturn)
        {
            //log.info("Invasion fleet " + this.fleet.getNameWithFaction() + " standing down");
            this.orderedReturn = true;
            if (data.event != null) data.event.endEvent(InvasionFleetEvent.FleetReturnReason.OTHER, data.source);
            this.fleet.clearAssignments();
            
            Script despawnScript = null, despawnScriptFinal = null;
            boolean despawningAtTarget = false;
            if (data.target.getFaction() == data.fleet.getFaction())
            {
                // our faction controls the original target, perhaps we captured it?
                // anyway, go ahead and despawn there if it's closer
                float distToSource = Misc.getDistance(data.fleet.getLocationInHyperspace(), data.source.getLocationInHyperspace());
                float distToTarget = Misc.getDistance(data.fleet.getLocationInHyperspace(), data.target.getLocationInHyperspace());
                if (distToSource > distToTarget)
                {
                    despawningAtTarget = true;
                    despawnScript = new Script() {
                        @Override
                        public void run() {
                            float points = ExerelinUtilsFleet.getFleetGenPoints(fleet) * RESERVE_CONVERSION_EFFICIENCY;
                            log.info("Invasion fleet despawning at target " + data.target.getName() + "; can restore " + points + " points");
                            ResponseFleetManager.modifyReserveSize(data.targetMarket, points);
                        }
                    };
                }
            }
            final SectorEntityToken destination = despawningAtTarget ? data.target : data.source;
            despawnScriptFinal = new Script() {
                @Override
                public void run() {
                    if (destination.getId().equals("exigency_anomaly"))
                        Tasserus.getAnomalyPlugin().createBigPulse(Math.min(1f, fleet.getFleetSizeCount() / 10f), true);
                }
            };
            
            this.fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
            this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, ExerelinUtilsFleet.getDaysToOrbit(fleet), StringHelper.getFleetAssignmentString("endingMission", destination.getName()), despawnScript);
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F, despawnScriptFinal);
        }
    }
}

