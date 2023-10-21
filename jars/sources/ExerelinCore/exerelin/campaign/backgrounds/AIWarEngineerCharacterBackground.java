package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexFactionConfig;

public class AIWarEngineerCharacterBackground extends BaseCharacterBackground {
    @Override
    public String getTitle() {
        return "AI-War Engineer";
    }

    @Override
    public String getShortDescription() {
        return "You were once tasked to maintain a whole fleet of automated ships, knowledge that may come in handy on your new adventure.";
    }

    @Override
    public String getIcon(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return "graphics/icons/skills/automated_ships.png";
    }

    @Override
    public int getOrder() {
        return 11;
    }

    @Override
    public boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return true;
    }

    @Override
    public boolean hasSelectionTooltip() {
        return true;
    }

    @Override
    public void addTooltipForSelection(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        tooltip.addPara("-> Start with the \"Automated Ships\" skill.", 0f);
    }

    @Override
    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    @Override
    public void executeAfterGameCreation(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }
}
