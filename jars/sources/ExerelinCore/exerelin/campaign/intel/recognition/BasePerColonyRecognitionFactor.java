package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.util.Misc;

public abstract class BasePerColonyRecognitionFactor extends BaseRecognitionEventFactor {

    public static final float INCOME_PER_SCORE = 2000;

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (intel instanceof FactionRecognitionIntel fri) {
            if (fri.forRespectValues) {
                return computeRespectPoints();
            }
        }
        return computeRecognitionPoints();
    }

    abstract int computeRespectPoints();
    abstract int computeRecognitionPoints();

    protected float getMarketsContribution() {
        float score = 0;
        for (MarketAPI market : Misc.getPlayerMarkets(false)) {
            //if (!market.isPlayerOwned()) continue;
            if (market.isHidden()) continue;

            score += getMarketContribution(market);
        }
        return score;
    }

    abstract float getMarketContribution(MarketAPI market);
}
