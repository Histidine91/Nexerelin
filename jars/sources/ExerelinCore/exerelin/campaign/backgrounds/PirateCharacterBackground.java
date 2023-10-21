package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexFactionConfig;

public class PirateCharacterBackground extends BaseCharacterBackground {
    @Override
    public String getTitle() {
        return "Famous Pirate";
    }

    @Override
    public String getShortDescription() {
        return "Begin as a once-been pirate with fame to their name. Some shudder just from hearing your name.";
    }

    @Override
    public String getIcon(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return Global.getSettings().getFactionSpec("pirates").getCrest();
    }

    @Override
    public int getOrder() {
        return 10;
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
    public void executeAfterGameCreation(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }
}
