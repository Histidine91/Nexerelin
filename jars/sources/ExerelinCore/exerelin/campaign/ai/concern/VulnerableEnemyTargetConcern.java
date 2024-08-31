package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.fleets.InvasionFleetManager;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class VulnerableEnemyTargetConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;
    public static final float VALUE_MULT = 2f;

    @Override
    public boolean generate() {
        List<Pair<MarketAPI, Float>> targetsSorted = new ArrayList<>();

        Set<MarketAPI> alreadyConcernMarkets = getExistingConcernItems();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (alreadyConcernMarkets.contains(market)) continue;
            boolean canUse = InvasionFleetManager.getManager().isValidInvasionOrRaidTarget(ai.getFaction(), null, market, null, false);
            if (canUse) {
                int size = market.getSize();
                float value = getMarketValue(market);
                float sd = getSpaceDefenseValue(market);
                float gd = getGroundDefenseValue(market);
                if (sd/size >= SAIConstants.SPACE_DEF_THRESHOLD && gd/size >= SAIConstants.GROUND_DEF_THRESHOLD) return false;

                float valueMod = value/(sd*2 + gd)/SAIConstants.MARKET_VALUE_DIVISOR * VALUE_MULT;
                valueMod *= (1 + 0.5f * size/5);
                if (valueMod < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) continue;

                targetsSorted.add(new Pair<>(market, valueMod));
            }
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
            priority.modifyFlat("defenseAdjustedValue", goal.two * 2, StrategicAI.getString("statDefenseAdjustedValue", true));
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
        float valueMod = value/(sd*2 + gd)/SAIConstants.MARKET_VALUE_DIVISOR * VALUE_MULT;
        valueMod *= (1 + 0.5f * size/5);
        if (valueMod < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) {
            end();
            return;
        }
        priority.modifyFlat("defenseAdjustedValue", value/(sd*2 + gd)/SAIConstants.MARKET_VALUE_DIVISOR,
                StrategicAI.getString("statDefenseAdjustedValue", true));

        super.update();
    }

    @Override
    public boolean isValid() {
        return market != null && market.getFaction().isHostileTo(ai.getFaction());
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof VulnerableEnemyTargetConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }
}
