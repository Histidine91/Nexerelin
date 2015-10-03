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
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class InvasionSupportFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(InvasionSupportFleetAI.class);
       
    private final InvasionFleetManager.InvasionFleetData data;
    private float daysTotal = 0.0F;
    private final CampaignFleetAPI fleet;
    private boolean orderedReturn = false;
    private boolean criticalDamage = false;
  
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
                criticalDamage = true;
                //orderedReturn = false;
                giveStandDownOrders();
            }
            if(!data.target.getFaction().isHostileTo(fleet.getFaction()))    {
                giveStandDownOrders();  // market is no longer hostile; abort strike mission
            }
            
            if (orderedReturn)
                return;
        }
        else
        {
            MarketAPI market = data.targetMarket;
            StarSystemAPI system = market.getStarSystem();
            String entityName = data.target.getName();
            
            if (system != null)
            {
                Vector2f dest = Misc.getPointAtRadius(system.getLocation(), 1500.0F);
                LocationAPI loc = Global.getSector().getHyperspace();
                SectorEntityToken token = loc.createToken(dest.x, dest.y);
                String systemBaseName = system.getBaseName();
                
                if (system != this.fleet.getContainingLocation()) {
                  this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingToStarSystem", systemBaseName, null));
                }
                if (this.data.noWander) {
                    this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 40.0F, StringHelper.getFleetAssignmentString("attacking", entityName, null));
                    this.fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, market.getPrimaryEntity(), 40.0F, StringHelper.getFleetAssignmentString("attacking", entityName, null));
                } else if (Math.random() > 0.8D) {
                  this.fleet.addAssignment(FleetAssignment.RAID_SYSTEM, system.getHyperspaceAnchor(), 40.0F, 
                          StringHelper.getFleetAssignmentString("attackingAroundStarSystem", systemBaseName, null));
                } else if (Math.random() > 0.5D) {
                  this.fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, market.getPrimaryEntity(), 40.0F, StringHelper.getFleetAssignmentString("attacking", entityName, null));
                } else {
                  this.fleet.addAssignment(FleetAssignment.RAID_SYSTEM, system.getStar(), 40.0F,  StringHelper.getFleetAssignmentString("attackingStarSystem", systemBaseName, null));
                }
            }
            else
            {
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 40.0F, StringHelper.getFleetAssignmentString("attacking", entityName, null));
                this.fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, market.getPrimaryEntity(), 40.0F, StringHelper.getFleetAssignmentString("attacking", entityName, null));
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
        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionStrike"));
    }
  
    private void giveStandDownOrders()
    {
        if (!this.orderedReturn)
        {
            //log.info("Invasion support fleet " + this.fleet.getNameWithFaction() + " standing down");
            this.orderedReturn = true;
            this.fleet.clearAssignments();
            
            SectorEntityToken destination = data.source;
			
			// if we're standing down from taking too much damage
			if (criticalDamage)
			{
				if (data.target.getFaction() == data.fleet.getFaction())
				{
					// our faction controls the original target, perhaps we captured it?
					// anyway, go ahead and despawn there if it's closer
					float distToSource = Misc.getDistance(data.fleet.getLocationInHyperspace(), data.source.getLocationInHyperspace());
					float distToTarget = Misc.getDistance(data.fleet.getLocationInHyperspace(), data.target.getLocationInHyperspace());
					if (distToSource > distToTarget)
						destination = data.target;
				}
				this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName(), null));
				this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, getDaysToOrbit(), StringHelper.getFleetAssignmentString("standingDown", null, "missionStrike"));
			}
			//other stand down reasons
			else if (data.target.getFaction() == data.fleet.getFaction())
            {
                // our faction controls the original target, presumably we captured it
                // go ahead and patrol around it
                destination = data.target;
                LocationAPI loc = data.target.getContainingLocation();
                StarSystemAPI system = data.targetMarket.getStarSystem();
                if (system != null && system != this.fleet.getContainingLocation()) {
                    Vector2f dest = Misc.getPointAtRadius(system.getLocation(), 1500.0F);
                    SectorEntityToken token = loc.createToken(dest.x, dest.y);
                    this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingToStarSystem", system.getBaseName(), null));
                }
                destination = data.target;
                this.fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, destination, 40.0F, StringHelper.getFleetAssignmentString("defending", data.target.getName(), null));
                this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, getDaysToOrbit(), StringHelper.getFleetAssignmentString("endingMission", destination.getName(), null));
            }
            else
            {
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName(), null));
                this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, getDaysToOrbit(), StringHelper.getFleetAssignmentString("endingMission", destination.getName(), null));
            }
			this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
			log.info("Strike fleet standing down; critical damage? " + criticalDamage);
        }
    }
}

