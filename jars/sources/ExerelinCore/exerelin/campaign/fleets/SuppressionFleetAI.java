package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.events.RebellionEvent.SuppressionFleetData;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

// Rebel suppression fleet AI
public class SuppressionFleetAI implements EveryFrameScript
{
	public static Logger log = Global.getLogger(SuppressionFleetAI.class);
		
	protected final SuppressionFleetData data;
	protected float daysTotal = 0.0F;
	protected final CampaignFleetAPI fleet;
	protected boolean orderedReturn = false;
	//protected EveryFrameScript broadcastScript;
	
	public SuppressionFleetAI(CampaignFleetAPI fleet, SuppressionFleetData data)
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
		if (interval > 0.25f) interval -= 0.25f;
		else return;
		
		if (orderedReturn)
			return;
		
		if (data.event != null && data.event.isDone())
		{
			giveStandDownOrders();  // rebellion over; go home
			return;
		}

		float fp = this.fleet.getFleetPoints();
		if (fp < this.data.startingFleetPoints / 3.0F) {
			if (data.event != null)
				data.event.suppressionFleetDefeated(data);
			giveStandDownOrders();
			return;
		}
		
		// target market got captured under us, or we're otherwise no longer allied to it
		if (!AllianceManager.areFactionsAllied(fleet.getFaction().getId(), data.targetMarket.getFactionId()))
		{
			giveStandDownOrders();
			return;
		}
		
		FleetAssignmentDataAPI assignment = this.fleet.getAI().getCurrentAssignment();
		if (assignment != null)
		{
			
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
				SectorEntityToken token = system.getHyperspaceAnchor();
				this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, token, 1000, 
						StringHelper.getFleetAssignmentString("travellingTo", locName));
				this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, market.getPrimaryEntity(), 
						1000, StringHelper.getFleetAssignmentString("travellingTo", marketName));
			}
			else {
				this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, market.getPrimaryEntity(), 
						1000, StringHelper.getFleetAssignmentString("travellingTo", marketName), new Script() {

							@Override
							public void run() {
								data.event.suppressionFleetArrived(data);
							}
							
						});
				this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 
						60, StringHelper.getFleetAssignmentString("suppressingRebellion", marketName));
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
		if (data.noWait) return;
		float daysToOrbit = ExerelinUtilsFleet.getDaysToOrbit(fleet);
		this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, data.source, daysToOrbit, 
				StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionSuppression"));
	}
  
	protected void giveStandDownOrders()
	{
		if (!orderedReturn)
		{
			//log.info("Invasion fleet " + this.fleet.getNameWithFaction() + " standing down");
			orderedReturn = true;
			fleet.clearAssignments();
			
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
				}
			}
			final SectorEntityToken destination = despawningAtTarget ? data.target : data.source;
			
			this.fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000.0F, 
					StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
			this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, ExerelinUtilsFleet.getDaysToOrbit(fleet), 
					StringHelper.getFleetAssignmentString("endingMission", destination.getName()));
			this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
}

