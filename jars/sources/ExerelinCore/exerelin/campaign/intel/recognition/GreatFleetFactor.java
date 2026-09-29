package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsGUI;
import lombok.Getter;

public class GreatFleetFactor extends BaseRecognitionEventFactor
{
	@Getter
	protected FactionAPI faction;

	public GreatFleetFactor(FactionAPI faction) {
		this.faction = faction;
		recogPoints = FactionRecognitionIntel.GREAT_FLEET_RECOGNITION;
		respectPoints = FactionRecognitionIntel.GREAT_FLEET_RESPECT;
	}

	@Override
	public String getDesc(BaseEventIntel intel) {
		return String.format(FactionRecognitionIntel.getString("factorDesc_greatFleetVisit"), faction.getDisplayName());
	}

	@Override
	public boolean isOneTime() {
		return true;
	}

	@Override
	public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
		String str = String.format(FactionRecognitionIntel.getString("factorTooltip_greatFleetVisit"), faction.getDisplayNameWithArticle());
		return NexUtilsGUI.createSimpleTextTooltip(str, TOOLTIP_WIDTH);
	}
}
