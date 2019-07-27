package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.impl.campaign.intel.ProcurementMissionCreator;

public class Nex_ProcurementMissionCreator extends ProcurementMissionCreator {
	
	@Override
	public EveryFrameScript createMissionIntel() {
		return new Nex_ProcurementMissionIntel();
	}
}
