package exerelin.campaign.ai.action.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.world.NexMarketBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuildForAccessibilityAction extends BuildIndustryAction {

    public static final List<String> INDUSTRIES_ORDERED = new ArrayList<>(Arrays.asList(
            Industries.SPACEPORT, Industries.MEGAPORT, Industries.WAYSTATION
    ));
    public static final int MIN_SIZE_FOR_MEGAPORT = 5;

    public static boolean canDoAnything(MarketAPI market) {
        return !market.hasIndustry(Industries.MEGAPORT) && !market.hasIndustry(Industries.WAYSTATION);
    }

    @Override
    protected boolean buildOrUpgrade() {
        NexMarketBuilder.addIndustry(market, industryId, false);
        ColonyManager.getManager().processNPCConstruction(market);
        return true;
    }

    @Override
    protected String pickIndustry(MarketAPI market) {
        for (String indID : INDUSTRIES_ORDERED) {
            if (market.hasIndustry(indID)) continue;
            if (Industries.MEGAPORT.equals(indID) && market.getSize() < MIN_SIZE_FOR_MEGAPORT) continue;

            return indID;
        }

        return null;
    }

    @Override
    protected MarketAPI pickMarketFallback(String industryId) {
        return null;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canBuildSpaceport")) return false;
        MarketAPI market = concern.getMarket();
        if (market == null) return false;
        if (!canDoAnything(market)) return false;

        return super.canUse(concern);
    }
}
