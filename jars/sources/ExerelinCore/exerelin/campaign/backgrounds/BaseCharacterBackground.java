package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexFactionConfig;

public abstract class BaseCharacterBackground {

    public abstract String getTitle();

    public abstract String getShortDescription();

    public abstract String getIcon(FactionSpecAPI factionSpec, NexFactionConfig factionConfig);

    public abstract int getOrder();

    public abstract boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig);


    public boolean hasSelectionTooltip() {
        return false;
    }

    public void addTooltipForSelection(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    public void executeAfterGameCreation(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }
}
