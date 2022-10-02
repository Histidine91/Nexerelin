package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexUtilsFaction;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class InadequateDefenseConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;

    @Override
    public boolean generate() {
        List<Pair<MarketAPI, Float>> targetsSorted = new ArrayList<>();

        Set<Object> alreadyConcernMarkets = getExistingConcernItems();

        for (MarketAPI market : NexUtilsFaction.getFactionMarkets(ai.getFaction().getId(), true)) {
            if (alreadyConcernMarkets.contains(market)) continue;
            int size = market.getSize();
            float value = getMarketValue(market);
            float sd = getSpaceDefenseValue(market);
            float gd = getGroundDefenseValue(market);
            if (sd/size >= SAIConstants.SPACE_DEF_THRESHOLD) continue;
            if (gd/size >= SAIConstants.GROUND_DEF_THRESHOLD) continue;

            float valueMod = value/(sd*2 + gd);

            targetsSorted.add(new Pair<>(market, valueMod));
        }

        int max = Math.min(targetsSorted.size(), MAX_MARKETS_FOR_PICKER);

        WeightedRandomPicker<Pair<MarketAPI, Float>> picker = new WeightedRandomPicker<>();
        for (int i=0; i<max; i++) {
            Pair<MarketAPI, Float> pair = targetsSorted.get(i);
            picker.add(pair, pair.two);
        }
        Pair<MarketAPI, Float> goal = picker.pick();
        if (goal != null) {
            market = goal.one;
            priority.modifyFlat("defenseAdjustedValue", goal.two/10, StrategicAI.getString("statDefenseAdjustedValue", true));
        }

        return market != null;
    }

    @Override
    public void update() {
        int size = market.getSize();
        float value = getMarketValue(market);
        float sd = getSpaceDefenseValue(market);
        float gd = getGroundDefenseValue(market);
        if (sd/size >= SAIConstants.SPACE_DEF_THRESHOLD && gd/size >= SAIConstants.GROUND_DEF_THRESHOLD) {
            end();
        }
    }

    @Override
    public boolean isValid() {
        return market != null && market.getFaction() == ai.getFaction();
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof InadequateDefenseConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }

    public static final Comparator<Pair<MarketAPI, Float>> VALUE_COMPARATOR = new Comparator<Pair<MarketAPI, Float>>() {
        @Override
        public int compare(Pair<MarketAPI, Float> o1, Pair<MarketAPI, Float> o2) {
            return Float.compare(o2.two, o1.two);
        }
    };
}
