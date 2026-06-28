package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;

public class CrisisCompletedFactor extends BaseRecognitionEventFactor {

	protected String factionId;
	protected String crisisId;

	public CrisisCompletedFactor(CrisisChecker.CrisisCheckerEntry entry) {
		factionId = entry.factionId;
		crisisId = entry.crisisId;

		this.respectPoints = entry.respectPoints;
		this.recogPoints = entry.recogPoints;
	}

	@Override
	public String getDesc(BaseEventIntel intel) {
		return String.format(FactionRecognitionIntel.getString("factorTooltip_crisis"), Global.getSector().getFaction(factionId).getDisplayName());
	}

	@Override
	public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
		return NexUtilsGUI.createSimpleTextTooltip(FactionRecognitionIntel.getString("factorTooltip_crisis"), TOOLTIP_WIDTH);
	}
}
