package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

public class ReliefFleetAI implements EveryFrameScript
{
	public static Logger log = Global.getLogger(ReliefFleetAI.class);
	private float daysTotal = 0.0F;
	private final CampaignFleetAPI fleet;
	private boolean orderedReturn = false;
	protected ReliefFleetIntelAlt intel;
	
	public ReliefFleetAI(CampaignFleetAPI fleet, ReliefFleetIntelAlt intel)
	{
		this.fleet = fleet;
		this.intel = intel;
		giveInitialAssignment();
	}
	
	float interval = 0;
	
	@Override
	public void advance(float amount)
	{
		if (orderedReturn)
			return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		this.daysTotal += days;
		if (this.daysTotal > 300.0F) {
			intel.endEvent(ReliefFleetIntelAlt.EndReason.DEFEATED);
			giveStandDownOrders();
			return;
		}
		if (intel.isEnding() || intel.isEnded())
		{
			giveStandDownOrders();
			return;
		}
		
		interval += days;
		if (interval >= 0.25f) interval -= 0.25f;
		else return;
		
		FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
		if (assignment != null)
		{
			float fp = this.fleet.getFleetPoints();
			if (fp < fleet.getMemoryWithoutUpdate().getFloat("$startingFP") / 2) {
				intel.endEvent(ReliefFleetIntelAlt.EndReason.DEFEATED);
				giveStandDownOrders();
				return;
			}
		}
		else
		{
			MarketAPI market = intel.target;
			String name = intel.target.getName();
			
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(), 1000, 
						StringHelper.getFleetAssignmentString("deliveringRelief", name));
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 0.5f + intel.cargoSize/600, 
						StringHelper.getFleetAssignmentString("unloadingRelief", name), new Script() {
				@Override
				public void run() {
					intel.deliver();
				}
			});
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
		float daysToOrbit = ExerelinUtilsFleet.getDaysToOrbit(fleet);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, intel.source.getPrimaryEntity(), daysToOrbit, 
				StringHelper.getFleetAssignmentString("orbiting", intel.source.getPrimaryEntity().getName()));
	}
	
	protected void giveStandDownOrders()
	{
		if (!this.orderedReturn)
		{
			//log.info("Invasion support fleet " + this.fleet.getNameWithFaction() + " standing down");
			orderedReturn = true;
			fleet.clearAssignments();
			float daysToOrbit = ExerelinUtilsFleet.getDaysToOrbit(fleet);
			
			SectorEntityToken destination = intel.source.getPrimaryEntity();
			
			fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000.0F, StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, daysToOrbit, StringHelper.getFleetAssignmentString("endingMission", destination.getName()));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
}

