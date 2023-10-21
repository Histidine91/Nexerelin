package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexFactionConfig;

public abstract class BaseCharacterBackground {

    public CharacterBackgroundSpec spec;

    public String getTitle() {
        return spec.title;
    }

    public String getShortDescription() {
        return spec.shortDescription;
    }

    public  String getIcon(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return spec.iconPath;
    }

    public float getOrder() {
        return spec.order;
    }

    public abstract boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig);


    public boolean hasSelectionTooltip() {
        return false;
    }

    public void addTooltipForSelection(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    public void executeAfterGameCreation(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }
}
