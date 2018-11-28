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
import com.fs.starfarer.api.impl.campaign.ids.Tags;
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
import org.lazywizard.lazylib.MathUtils;
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
	protected boolean needReload = false;
	//protected EveryFrameScript broadcastScript;
	
	public MiningFleetAI(CampaignFleetAPI fleet, MiningFleetData data)
	{
		this.fleet = fleet;
		this.data = data;
		giveInitialAssignment();
	}
	
	protected SectorEntityToken getNearestAsteroid()
	{
		List<SectorEntityToken> roids = data.fleet.getContainingLocation().getAsteroids();
		float closestDistSq = 99999999;
		SectorEntityToken closest = roids.get(0);
		for (SectorEntityToken roid : roids)
		{
			if (roid.hasTag(Tags.NON_CLICKABLE))	// asteroid created from drive bubble bounce
					continue;	
			float distSq = MathUtils.getDistanceSquared(roid.getLocation(), fleet.getLocation());
			if (distSq < closestDistSq)
			{
				closest = roid;
				closestDistSq = distSq;
			}
		}
		
		return closest;
	}
	
	protected Object readResolve()
	{
		// our asteroid won't be reloaded in star system on its own, we'll put it back in advance()
		if (data.target != null && data.target instanceof AsteroidAPI)
		{
			needReload = true;
		}
		return this;
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
		
		// reset asteroid
		if (needReload)
		{
			needReload = false;
			fleet.getContainingLocation().addEntity(data.target);
		}
		
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
				// unload cargo if appropriate
				if(!unloaded && assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE && data.source.getContainingLocation() == data.fleet.getContainingLocation()
						&& MathUtils.getDistance(data.source, data.fleet) < 600f)
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
			
			// mine
			if(assignment.getAssignment() == FleetAssignment.ORBIT_PASSIVE || assignment.getAssignment() == FleetAssignment.INTERCEPT
					&& data.target.getContainingLocation() == data.fleet.getContainingLocation())
			{
				float range = MathUtils.getDistance(data.target, data.fleet);
				//debugLocal("Fleet " + data.fleet.getNameWithFaction() + " range to " + data.target.getName() + ": " + range);
				if (range < 300f)
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
				else	// drifted too far, return to target 
				{
					
					//debugLocal("Moving " + fleet.getNameWithFaction() + " back to target " + data.target.getName());
					fleet.clearAssignments();
				}
				return;
			}
		}
		
		// no orders, head to mining target
		SectorEntityToken target = data.target;
		LocationAPI loc = target.getContainingLocation();
		String locName = "the " + loc.getName();

		if (loc != fleet.getContainingLocation()) {
			LocationAPI hyper = Global.getSector().getHyperspace();
			Vector2f dest = Misc.getPointAtRadius(loc.getLocation(), 1500.0F);
			SectorEntityToken token = hyper.createToken(dest.x, dest.y);
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", locName));
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", target.getName()));
		}
		else {
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", target.getName()));
		}
		if (target instanceof AsteroidAPI)
			fleet.addAssignment(FleetAssignment.INTERCEPT, target, 30, StringHelper.getFleetAssignmentString("mining", target.getName()));
		else
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 30, StringHelper.getFleetAssignmentString("mining", target.getName()));
	}
	
	@Override
	public boolean isDone()
	{
		return !fleet.isAlive();
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
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, this.data.source, daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionMining"));
	}
	
	protected void debugLocal(String str)
	{
		if (fleet.getContainingLocation() != Global.getSector().getPlayerFleet().getContainingLocation())
			return;
		Global.getSector().getCampaignUI().addMessage(str);
	}
	
	protected void giveStandDownOrders()
	{
		if (!orderedReturn)
		{
			orderedReturn = true;
			fleet.clearAssignments();
			
			SectorEntityToken destination = data.source;
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));			
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, ExerelinUtilsFleet.getDaysToOrbit(fleet), StringHelper.getFleetAssignmentString("miningUnload", null));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
}

