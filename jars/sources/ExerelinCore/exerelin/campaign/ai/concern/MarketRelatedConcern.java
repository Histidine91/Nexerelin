package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.HashSet;
import java.util.Set;

public abstract class MarketRelatedConcern extends BaseStrategicConcern {
    @Override
    public Set getExistingConcernItems() {
        Set<MarketAPI> markets = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            markets.add(concern.getMarket());
        }
        return markets;
    }
}
