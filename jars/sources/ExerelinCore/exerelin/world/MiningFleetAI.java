package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.MiningHelper;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.StringHelper;
import exerelin.world.MiningFleetManager.MiningFleetData;
import java.util.List;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class MiningFleetAI implements EveryFrameScript
{
    public static Logger log = Global.getLogger(MiningFleetAI.class);
    public static final float UPDATE_INTERVAL = 0.25f;
	
    protected final MiningFleetData data;
	
    protected float daysTotal = 0.0F;
	protected float miningDailyProgress = 0;
    protected final CampaignFleetAPI fleet;
    protected boolean orderedReturn = false;
	protected boolean unloaded = false;
    protected boolean responseFleetRequested = false;
    //protected EveryFrameScript broadcastScript;
  
    public MiningFleetAI(CampaignFleetAPI fleet, MiningFleetData data)
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
        if (this.daysTotal > 150.0F)
        {
            giveStandDownOrders();
            return;
        }
        
        interval += days;
        if (interval > UPDATE_INTERVAL) interval -= UPDATE_INTERVAL;
        else return;
        
        FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
        if (assignment != null)
        {
            float fp = this.fleet.getFleetPoints();
            if (fp < this.data.startingFleetPoints / 2.0F) {
                giveStandDownOrders();
            }
			CargoAPI cargo = fleet.getCargo();
			if (cargo.getSpaceUsed() / cargo.getMaxCapacity() > 0.9f)
			{
				giveStandDownOrders();
			}
            
            if (orderedReturn)
			{
				if(!unloaded && assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE && data.source.getContainingLocation() == data.fleet.getContainingLocation()
						&& Misc.getDistance(data.source.getLocation(), data.fleet.getLocation()) < 600f)
				{
					List<CargoStackAPI> cargoStacks = cargo.getStacksCopy();
					for (CargoStackAPI stack : cargoStacks)
					{
						if (stack.isCommodityStack() && !stack.isSupplyStack())
						{
							ExerelinUtilsCargo.addCommodityStockpile(data.sourceMarket, stack.getCommodityId(), stack.getSize());
							cargo.addCommodity(stack.getCommodityId(), -stack.getSize());
						}
					}
					cargo.removeEmptyStacks();
					unloaded = true;
				}
                return;
			}
            
            if(assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE || assignment.getAssignment() == FleetAssignment.INTERCEPT
					&& data.target.getContainingLocation() == data.fleet.getContainingLocation()
                    && Misc.getDistance(data.target.getLocation(), data.fleet.getLocation()) < 600f)
            {
                fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, 0.5f);
				miningDailyProgress += UPDATE_INTERVAL;
				if (miningDailyProgress > 1)
				{
					miningDailyProgress -= 1;
					if (MiningHelper.getFleetMiningStrength(fleet) < data.miningStrength * 0.5f)
						giveStandDownOrders();
					else
						MiningHelper.getMiningResults(fleet, data.target, 0.5f, false);
					//log.info("Fleet " + fleet.getName() + " has cargo " + cargo.getSpaceUsed() + " of " + cargo.getMaxCapacity());
				}
            }
        }
        else
        {
			SectorEntityToken target = data.target;
            LocationAPI loc = target.getContainingLocation();
            String locName = "the " + loc.getName();
            
            if (loc != this.fleet.getContainingLocation()) {
                LocationAPI hyper = Global.getSector().getHyperspace();
                Vector2f dest = Misc.getPointAtRadius(loc.getLocation(), 1500.0F);
                SectorEntityToken token = hyper.createToken(dest.x, dest.y);
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", locName, null));
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", target.getName(), null));
            }
            else {
                this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", target.getName(), null));
            }
			if (target instanceof AsteroidAPI)
				this.fleet.addAssignment(FleetAssignment.INTERCEPT, target, 30, StringHelper.getFleetAssignmentString("mining", target.getName(), null));
			else
				this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 30, StringHelper.getFleetAssignmentString("mining", target.getName(), null));

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
    
    protected float getDaysToOrbit()
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
  
    protected void giveInitialAssignment()
    {
        if (data.noWait) return;
        float daysToOrbit = getDaysToOrbit();
        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionMining"));
    }
  
    protected void giveStandDownOrders()
    {
        if (!this.orderedReturn)
        {
            this.orderedReturn = true;
            this.fleet.clearAssignments();
            
            SectorEntityToken destination = data.source;
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName(), null));			
            this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, getDaysToOrbit(), StringHelper.getFleetAssignmentString("miningUnload", null, null));
            this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
        }
    }
}

