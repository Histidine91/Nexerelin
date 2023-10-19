package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.MilitaryAIModule;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexUtilsFaction;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Log4j
public class InadequateDefenseConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;
    public static final float PRIORITY_MULT = 1.5f;

    @Override
    public boolean generate() {
        List<Pair<MarketAPI, Float>> targetsSorted = new ArrayList<>();

        Set<Object> alreadyConcernMarkets = getExistingConcernItems();

        for (MarketAPI market : NexUtilsFaction.getFactionMarkets(ai.getFactionId(), true)) {
            if (alreadyConcernMarkets.contains(market)) continue;
            int size = market.getSize();
            float value = getMarketValue(market);
            float sd = getSpaceDefenseValue(market);
            float gd = getGroundDefenseValue(market);
            if (sd/size >= SAIConstants.SPACE_DEF_THRESHOLD && gd/size >= SAIConstants.GROUND_DEF_THRESHOLD) return false;

            float valueMod = value/(sd*2 + gd)/SAIConstants.MARKET_VALUE_DIVISOR * 2;
            if (valueMod < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) continue;

            float score = valueMod + getPriorityFromRaids(market);

            targetsSorted.add(new Pair<>(market, score));
        }

        Collections.sort(targetsSorted, MARKET_PAIR_COMPARATOR);
        int max = Math.min(targetsSorted.size(), MAX_MARKETS_FOR_PICKER);

        WeightedRandomPicker<Pair<MarketAPI, Float>> picker = new WeightedRandomPicker<>();
        for (int i=0; i<max; i++) {
            Pair<MarketAPI, Float> pair = targetsSorted.get(i);
            picker.add(pair, pair.two);
        }
        Pair<MarketAPI, Float> goal = picker.pick();
        if (goal != null) {
            market = goal.one;
            update();
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
            return;
        }

        float valueMod = value/(sd*2 + gd)/SAIConstants.MARKET_VALUE_DIVISOR * 2;
        if (valueMod < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) {
            end();
            return;
        }
        priority.modifyFlat("defenseAdjustedValue", valueMod * PRIORITY_MULT,
                StrategicAI.getString("statDefenseAdjustedValue", true));

        float fromRaids = getPriorityFromRaids(market);
        priority.modifyFlat("recentAttacks", fromRaids,
                StrategicAI.getString("statRecentAttacks", true));

        super.update();
    }

    /**
     * Gets the priority contribution from recent raid-type events on the specified market.
     * @param market
     * @return
     */
    public float getPriorityFromRaids(MarketAPI market) {
        List<Pair<MilitaryAIModule.RaidRecord, Float>> raidsSorted = new ArrayList<>();
        List<MilitaryAIModule.RaidRecord> recentRaids = new ArrayList<>();

        float totalImpact = 0;
        for (MilitaryAIModule.RaidRecord raid : ai.getMilModule().getRecentRaids()) {
            //log.info("Checking raid " + raid.name);
            if (raid.defender != ai.getFaction()) continue;
            if (raid.target != market) continue;

            recentRaids.add(raid);
            totalImpact += raid.getAgeAdjustedImpact();
        }
        return totalImpact;
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
}
