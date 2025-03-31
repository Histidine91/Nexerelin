package exerelin.campaign.intel.rebellion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.intel.fleets.TwoWayTravelFleetAI;
import exerelin.campaign.intel.rebellion.RebellionIntel.SupportFleetData;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

// Rebel suppression/smuggler fleet AI
public class RebellionSupportFleetAI extends TwoWayTravelFleetAI
{
	public static Logger log = Global.getLogger(RebellionSupportFleetAI.class);
		
	protected final SupportFleetData data;
	
	public RebellionSupportFleetAI(CampaignFleetAPI fleet, SupportFleetData data)
	{
		super(fleet, data.source, data.target, 150);
		this.data = data;
	}
	
	@Override
	public void advance(float amount)
	{		
		if (orderedReturn)
			return;
		
		if (data.intel.isEnding() || data.intel.isEnded())
		{
			giveStandDownOrders();  // rebellion over; go home
			return;
		}
		
		super.advance(amount);
		
		if (!interval.intervalElapsed()) return;
		
		// target market got captured under us, or we're otherwise no longer allied to it
		if (data.forGovernment) {
			if (!AllianceManager.areFactionsAllied(fleet.getFaction().getId(), data.target.getFactionId()))
			{
				giveStandDownOrders();
				return;
			}
		}
	}
	
	@Override
	protected void onFleetDefeat() {
		//log.info("Suppression fleet defeated");
		data.intel.fleetDefeated(data);
		super.onFleetDefeat();
	}
	
	@Override
	protected void giveGoToAssignment() {
		MarketAPI market = data.target;
		String marketName = market.getName();

		// TODO: maybe damage rebels for every day it's in orbit instead?

		fleet.addAssignment(FleetAssignment.DELIVER_MARINES, market.getPrimaryEntity(), 
				1000, StringHelper.getFleetAssignmentString("travellingTo", marketName));

		String assignKey = data.forGovernment ? "suppressingRebellion" : "deliveringToRebellion";
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 
				3, StringHelper.getFleetAssignmentString(assignKey, marketName),
				getDeliveryScript());
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 
				7, StringHelper.getFleetAssignmentString("orbiting", marketName), standDownScript);
	}
	
	@Override
	protected void giveInitialAssignment() {
		float daysToOrbit = NexUtilsFleet.getDaysToOrbit(fleet);
		String missionType = data.forGovernment ? "missionSuppression" : "missionSmuggle";
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, data.source.getPrimaryEntity(), 
				daysToOrbit, StringHelper.getFleetAssignmentString("preparingFor", 
				data.source.getPrimaryEntity().getName(), missionType));
	}
	
	@Override
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
			// retroactive crash fix
			if (data.source == null) data.source = Global.getSector().getEconomy().getMarketsCopy().get(0);

			final SectorEntityToken destination = despawningAtTarget ? data.target.getPrimaryEntity() : 
					data.source.getPrimaryEntity();
			
			fleet.addAssignment(FleetAssignment.DELIVER_CREW, destination, 1000.0F, 
					StringHelper.getFleetAssignmentString("returningTo", destination.getName()));
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination, NexUtilsFleet.getDaysToOrbit(fleet), 
					StringHelper.getFleetAssignmentString("endingMission", destination.getName()));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, destination, 1000.0F);
		}
	}
	
	@Override
	protected Script getDeliveryScript() {
		return new Script() {
			@Override
			public void run() {
				data.intel.fleetArrived(data);
			}
		};
	}
	
	protected Script standDownScript = new Script() {
		public void run() {
			giveStandDownOrders();
		}
	};
}

