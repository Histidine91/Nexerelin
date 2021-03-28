package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;

/**
 * Use for a fleet that goes from one market to another, maybe does something there,
 * then goes back and maybe does something there too.
 */
public class TwoWayTravelFleetAI implements EveryFrameScript
{
	protected float elapsed;
	protected float maxTime;
	protected float fpMultToWithdraw = 0.4f;
	protected final CampaignFleetAPI fleet;
	protected boolean orderedReturn = false;
	protected MarketAPI source, destination;
	protected IntervalUtil interval = new IntervalUtil(.25f, .25f);
	
	public TwoWayTravelFleetAI(CampaignFleetAPI fleet, MarketAPI source, MarketAPI destination, float maxTime)
	{
		this.fleet = fleet;
		this.maxTime = maxTime;
		this.source = source;
		this.destination = destination;
	}
	
	protected void onExpired() {
		giveStandDownOrders();
	}
	
	protected void onFleetDefeat() {
		giveStandDownOrders();
	}
	
	@Override
	public void advance(float amount)
	{
		if (orderedReturn)
			return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		this.elapsed += days;
		if (this.elapsed > maxTime) {
			onExpired();
			return;
		}
		
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
		
		FleetAssignmentDataAPI assignment = fleet.getAI().getCurrentAssignment();
		if (assignment != null)
		{
			float fp = fleet.getFleetPoints();
			
			if (fp < fleet.getMemoryWithoutUpdate().getFloat("$startingFP") * fpMultToWithdraw) {
				onFleetDefeat();
				return;
			}
		}
		else
		{
			giveGoToAssignment();
		}
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
		float daysToOrbit = NexUtilsFleet.getDaysToOrbit(fleet);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source.getPrimaryEntity(), daysToOrbit, 
				StringHelper.getFleetAssignmentString("orbiting", source.getPrimaryEntity().getName()));
	}
	
	protected void giveGoToAssignment() {
		
	}
	
	protected Script getDeliveryScript() {
		return null;
	}
	
	protected Script getReturnScript() {
		return null;
	}
	
	protected void giveStandDownOrders()
	{
		if (!this.orderedReturn)
		{
			//log.info("Invasion support fleet " + this.fleet.getNameWithFaction() + " standing down");
			orderedReturn = true;
			fleet.clearAssignments();
			float daysToOrbit = NexUtilsFleet.getDaysToOrbit(fleet);
			
			SectorEntityToken destination = source.getPrimaryEntity();
			
			fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000, 
					StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, daysToOrbit, 
					StringHelper.getFleetAssignmentString("endingMission", destination.getName()),
					getReturnScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000);
		}
	}
}

