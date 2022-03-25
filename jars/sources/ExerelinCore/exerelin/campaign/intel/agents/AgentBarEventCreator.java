package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

public class AgentBarEventCreator extends BaseBarEventCreator
{
	@Override
	public PortsideBarEvent createBarEvent() {
		return new AgentBarEvent();
	}
	
	public float getBarEventFrequencyWeight() {
		return 20f;
	}
}
