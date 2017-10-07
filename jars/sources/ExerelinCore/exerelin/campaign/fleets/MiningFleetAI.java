package exerelin.campaign.fleets;

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
import exerelin.campaign.MiningHelperLegacy;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.MiningFleetManager.MiningFleetData;
import exerelin.plugins.ExerelinModPlugin;
import java.util.List;
import org.apache.log4j.Logger;
import org.histidine.industry.scripts.MiningHelper;
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
		if (interval >= UPDATE_INTERVAL) interval -= UPDATE_INTERVAL;
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
					if (ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST)
					{
						if (MiningHelper.getFleetMiningStrength(fleet) < data.miningStrength * 0.5f)
							giveStandDownOrders();
						else
							MiningHelper.getMiningResults(fleet, data.target, 1f, false);
					}
					else
					{
						if (MiningHelperLegacy.getFleetMiningStrength(fleet) < data.miningStrength * 0.5f)
							giveStandDownOrders();
						else
							MiningHelperLegacy.getMiningResults(fleet, data.target, 1f, false);
					}
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
				this.fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", locName));
				this.fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", target.getName()));
			}
			else {
				this.fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", target.getName()));
			}
			if (target instanceof AsteroidAPI)
				this.fleet.addAssignment(FleetAssignment.INTERCEPT, target, 30, StringHelper.getFleetAssignmentString("mining", target.getName()));
			else
				this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 30, StringHelper.getFleetAssignmentString("mining", target.getName()));
	
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
		this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionMining"));
	}
	
	protected void giveStandDownOrders()
	{
		if (!this.orderedReturn)
		{
			this.orderedReturn = true;
			this.fleet.clearAssignments();
			
			SectorEntityToken destination = data.source;
			this.fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));			
			this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, ExerelinUtilsFleet.getDaysToOrbit(fleet), StringHelper.getFleetAssignmentString("miningUnload", null));
			this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
}

