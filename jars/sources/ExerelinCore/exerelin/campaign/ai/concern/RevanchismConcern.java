package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.utilities.NexUtilsMarket;

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

        if (market != null) {
            float value = this.getMarketValue(market)/1000f * market.getSize();
            value /= 10;

            priority.modifyFlat("value", value, StrategicAI.getString("statValue", true));
        }

        return market != null;
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof RevanchismConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return market != null && market.getFaction() != ai.getFaction();
    }


}
