package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.utilities.NexUtilsGUI;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class RaiseRelationsFactor extends BaseRecognitionEventFactor {

    public static final float RESPECT_EFFECT_MULT = 2f;

    @Getter protected RaiseRelations action;

    public RaiseRelationsFactor(@Nullable RaiseRelations action) {
        if (action == null) {
            setInfoMode(true);
            return;
        }
        this.action = action;
        this.recogPoints = (int)(action.getReputationResult().delta * 100);
        this.respectPoints = (int)(recogPoints * RESPECT_EFFECT_MULT);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return FactionRecognitionIntel.getString("factorDesc_raiseRelations");
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        if (infoMode) {
            return NexUtilsGUI.createSimpleTextTooltip(FactionRecognitionIntel.getString("factorTooltip_raiseRelationsInfo"), TOOLTIP_WIDTH);
        }

        String operativeName = "";
        if (action.getAgent() != null) operativeName = action.getAgent().getAgent().getNameString();
        String str = String.format(FactionRecognitionIntel.getString("factorTooltip_raiseRelations"), operativeName);
        return NexUtilsGUI.createSimpleTextTooltip(str, TOOLTIP_WIDTH);
    }

    @Override
    public boolean isOneTime() {
        return true;
    }
}
