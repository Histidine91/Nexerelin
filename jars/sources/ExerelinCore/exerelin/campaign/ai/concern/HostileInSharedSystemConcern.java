package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.utilities.NexUtilsMarket;

import java.util.*;

public class HostileInSharedSystemConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;

    @Override
    public boolean generate() {
        List<MarketAPI> hostiles = new ArrayList<>();
        Set alreadyConcernMarkets = getExistingConcernItems();

        Set<LocationAPI> toCheck = new HashSet<>();
        for (MarketAPI market : Misc.getFactionMarkets(ai.getFaction())) {
            if (alreadyConcernMarkets.contains(market)) continue;
            toCheck.add(market.getContainingLocation());
        }

        for (LocationAPI loc : toCheck) {
            for (MarketAPI market : Global.getSector().getEconomy().getMarkets(loc)) {
                if (market.isHidden()) continue;
                if (!market.getFaction().isHostileTo(ai.getFaction())) continue;
                hostiles.add(market);
            }
        }

        if (hostiles.isEmpty()) return false;

        Collections.sort(hostiles, NexUtilsMarket.marketSizeComparator);

        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
        int max = Math.min(hostiles.size(), MAX_MARKETS_FOR_PICKER);
        for (int i=0; i<max; i++) {
            MarketAPI toPick = hostiles.get(i);
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
        if (otherConcern instanceof HostileInSharedSystemConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return market != null && market.getFaction().isHostileTo(ai.getFaction());
    }


}
