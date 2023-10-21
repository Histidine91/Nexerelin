package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexFactionConfig;

import java.awt.*;

public abstract class BaseCharacterBackground {

    public CharacterBackgroundSpec spec;

    public String getTitle(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return spec.title;
    }

    public String getShortDescription(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return spec.shortDescription;
    }

    public  String getIcon(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return spec.iconPath;
    }

    public float getOrder() {
        return spec.order;
    }

    public abstract boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig);

    public boolean canTooltipBeExpanded() {
        return false;
    }

    public void addTooltipForSelection(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig, Boolean expanded) {
       addBaseTooltip(tooltip, factionSpec, factionConfig);
    }

    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        addBaseTooltip(tooltip, factionSpec, factionConfig);
    }

    public void executeAfterGameCreation(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {

    }

    protected void addBaseTooltip(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        Color hc = Misc.getHighlightColor();
        Color tc = Misc.getTextColor();
        float pad = 10f;

        TooltipMakerAPI imageTooltip = tooltip.beginImageWithText(spec.iconPath, 40f);
        imageTooltip.addPara(spec.title, 0f, hc, hc);
        imageTooltip.addPara(spec.shortDescription, 0f, tc, tc);
        tooltip.addImageWithText(0f);

        tooltip.addSpacer(pad);

        tooltip.addPara(spec.longDescription, 0f);
    }
}
