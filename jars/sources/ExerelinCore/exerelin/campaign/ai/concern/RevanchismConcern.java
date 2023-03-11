package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;

import java.util.*;

public class RevanchismConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;

    @Override
    public boolean generate() {
        List<MarketAPI> claimedSorted = new ArrayList<>();
        Set alreadyConcernMarkets = getExistingConcernItems();

        // get all markets on which we have a revanchist claim
        // remove markets where we already have a concern?
        // sort by arbitrary importance (maybe just size?)
        // pick randomly from top 4
        for (MarketAPI market : DiplomacyProfileIntel.getClaimedMarkets(ai.getFaction())) {
            if (alreadyConcernMarkets.contains(market)) continue;
            claimedSorted.add(market);
        }

        if (claimedSorted.isEmpty()) return false;

        Collections.sort(claimedSorted, NexUtilsMarket.marketSizeComparator);

        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
        int max = Math.min(claimedSorted.size(), MAX_MARKETS_FOR_PICKER);
        for (int i=0; i<max; i++) {
            MarketAPI toPick = claimedSorted.get(i);
            picker.add(toPick, toPick.getSize());
        }
        market = picker.pick();

        update();

        return market != null;
    }

    @Override
    public void update() {
        if (market != null) {
            float value = getMarketValue(market)/1000f * market.getSize();
            value /= SAIConstants.MARKET_VALUE_DIVISOR;

            priority.modifyFlat("value", value, StrategicAI.getString("statValue", true));
            reapplyPriorityModifiers();
        }
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();
        applyPriorityModifierForTrait(DiplomacyTraits.TraitIds.IRREDENTIST, 1.5f, false);
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof RevanchismConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        LabelAPI label = super.createTooltipDesc(tooltip, holder, pad);
        label.setText(StringHelper.substituteFactionTokens(label.getText(), market.getFaction()));
        return label;
    }

    @Override
    public boolean isValid() {
        return market != null && market.getFaction() != ai.getFaction();
    }


}
