package exerelin.campaign.intel.bar;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEventCreator;

public class NexDeliveryBarEventCreator extends DeliveryBarEventCreator {
	
	@Override
	public PortsideBarEvent createBarEvent() {
		return new NexDeliveryBarEvent();
	}
}
