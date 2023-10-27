package exerelin.campaign.ai.action.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.world.ExerelinProcGen;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.HeavyIndustry;
import exerelin.world.industry.IndustryClassGen;

import java.util.List;

/**
 * Specialized action for building heavy industry in response to low ship quality.
 */
public class BuildHeavyIndustryAction extends BuildIndustryAction {

    @Override
    public boolean generate() {
        industryId = Industries.HEAVYINDUSTRY;
        market = concern.getMarket();
        if (market == null) market = pickMarketFallback(industryId);
        if (market == null) return false;

        delegate = this;
        status = ActionStatus.STARTING;
        return buildOrUpgrade();
    }

    @Override
    protected boolean buildOrUpgrade() {
        IndustryClassGen gen = NexMarketBuilder.getIndustryClassesByIndustryId().get(industryId);
        ExerelinProcGen.ProcGenEntity entity = ExerelinProcGen.createEntityData(market.getPrimaryEntity());
        if (market.hasIndustry("ms_modularFac"))
            market.getMemoryWithoutUpdate().set(HeavyIndustry.MEM_KEY_WANTED_SHADOWYARDS_UPGRADE, "ms_specializedSystemsFabs", 0);
        gen.apply(entity, false);
        ColonyManager.getManager().processNPCConstruction(market);
        return true;
    }

    @Override
    protected String pickIndustry(MarketAPI market) {
        return null;
    }

    @Override
    protected MarketAPI pickMarketFallback(String industryId) {
        List<MarketAPI> markets = Misc.getFactionMarkets(ai.getFactionId());

        MarketAPI best = null;
        float bestScore = 0;

        // first look for existing heavy industries we can upgrade
        for (MarketAPI market : markets) {
            if (market.hasIndustry(Industries.ORBITALWORKS) || market.hasIndustry("ms_orbitalShipyard")) {
                // we already have the best industry already, it's probably just disrupted or something
                // could just exit the action here, but it may be worth building a backup
                continue;
            }

            if (market.hasIndustry(Industries.HEAVYINDUSTRY) || market.hasIndustry("ms_modularFac")) {
                float score = market.getSize();
                if (score > bestScore) {
                    bestScore = score;
                    best = market;
                }
            }
        }

        if (best != null) return best;

        // no upgradables, look for best market to build heavy industry on
        bestScore = 0;
        IndustryClassGen gen = NexMarketBuilder.getIndustryClassesByIndustryId().get(Industries.HEAVYINDUSTRY);
        for (MarketAPI market : markets) {
            ExerelinProcGen.ProcGenEntity entity = ExerelinProcGen.createEntityData(market.getPrimaryEntity());
            if (!gen.canApply(entity)) continue;
            float score = gen.getWeight(entity);
            if (score > bestScore) {
                bestScore = score;
                best = market;
            }
        }
        return best;
    }



    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canBuildHeavyIndustry")) return false;
        if (!super.canUse(concern)) return false;
        return true;
    }
}
