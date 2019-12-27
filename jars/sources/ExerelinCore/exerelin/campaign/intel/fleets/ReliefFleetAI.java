package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

public class ReliefFleetAI extends TwoWayTravelFleetAI
{
	public static Logger log = Global.getLogger(ReliefFleetAI.class);
	protected ReliefFleetIntelAlt intel;
	
	public ReliefFleetAI(CampaignFleetAPI fleet, ReliefFleetIntelAlt intel)
	{
		super(fleet, intel.source, intel.target, 300);
		this.intel = intel;
	}
	
	protected Object readResolve() {
		if (source == null) source = intel.source;
		if (destination == null) destination = intel.target;
		return this;
	}
	
	@Override
	protected void onExpired() {
		super.onExpired();
		intel.endEvent(ReliefFleetIntelAlt.EndReason.DEFEATED);
	}
	
	@Override
	protected void onFleetDefeat() {
		super.onFleetDefeat();
	}
	
	@Override
	protected void giveGoToAssignment() {
		String name = destination.getName();

		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination.getPrimaryEntity(), 1000, 
					StringHelper.getFleetAssignmentString("deliveringRelief", name));
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, destination.getPrimaryEntity(), 0.5f + intel.cargoSize/600, 
					StringHelper.getFleetAssignmentString("unloadingRelief", name), getDeliveryScript());
	}
	
	@Override
	protected Script getDeliveryScript() {
		return new Script() {
			@Override
			public void run() {
				intel.deliver();
			}
		};
	}
	
	@Override
	public void advance(float amount)
	{
		if (orderedReturn)
			return;
		
		if (intel.isEnding() || intel.isEnded())
		{
			giveStandDownOrders();
			return;
		}
		super.advance(amount);
	}
}

