package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.backgrounds.scripts.HeavyDebtObligation;
import exerelin.utilities.NexFactionConfig;

public class HeavyDebtBackground extends BaseCharacterBackground {

    @Override
    public boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return true;
    }

    @Override
    public void executeAfterGameCreation(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        new HeavyDebtObligation();
    }

    @Override
    public void addTooltipForSelection(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig, Boolean expanded) {
        super.addTooltipForSelection(tooltip, factionSpec, factionConfig, expanded);

        int base = HeavyDebtObligation.DEBT_BASE;
        int perLevel = HeavyDebtObligation.DEBT_PER_LEVEL;
        float mult = Global.getSettings().getFloat("nex_spacerDebtMult");

        if (expanded) {
            tooltip.addSpacer(10f);
            tooltip.addPara("The formula for debt calculation is: (" + base + " + (" + perLevel + " * level)) * " + mult, 0f, Misc.getTextColor(), Misc.getHighlightColor(),
                    "" + base, "" + perLevel, "level","" + mult);
        }
    }

    @Override
    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        super.addTooltipForIntel(tooltip, factionSpec, factionConfig);

        int debt = HeavyDebtObligation.getCalculatedDebt();
        tooltip.addSpacer(10f);
        tooltip.addPara("You currently have to pay back " + debt + " credits every month.", 0f, Misc.getTextColor(), Misc.getNegativeHighlightColor(), "" + debt);
    }
}
