package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyTraits;

import java.util.*;

public class HostileInSharedSystemConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;

    @Override
    public boolean generate() {
        List<Pair<MarketAPI, Float>> hostiles = new ArrayList<>();
        Set alreadyConcernMarkets = getExistingConcernItems();

        Set<LocationAPI> toCheck = new HashSet<>();
        for (MarketAPI market : Misc.getFactionMarkets(ai.getFaction())) {
            toCheck.add(market.getContainingLocation());
        }

        for (LocationAPI loc : toCheck) {
            for (MarketAPI market : Global.getSector().getEconomy().getMarkets(loc)) {
                if (market.isHidden()) continue;
                if (alreadyConcernMarkets.contains(market)) continue;
                if (!market.getFaction().isHostileTo(ai.getFaction())) continue;

                float value = getMarketValue(market)/1000f + market.getSize() * 100;
                value /= SAIConstants.MARKET_VALUE_DIVISOR;
                value *= 2;
                if (value < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) continue;

                hostiles.add(new Pair<>(market, value));
            }
        }

        if (hostiles.isEmpty()) return false;

        Collections.sort(hostiles, MARKET_PAIR_COMPARATOR);

        WeightedRandomPicker<Pair<MarketAPI, Float>> picker = new WeightedRandomPicker<>();
        int max = Math.min(hostiles.size(), MAX_MARKETS_FOR_PICKER);
        for (int i=0; i<max; i++) {
            Pair<MarketAPI, Float> entry = hostiles.get(i);
            picker.add(entry, entry.two);
        }
        Pair<MarketAPI, Float> chosen = picker.pick();

        if (chosen != null) {
            market = chosen.one;
            priority.modifyFlat("value", chosen.two, StrategicAI.getString("statValue", true));
            reapplyPriorityModifiers();
        }

        return market != null;
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();
        applyPriorityModifierForTrait(DiplomacyTraits.TraitIds.PARANOID, 1.4f, false);
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
