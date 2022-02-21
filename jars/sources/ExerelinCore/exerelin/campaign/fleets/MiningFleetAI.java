package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.MiningHelperLegacy;
import exerelin.campaign.fleets.MiningFleetManagerV2.MiningFleetData;
import exerelin.utilities.NexUtilsCargo;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.List;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
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
				if (!orderedReturn) MiningFleetManagerV2.getManager().reportFleetLost(data);
				giveStandDownOrders();
			}
			CargoAPI cargo = fleet.getCargo();
			if (cargo.getSpaceUsed() / cargo.getMaxCapacity() > 0.9f)
			{
				giveStandDownOrders();
			}
			
			if (orderedReturn)
			{
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
						if (MiningHelperLegacy.getFleetMiningStrength(fleet) < data.miningStrength * 0.5f)
							giveStandDownOrders();
						else
							MiningHelperLegacy.getMiningResults(fleet, data.target, 1f, false);
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
		String tgtName = target.getName();
		if (loc != fleet.getContainingLocation()) {
			LocationAPI hyper = Global.getSector().getHyperspace();
			Vector2f dest = Misc.getPointAtRadius(loc.getLocation(), 1500.0F);
			SectorEntityToken token = hyper.createToken(dest.x, dest.y);
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, token, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", locName));
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", tgtName));
		}
		else {
			fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, target, 1000.0F, StringHelper.getFleetAssignmentString("travellingTo", tgtName));
		}
		if (target instanceof AsteroidAPI)
			fleet.addAssignment(FleetAssignment.INTERCEPT, target, 30, StringHelper.getFleetAssignmentString("mining", 
					Misc.lcFirst(tgtName)));
		else
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 30, StringHelper.getFleetAssignmentString("mining", tgtName));
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
		float daysToOrbit = NexUtilsFleet.getDaysToOrbit(fleet);
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
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, NexUtilsFleet.getDaysToOrbit(fleet), 
					StringHelper.getFleetAssignmentString("miningUnload", null), 
					getUnloadScript(fleet, data.sourceMarket, false));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
	
	public static Script getUnloadScript(CampaignFleetAPI fleet, MarketAPI market, boolean allowSupplies) 
	{
		return new UnloadScript(fleet, market, allowSupplies);
	}
	
	@AllArgsConstructor
	public static class UnloadScript implements Script {
		
		public CampaignFleetAPI fleet;
		public MarketAPI market;
		public boolean allowSupplies;
		
		public void run() {
			CargoAPI cargo = fleet.getCargo();
			List<CargoStackAPI> cargoStacks = cargo.getStacksCopy();
			for (CargoStackAPI stack : cargoStacks)
			{
				if (stack.isCommodityStack() && (allowSupplies || !stack.isSupplyStack()))
				{
					NexUtilsCargo.addCommodityStockpile(market, stack.getCommodityId(),
							Math.round(stack.getSize()/2));
					cargo.removeCommodity(stack.getCommodityId(), stack.getSize());
				}
			}
			cargo.removeEmptyStacks();
		}
	};
}

