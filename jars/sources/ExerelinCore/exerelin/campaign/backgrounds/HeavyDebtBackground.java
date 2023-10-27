package exerelin.campaign.backgrounds;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.impl.campaign.tutorial.SpacerObligation;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.backgrounds.scripts.HeavyDebtObligation;
import exerelin.campaign.customstart.Nex_SpacerObligation;
import exerelin.utilities.NexFactionConfig;

public class HeavyDebtBackground extends BaseCharacterBackground {

    @Override
    public boolean shouldShowInSelection(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        return true;
    }


    @Override
    public void onNewGameAfterEconomyLoad(FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
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
            tooltip.addPara(CharacterBackgroundUtils.getString("bgDebt_selectionTooltipFormula"), 0f, Misc.getTextColor(), Misc.getHighlightColor(),
                    "" + base, "" + perLevel, "level","" + mult);
        }
    }

    @Override
    public void addTooltipForIntel(TooltipMakerAPI tooltip, FactionSpecAPI factionSpec, NexFactionConfig factionConfig) {
        CampaignFleetAPI fleet = (CampaignFleetAPI) Global.getSector().getMemoryWithoutUpdate().get("$nex_debtBackgroundTarget");
        Boolean defeated = (Boolean) Global.getSector().getMemoryWithoutUpdate().get("$nex_defeatedDebtBackgroundTarget");

        super.addTooltipForIntel(tooltip, factionSpec, factionConfig);

        if (defeated != null) {
            tooltip.addSpacer(10f);
            tooltip.addPara(CharacterBackgroundUtils.getString("bgDebt_intelDescEnded"), 0f, Misc.getNegativeHighlightColor());
            return;
        }

        int debt = HeavyDebtObligation.getCalculatedDebt();

        if (fleet == null) {
            tooltip.addSpacer(10f);
            tooltip.addPara(CharacterBackgroundUtils.getString("bgDebt_intelDesc"), 0f, Misc.getNegativeHighlightColor(), "" + debt);
        }
        if (fleet != null) {
            if (fleet.isAlive() && !fleet.isDespawning()) {
                tooltip.addSpacer(10f);
                tooltip.addPara(CharacterBackgroundUtils.getString("bgDebt_intelDesc"), 0f, Misc.getNegativeHighlightColor(), "" + debt);

                tooltip.addSpacer(10f);

                tooltip.addPara(CharacterBackgroundUtils.getString("bgDebt_intelDescFleet"), 0f,
                        Misc.getHighlightColor(), fleet.getContainingLocation().getNameWithNoType());
            }
        }
    }


}
