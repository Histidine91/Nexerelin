package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class NexReturnStage extends ReturnStage {
	
	protected OffensiveFleetIntel offFltIntel;

	public NexReturnStage(OffensiveFleetIntel intel) {
		super(intel);
		offFltIntel = intel;
	}
	
	protected Object readResolve() {
		if (offFltIntel == null)
			offFltIntel = (OffensiveFleetIntel)intel;
		
		return this;
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		// blank
	}
}
