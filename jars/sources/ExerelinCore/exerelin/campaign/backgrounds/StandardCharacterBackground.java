package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexFactionConfig;

public class StandardCharacterBackground extends BaseCharacterBackground {
    @Override
    public String getTitle() {
        return "Unknown";
    }

    @Override
    public String getShortDescription() {
        return "Begin without an existing story to your name and shape your own right from the start.";
    }

    @Override
    public String getIcon(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return Global.getSettings().getFactionSpec("player").getCrest();
    }

    @Override
    public float getOrder() {
        return -100f;
    }

    @Override
    public boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return true;
    }


    @Override
    public void addTooltipForSelection(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    @Override
    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    @Override
    public void executeAfterGameCreation(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }
}
